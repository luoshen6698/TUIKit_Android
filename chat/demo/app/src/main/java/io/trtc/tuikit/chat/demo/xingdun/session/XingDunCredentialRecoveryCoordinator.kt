package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Keeps UserSig renewal on the current task and owns the only terminal-login redirect. */
internal object XingDunCredentialRecoveryCoordinator {
    private const val TRANSIENT_RETRY_MILLIS = 30_000L
    private const val TAG = "CredentialRecovery"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = XingDunCredentialRecoveryGate()
    private val mutableState = MutableStateFlow(XingDunCredentialRecoveryState.NORMAL)
    val state: StateFlow<XingDunCredentialRecoveryState> = mutableState.asStateFlow()

    private lateinit var appContext: Context
    private var foreground = false
    private var scheduledRefreshJob: Job? = null

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
    }

    fun onLoginExpired() {
        beginRefresh("expired_callback")
    }

    fun onUserSigExpired() {
        beginRefresh("usersig_callback")
    }

    fun onIMLoginRequiredAfterWakeup() {
        if (XingDunSessionManager.currentSession() != null) {
            beginRefresh("push_wakeup")
        }
    }

    fun onKickedOffline() {
        redirectToLogin(R.string.demo_force_offline)
    }

    fun onAppForeground() {
        foreground = true
        scheduleProactiveRefresh()
    }

    fun onAppBackground() {
        foreground = false
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = null
    }

    fun onAuthenticated() {
        gate.markAuthenticated()
        mutableState.value = XingDunCredentialRecoveryState.NORMAL
        scheduleProactiveRefresh()
    }

    fun isRefreshing(): Boolean = gate.current() == XingDunCredentialRecoveryState.REFRESHING

    fun redirectToLogin(@StringRes noticeResId: Int = R.string.demo_login_expired) {
        if (!gate.tryBeginRedirect()) return
        mutableState.value = XingDunCredentialRecoveryState.REDIRECTING
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = null
        XingDunTenantSessionCoordinator.logout {
            appContext.startActivity(Intent(appContext, XingDunLaunchActivity::class.java).apply {
                putExtra(XingDunLaunchActivity.EXTRA_NOTICE_RES_ID, noticeResId)
                putExtra(XingDunLaunchActivity.EXTRA_DISABLE_AUTO_RESTORE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        }
    }

    private fun beginRefresh(reason: String) {
        if (!::appContext.isInitialized || !gate.tryBeginRefresh()) return
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = null
        mutableState.value = XingDunCredentialRecoveryState.REFRESHING
        scope.launch {
            when (val outcome = recoverOnCurrentTask()) {
                is RecoveryOutcome.Success -> completeRefresh(outcome.session)
                is RecoveryOutcome.Terminal -> redirectToLogin(outcome.noticeResId)
                is RecoveryOutcome.Transient -> {
                    Log.w(TAG, "Credential renewal deferred ($reason): ${outcome.error.javaClass.simpleName}")
                    retryAfterTransientFailure()
                }
            }
        }
    }

    private suspend fun recoverOnCurrentTask(): RecoveryOutcome {
        val previous = XingDunSessionManager.currentSession()
            ?: return RecoveryOutcome.Terminal(R.string.demo_login_expired)
        val refreshed = when (val result = refreshCredentialWithFallback()) {
            is RefreshResult.Success -> result.session
            is RefreshResult.Terminal -> return RecoveryOutcome.Terminal(result.noticeResId)
            is RefreshResult.Transient -> return RecoveryOutcome.Transient(result.error)
        }
        if (!sameIdentity(previous, refreshed)) {
            return RecoveryOutcome.Terminal(R.string.xingdun_error_company_mismatch)
        }

        val loginFailure = loginToIM(refreshed)
        if (loginFailure != null) {
            return RecoveryOutcome.Transient(loginFailure)
        }
        if (!XingDunSessionManager.matchesCurrentIMIdentity(refreshed.sdkAppId, refreshed.timUserId) ||
            LoginStore.shared.sdkAppID != refreshed.sdkAppId ||
            TUILogin.getLoginUser() != refreshed.timUserId
        ) {
            return RecoveryOutcome.Terminal(R.string.xingdun_error_company_mismatch)
        }
        return RecoveryOutcome.Success(refreshed)
    }

    private suspend fun refreshCredentialWithFallback(): RefreshResult {
        return try {
            RefreshResult.Success(XingDunSessionManager.refreshIMCredential())
        } catch (error: Throwable) {
            if (isTransient(error)) return RefreshResult.Transient(error)
            try {
                val session = XingDunSessionManager.restore(
                    preferCachedCredentials = false,
                    retainCachedCredentialsOnFailure = false,
                ) ?: return RefreshResult.Terminal(R.string.demo_login_expired)
                RefreshResult.Success(session)
            } catch (fallbackError: Throwable) {
                if (isTransient(fallbackError)) {
                    RefreshResult.Transient(fallbackError)
                } else {
                    RefreshResult.Terminal(
                        if (fallbackError is IllegalArgumentException) {
                            R.string.xingdun_error_company_mismatch
                        } else {
                            R.string.demo_login_expired
                        }
                    )
                }
            }
        }
    }

    private suspend fun loginToIM(session: XingDunStoredSession): Throwable? =
        suspendCancellableCoroutine { continuation ->
            LoginStore.shared.login(
                appContext,
                session.sdkAppId,
                session.timUserId,
                session.userSig,
                object : CompletionHandler {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        if (continuation.isActive) {
                            continuation.resume(IMLoginException(code, desc))
                        }
                    }
                },
            )
        }

    private fun completeRefresh(session: XingDunStoredSession) {
        if (!gate.completeRefresh()) {
            LoginStore.shared.logout(null)
            return
        }
        XingDunCallSessionInitializer.initialize(
            appContext,
            session.sdkAppId,
            session.timUserId,
            session.userSig,
        )
        XingDunPushManager.syncDeviceRegistration()
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, session.timUserId)
        mutableState.value = XingDunCredentialRecoveryState.NORMAL
        XingDunRouter.consumePendingRoute()
        scheduleProactiveRefresh()
    }

    private fun retryAfterTransientFailure() {
        if (!gate.completeRefresh()) return
        mutableState.value = XingDunCredentialRecoveryState.NORMAL
        if (!foreground) return
        scheduledRefreshJob = scope.launch {
            delay(TRANSIENT_RETRY_MILLIS)
            if (foreground) beginRefresh("transient_retry")
        }
    }

    private fun scheduleProactiveRefresh() {
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = null
        if (!foreground || gate.current() != XingDunCredentialRecoveryState.NORMAL) return
        val session = XingDunSessionManager.currentSession() ?: return
        if (!isLoggedInto(session)) return
        val now = System.currentTimeMillis()
        if (XingDunCredentialRefreshPolicy.shouldRefresh(session.userSigExpiresAtMillis, now)) {
            beginRefresh("proactive_foreground")
            return
        }
        scheduledRefreshJob = scope.launch {
            delay(XingDunCredentialRefreshPolicy.delayUntilRefresh(session.userSigExpiresAtMillis, now))
            if (foreground) beginRefresh("proactive_timer")
        }
    }

    private fun isLoggedInto(session: XingDunStoredSession): Boolean =
        LoginStore.shared.sdkAppID == session.sdkAppId && TUILogin.getLoginUser() == session.timUserId

    private fun sameIdentity(first: XingDunStoredSession, second: XingDunStoredSession): Boolean =
        first.sdkAppId == second.sdkAppId &&
            first.timUserId == second.timUserId &&
            first.companyCode.equals(second.companyCode, ignoreCase = true) &&
            first.companyId == second.companyId

    private fun isTransient(error: Throwable): Boolean = when (error) {
        is IOException -> true
        is XingDunApiException -> error.httpStatus == 429 ||
            error.businessCode == 429 ||
            (error.httpStatus ?: 0) >= 500 ||
            (error.businessCode ?: 0) >= 500
        is IMLoginException -> true
        else -> false
    }

    private sealed interface RecoveryOutcome {
        data class Success(val session: XingDunStoredSession) : RecoveryOutcome
        data class Terminal(@StringRes val noticeResId: Int) : RecoveryOutcome
        data class Transient(val error: Throwable) : RecoveryOutcome
    }

    private sealed interface RefreshResult {
        data class Success(val session: XingDunStoredSession) : RefreshResult
        data class Terminal(@StringRes val noticeResId: Int) : RefreshResult
        data class Transient(val error: Throwable) : RefreshResult
    }

    private class IMLoginException(val code: Int, description: String) :
        Exception("IM login failed ($code): $description")
}
