package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunForegroundNotificationManager
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunRedpacketStatusLoader
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter

/** Serializes logout/switch cleanup so no old-tenant state survives into a new enterprise. */
internal object XingDunTenantSessionCoordinator {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val completions = mutableListOf<() -> Unit>()
    private var running = false
    private var clearEnterpriseRequested = false
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun logout(onComplete: () -> Unit = {}) {
        leaveTenant(clearEnterprise = false, onComplete)
    }

    fun switchEnterprise(onComplete: () -> Unit = {}) {
        leaveTenant(clearEnterprise = true, onComplete)
    }

    private fun leaveTenant(clearEnterprise: Boolean, onComplete: () -> Unit) {
        val shouldStart = synchronized(lock) {
            completions += onComplete
            clearEnterpriseRequested = clearEnterpriseRequested || clearEnterprise
            if (running) false else {
                running = true
                true
            }
        }
        if (!shouldStart) return

        XingDunPushManager.unregisterDevice {
            LoginStore.shared.logout(object : CompletionHandler {
                override fun onSuccess() = finishCleanup()
                override fun onFailure(code: Int, desc: String) = finishCleanup()
            })
        }
    }

    private fun finishCleanup() {
        mainHandler.post {
            val clearEnterprise: Boolean
            val callbacks: List<() -> Unit>
            synchronized(lock) {
                clearEnterprise = clearEnterpriseRequested
                callbacks = completions.toList()
                completions.clear()
                clearEnterpriseRequested = false
                running = false
            }

            XingDunRouter.clearPendingRoute()
            XingDunRedpacketStatusLoader.clearTenantCache()
            XingDunForegroundNotificationManager.resetTenantState()
            MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
            if (clearEnterprise) {
                XingDunSessionManager.clearEnterpriseSelection()
            } else {
                XingDunSessionManager.clear()
            }
            callbacks.forEach { it() }
        }
    }
}
