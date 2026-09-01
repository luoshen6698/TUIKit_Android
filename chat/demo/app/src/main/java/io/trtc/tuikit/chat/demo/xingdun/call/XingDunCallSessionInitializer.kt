package io.trtc.tuikit.chat.demo.xingdun.call

import android.content.Context
import android.util.Log
import com.tencent.cloud.tuikit.engine.call.TUICallEngine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingBellFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingVibratorFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.NotificationFeature
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

/** Keeps the official call engine attached to the currently restored XingDun IM identity. */
object XingDunCallSessionInitializer {

    private val lock = Any()
    private var initializedIdentity: String? = null
    private var initializingIdentity: String? = null

    fun initialize(context: Context, sdkAppId: Int, userId: String, userSig: String) {
        if (sdkAppId <= 0 || userId.isBlank() || userSig.isBlank()) {
            Log.w(TAG, "Skipped call initialization because the restored credential is incomplete")
            return
        }
        val identity = "$sdkAppId:$userId"
        synchronized(lock) {
            if (initializedIdentity == identity || initializingIdentity == identity) return
            initializingIdentity = identity
        }

        val appContext = context.applicationContext
        LoginStore.shared.login(
            appContext,
            sdkAppId,
            userId,
            userSig,
            object : CompletionHandler {
                override fun onSuccess() {
                    val activeUserId = TUILogin.getLoginUser().orEmpty().takeIf(String::isNotBlank)
                        ?: LoginStore.shared.loginState.loginUserInfo.value?.userID
                    if (LoginStore.shared.sdkAppID != sdkAppId || activeUserId != userId) {
                        clearInitializing(identity)
                        Log.e(TAG, "Call initialization stopped because the active IM identity changed")
                        return
                    }
                    TUICallEngine.createInstance(appContext).init(
                        sdkAppId,
                        userId,
                        userSig,
                        object : TUICommonDefine.Callback {
                            override fun onSuccess() {
                                synchronized(lock) {
                                    initializingIdentity = null
                                    initializedIdentity = identity
                                }
                                NotificationFeature(appContext).registerNotificationBannerChannel()
                                CallingBellFeature(appContext)
                                CallingVibratorFeature(appContext)
                                TUICallEngine.createInstance(appContext).enableMultiDeviceAbility(true, null)
                                TUICallKit.createInstance(appContext).enableIncomingBanner(true)
                                Log.i(TAG, "Call engine initialized for the active XingDun session")
                            }

                            override fun onError(errCode: Int, errMsg: String) {
                                clearInitializing(identity)
                                Log.e(TAG, "Call engine initialization failed: $errCode")
                            }
                        },
                    )
                }

                override fun onFailure(code: Int, desc: String) {
                    clearInitializing(identity)
                    Log.e(TAG, "Unable to synchronize the IM identity before call initialization: $code")
                }
            },
        )
    }

    fun reset() {
        synchronized(lock) {
            initializedIdentity = null
            initializingIdentity = null
        }
    }

    private fun clearInitializing(identity: String) {
        synchronized(lock) {
            if (initializingIdentity == identity) initializingIdentity = null
        }
    }

    private const val TAG = "XingDunCallSession"
}
