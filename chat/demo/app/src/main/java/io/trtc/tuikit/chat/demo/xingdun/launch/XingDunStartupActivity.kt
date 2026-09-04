package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.main.XingDunMessageFirstFramePreloader
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Hands off from the system splash to a lightweight status view while the existing enterprise,
 * version, session and IM startup work runs. Enterprise and authentication remain fallbacks.
 */
class XingDunStartupActivity : AppCompatActivity() {

    private var hasRouted = false
    private var timeoutJob: Job? = null
    private lateinit var startupStatusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.xingdun_activity_startup_loading)
        startupStatusView = findViewById(R.id.xingdun_startup_status)
        splashScreen.setKeepOnScreenCondition { false }

        if (intent?.data != null) {
            routeToEnterpriseAccess()
            return
        }

        lifecycleScope.launch {
            timeoutJob = launch {
                delay(STARTUP_TIMEOUT_MILLIS)
                routeToAuthentication(R.string.xingdun_startup_timed_out)
            }
            prepareAuthenticatedStartup()
        }
    }

    private suspend fun prepareAuthenticatedStartup() {
        var enterprise = XingDunSessionManager.currentEnterprise()
        if (enterprise == null) {
            updateStartupStatus(R.string.xingdun_startup_loading_enterprise)
            enterprise = runCatching { XingDunSessionManager.attemptSimpleEnterprise() }.getOrNull()
        }
        if (enterprise == null) {
            if (!isStartupActive()) return
            routeToEnterpriseAccess()
            return
        }

        if (XingDunSessionManager.currentSession() == null) {
            routeToAuthentication()
            return
        }

        updateStartupStatus(R.string.xingdun_startup_checking_account)
        val validationIsFresh = startupValidationIsFresh(enterprise.companyCode)
        val startupResults = coroutineScope {
            val enterpriseRefresh = async {
                if (validationIsFresh) Result.success(enterprise)
                else runCatching { XingDunSessionManager.refreshSelectedEnterprise() }
            }
            val versionCheck = async {
                if (validationIsFresh) null
                else runCatching { XingDunSessionManager.checkVersion() }
            }
            val sessionRestore = async {
                runCatching { XingDunSessionManager.restore(preferCachedCredentials = true) }
            }
            Triple(enterpriseRefresh.await(), versionCheck.await(), sessionRestore.await())
        }
        if (!isStartupActive()) return

        val enterpriseRefreshError = startupResults.first.exceptionOrNull()
        if (enterpriseRefreshError != null &&
            !XingDunSessionManager.shouldRetainCachedEnterprise(enterpriseRefreshError)
        ) {
            XingDunSessionManager.clearEnterpriseSelection()
            clearStartupValidation()
            routeToEnterpriseAccess()
            return
        }
        val versionResult = startupResults.second?.getOrNull()
        if (versionResult?.hasUpdate == true) {
            clearStartupValidation()
            routeToAuthentication()
            return
        }
        if (!validationIsFresh && startupResults.first.isSuccess && startupResults.second?.isSuccess == true) {
            markStartupValidationFresh(enterprise.companyCode)
        }

        val restoredSession = startupResults.third
            .getOrElse { error ->
                if (!isStartupActive()) return
                routeToAuthentication(XingDunAuthenticationErrorPresenter.login(error))
                return
            }
        if (!isStartupActive()) return
        if (restoredSession == null) {
            routeToAuthentication(R.string.xingdun_session_expired)
            return
        }

        updateStartupStatus(R.string.xingdun_connecting_im)
        val imLoginFailure = loginToIM(restoredSession)
        if (!isStartupActive()) {
            LoginStore.shared.logout(null)
            return
        }
        if (imLoginFailure != 0) {
            routeToAuthentication(imLoginFailure)
            return
        }
        if (!XingDunSessionManager.matchesCurrentIMIdentity(restoredSession.sdkAppId, restoredSession.timUserId) ||
            LoginStore.shared.sdkAppID != restoredSession.sdkAppId ||
            TUILogin.getLoginUser() != restoredSession.timUserId
        ) {
            LoginStore.shared.logout(null)
            routeToAuthentication(R.string.xingdun_error_company_mismatch)
            return
        }

        XingDunCallSessionInitializer.initialize(
            this,
            restoredSession.sdkAppId,
            restoredSession.timUserId,
            restoredSession.userSig,
        )
        XingDunPushManager.syncDeviceRegistration()
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, restoredSession.timUserId)
        updateStartupStatus(R.string.xingdun_startup_loading_messages)
        XingDunMessageFirstFramePreloader.preload(this)
        routeToMessages()
    }

    private suspend fun loginToIM(session: XingDunStoredSession): Int =
        suspendCancellableCoroutine { continuation ->
            LoginStore.shared.login(
                this,
                session.sdkAppId,
                session.timUserId,
                session.userSig,
                object : CompletionHandler {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(0)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        if (continuation.isActive) {
                            continuation.resume(XingDunAuthenticationErrorPresenter.im(code, desc))
                        }
                    }
                },
            )
        }

    private fun routeToMessages() {
        routeOnce(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TAB_MESSAGES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun routeToAuthentication(noticeResId: Int = 0) {
        routeOnce(
            Intent(this, XingDunLaunchActivity::class.java).apply {
                if (noticeResId != 0) putExtra(XingDunLaunchActivity.EXTRA_NOTICE_RES_ID, noticeResId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun routeToEnterpriseAccess() {
        routeOnce(
            Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                data = intent?.data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun routeOnce(target: Intent) {
        if (hasRouted || isFinishing || isDestroyed) return
        hasRouted = true
        timeoutJob?.cancel()
        timeoutJob = null
        startActivity(target)
    }

    private fun isStartupActive(): Boolean = !hasRouted && !isFinishing && !isDestroyed

    private fun updateStartupStatus(@StringRes messageResId: Int) {
        if (::startupStatusView.isInitialized) startupStatusView.setText(messageResId)
    }

    private fun startupValidationIsFresh(companyCode: String): Boolean {
        val preferences = MMKV.defaultMMKV()
        if (preferences.decodeString(KEY_STARTUP_VALIDATION_SCOPE).orEmpty() != validationScope(companyCode)) {
            return false
        }
        val validatedAt = preferences.decodeLong(KEY_STARTUP_VALIDATED_AT, 0L)
        val age = System.currentTimeMillis() - validatedAt
        return age in 0..STARTUP_VALIDATION_TTL_MILLIS
    }

    private fun markStartupValidationFresh(companyCode: String) {
        MMKV.defaultMMKV().apply {
            encode(KEY_STARTUP_VALIDATION_SCOPE, validationScope(companyCode))
            encode(KEY_STARTUP_VALIDATED_AT, System.currentTimeMillis())
        }
    }

    private fun clearStartupValidation() {
        MMKV.defaultMMKV().apply {
            removeValueForKey(KEY_STARTUP_VALIDATION_SCOPE)
            removeValueForKey(KEY_STARTUP_VALIDATED_AT)
        }
    }

    private fun validationScope(companyCode: String): String = buildString {
        append(companyCode.trim().lowercase())
        append('|')
        append(BuildConfig.XINGDUN_ENVIRONMENT)
        append('|')
        append(BuildConfig.VERSION_CODE)
        append('|')
        append(BuildConfig.VERSION_NAME)
    }

    companion object {
        private const val STARTUP_TIMEOUT_MILLIS = 15_000L
        private const val STARTUP_VALIDATION_TTL_MILLIS = 5 * 60 * 1000L
        private const val KEY_STARTUP_VALIDATION_SCOPE = "xingdun.startup.validation.scope"
        private const val KEY_STARTUP_VALIDATED_AT = "xingdun.startup.validation.validated_at"
    }
}
