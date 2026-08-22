package io.trtc.tuikit.chat.demo.xingdun.push

import android.content.Context
import android.os.Build
import android.util.Log
import com.tencent.qcloud.tim.push.TIMPushCallback
import com.tencent.qcloud.tim.push.TIMPushListener
import com.tencent.qcloud.tim.push.TIMPushManager
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object XingDunPushManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    fun initialize(context: Context) {
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
                restoreIMLoginAfterWakeup()
            }
        }
    }

    private fun restoreIMLoginAfterWakeup() {
        scope.launch {
            val session = runCatching { XingDunSessionManager.restore() }.getOrElse { error ->
                Log.w(TAG, "Unable to restore session after push wakeup: ${error.javaClass.simpleName}")
                return@launch
            } ?: return@launch
            LoginStore.shared.login(
                appContext,
                session.sdkAppId,
                session.timUserId,
                session.userSig,
                object : CompletionHandler {
                    override fun onSuccess() {
                        if (!XingDunSessionManager.matchesCurrentIMIdentity(session.sdkAppId, session.timUserId) ||
                            LoginStore.shared.sdkAppID != session.sdkAppId ||
                            LoginStore.shared.loginState.loginUserInfo.value?.userID != session.timUserId
                        ) {
                            LoginStore.shared.logout(null)
                            XingDunRouter.clearPendingRoute()
                            return
                        }
                        syncDeviceRegistration()
                        XingDunRouter.consumePendingRoute()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        Log.w(TAG, "IM login after push wakeup failed: $code")
                    }
                }
            )
        }
    }

    /** TIMPush performs SDK registration automatically; this only mirrors its resulting ID to XingDun. */
    fun syncDeviceRegistration() {
        if (!::appContext.isInitialized) return
        TIMPushManager.getInstance().getRegistrationID(object : TIMPushCallback<String>() {
            override fun onSuccess(registrationID: String?) {
                val value = registrationID?.trim().orEmpty()
                if (value.isEmpty()) return
                scope.launch {
                    val session = XingDunSessionManager.currentSession() ?: return@launch
                    runCatching {
                        XingDunSessionManager.apiClient().postEmpty(
                            session,
                            "push/register",
                            deviceBody(value)
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Unable to mirror TIMPush registration: ${error.javaClass.simpleName}")
                    }
                }
            }

            override fun onError(code: Int, desc: String?, data: String?) {
                Log.w(TAG, "TIMPush registration ID unavailable: $code")
            }
        })
    }

    fun unregisterDevice(onComplete: () -> Unit) {
        val session = XingDunSessionManager.currentSession()
        if (session == null) {
            onComplete()
            return
        }
        scope.launch {
            runCatching {
                XingDunSessionManager.apiClient().postEmpty(
                    session,
                    "push/unregister",
                    deviceBody("")
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to mirror push unregistration: ${error.javaClass.simpleName}")
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
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
