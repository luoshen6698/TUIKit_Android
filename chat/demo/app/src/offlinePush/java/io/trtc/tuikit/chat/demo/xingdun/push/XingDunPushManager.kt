package io.trtc.tuikit.chat.demo.xingdun.push

import android.content.Context
import android.os.Build
import android.util.Log
import com.tencent.qcloud.tim.push.TIMPushCallback
import com.tencent.qcloud.tim.push.TIMPushListener
import com.tencent.qcloud.tim.push.TIMPushManager
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunPrivacyConsentStore
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object XingDunPushManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationGate = XingDunPushRegistrationGate()
    @Volatile private var leavingSession = false
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        check(XingDunPrivacyConsentStore(context.noBackupFilesDir).hasConsent())
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        TIMPushManager.getInstance().addPushListener(object : TIMPushListener() {
            override fun onNotificationClicked(ext: String?) {
                XingDunRouter.routeNotification(ext)
            }
        })
        TUICore.registerEvent(
            TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_KEY,
            TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_SUB_KEY
        ) { key, subKey, _ ->
            if (key == TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_KEY &&
                subKey == TUIConstants.TIMPush.EVENT_IM_LOGIN_AFTER_APP_WAKEUP_SUB_KEY
            ) {
                XingDunCredentialRecoveryCoordinator.onIMLoginRequiredAfterWakeup()
            }
        }
    }

    /** AtomicX login does not emit TUICore's automatic-push login event. Register explicitly. */
    fun syncDeviceRegistration() {
        if (!::appContext.isInitialized || leavingSession) return
        val session = XingDunSessionManager.currentSession() ?: return
        if (!matchesIMSession(session)) return
        registrationGate.registeredAttempt(identity(session))?.let {
            mirrorRegistration(it)
            return
        }
        val attempt = registrationGate.begin(identity(session)) ?: return
        try {
            TIMPushManager.getInstance().registerPush(appContext, 0, "", object : TIMPushCallback<Any?>() {
                override fun onSuccess(data: Any?) {
                    // SDK data may contain the vendor token. Never log or persist it here.
                    if (!isActive(attempt)) {
                        registrationGate.fail(attempt)
                        return
                    }
                    if (!registrationGate.complete(attempt)) return
                    Log.i(TAG, "TIMPush vendor registration and Tencent binding succeeded")
                    mirrorRegistration(attempt)
                }

                override fun onError(code: Int, desc: String?, data: Any?) {
                    if (registrationGate.fail(attempt)) {
                        Log.w(TAG, "TIMPush registration failed: $code")
                    }
                }
            })
        } catch (error: Exception) {
            if (registrationGate.fail(attempt)) {
                Log.w(TAG, "TIMPush registration could not start: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun mirrorRegistration(attempt: XingDunPushRegistrationGate.Attempt) {
        val session = XingDunSessionManager.currentSession() ?: return
        if (identity(session) != attempt.identity) return
        val client = XingDunSessionManager.apiClient()
        if (!isActive(attempt) || !registrationGate.beginMirror(attempt)) return
        scope.launch {
            var succeeded = false
            try {
                val value = withTimeoutOrNull(5_000) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        TIMPushManager.getInstance().getRegistrationID(object : TIMPushCallback<String>() {
                            override fun onSuccess(registrationID: String?) {
                                if (continuation.isActive) continuation.resume(registrationID?.trim())
                            }

                            override fun onError(code: Int, desc: String?, data: String?) {
                                Log.w(TAG, "TIMPush registration ID unavailable: $code")
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                }
                if (value.isNullOrEmpty() || !isActive(attempt)) return@launch
                client.postEmpty(session, "push/register", deviceBody(value))
                succeeded = true
            } catch (error: Exception) {
                Log.w(TAG, "Unable to mirror TIMPush registration: ${error.javaClass.simpleName}")
            } finally {
                // Each coroutine finishes exactly once; late SDK callbacks cannot finish a retry.
                registrationGate.finishMirror(attempt, succeeded)
            }
        }
    }

    fun unregisterDevice(onComplete: () -> Unit) {
        // Invalidate callbacks before the old identity starts leaving the SDK.
        leavingSession = true
        registrationGate.reset()
        val session = XingDunSessionManager.currentSession()
        val client = if (session != null) XingDunSessionManager.apiClient() else null
        scope.launch {
            try {
                val cleared = withTimeoutOrNull(5_000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        TIMPushManager.getInstance().unRegisterPush(object : TIMPushCallback<Any?>() {
                            override fun onSuccess(data: Any?) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(code: Int, desc: String?, data: Any?) {
                                Log.w(TAG, "TIMPush unregistration failed: $code")
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        })
                    }
                }
                if (cleared == null) Log.w(TAG, "TIMPush unregistration timed out")
                if (session != null && client != null) {
                    runCatching {
                        client.postEmpty(session, "push/unregister", deviceBody(""))
                    }.onFailure { error ->
                        Log.w(TAG, "Unable to mirror push unregistration: ${error.javaClass.simpleName}")
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to clear TIMPush registration: ${error.javaClass.simpleName}")
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun onSessionCleared() {
        registrationGate.reset()
        leavingSession = false
    }

    private fun identity(session: XingDunStoredSession): String =
        "${session.apiBaseUrl}:${session.sdkAppId}:${session.timUserId}"

    private fun matchesIMSession(session: XingDunStoredSession): Boolean =
        LoginStore.shared.sdkAppID == session.sdkAppId && TUILogin.getLoginUser() == session.timUserId

    private fun isActive(attempt: XingDunPushRegistrationGate.Attempt): Boolean {
        val session = XingDunSessionManager.currentSession() ?: return false
        return !leavingSession && registrationGate.isCurrent(attempt) &&
            identity(session) == attempt.identity && matchesIMSession(session)
    }

    private fun deviceBody(registrationID: String): Map<String, Any> = mapOf(
        "device_id" to XingDunSessionManager.deviceId(),
        "platform" to "android",
        "environment" to if (BuildConfig.XINGDUN_ENVIRONMENT == "prod") "production" else "development",
        "bundle_id" to appContext.packageName,
        "registration_id" to registrationID,
        "device_model" to Build.MODEL,
        "os_version" to Build.VERSION.RELEASE,
        "app_version" to BuildConfig.VERSION_NAME
    )

    private const val TAG = "XingDunPush"
}
