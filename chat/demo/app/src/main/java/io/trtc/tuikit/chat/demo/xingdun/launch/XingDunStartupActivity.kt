package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Keeps the system splash visible while the existing enterprise, version, session and IM startup
 * work runs. The enterprise and authentication screens remain the fallback for incomplete state.
 */
class XingDunStartupActivity : AppCompatActivity() {

    private var keepSplashVisible = true
    private var hasRouted = false
    private var timeoutJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepSplashVisible }

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
        val enterprise = XingDunSessionManager.currentEnterprise()
        if (enterprise == null) {
            val simpleEnterprise = runCatching { XingDunSessionManager.attemptSimpleEnterprise() }.getOrNull()
            if (!isStartupActive()) return
            if (simpleEnterprise == null) {
                routeToEnterpriseAccess()
                return
            }
        } else {
            val refreshError = runCatching { XingDunSessionManager.refreshSelectedEnterprise() }.exceptionOrNull()
            if (!isStartupActive()) return
            if (refreshError != null && !XingDunSessionManager.shouldRetainCachedEnterprise(refreshError)) {
                XingDunSessionManager.clearEnterpriseSelection()
                routeToEnterpriseAccess()
                return
            }
        }

        val versionResult = runCatching { XingDunSessionManager.checkVersion() }.getOrNull()
        if (!isStartupActive()) return
        if (versionResult?.hasUpdate == true) {
            routeToAuthentication()
            return
        }

        if (XingDunSessionManager.currentSession() == null) {
            routeToAuthentication()
            return
        }

        val restoredSession = runCatching { XingDunSessionManager.restore() }
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
        keepSplashVisible = false
    }

    private fun isStartupActive(): Boolean = !hasRouted && !isFinishing && !isDestroyed

    companion object {
        private const val STARTUP_TIMEOUT_MILLIS = 15_000L
    }
}
