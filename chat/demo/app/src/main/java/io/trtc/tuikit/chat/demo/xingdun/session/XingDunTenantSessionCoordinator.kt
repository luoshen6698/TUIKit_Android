package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunForegroundNotificationManager
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunRedpacketStatusLoader
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunAutoDeleteRepository
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunPinnedMessageRepository
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMessageFavoriteRepository
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Serializes logout/switch cleanup so no old-tenant state survives into a new enterprise. */
internal object XingDunTenantSessionCoordinator {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        XingDunCredentialRecoveryCoordinator.onSessionLeaving()
        val shouldStart = synchronized(lock) {
            completions += onComplete
            clearEnterpriseRequested = clearEnterpriseRequested || clearEnterprise
            if (running) false else {
                running = true
                true
            }
        }
        if (!shouldStart) return

        val departingSession = XingDunSessionManager.currentSession()
        logoutScope.launch {
            try {
                if (departingSession != null) withTimeout(5_000) {
                    XingDunSessionManager.apiClient().revokeSession(departingSession)
                }
            } catch (_: Exception) { /* Offline logout still clears the local session. */ }
            XingDunPushManager.unregisterDevice {
                LoginStore.shared.logout(object : CompletionHandler {
                    override fun onSuccess() = finishCleanup()
                    override fun onFailure(code: Int, desc: String) = finishCleanup()
                })
            }
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
            XingDunAutoDeleteRepository.clearTenantCache()
            XingDunPinnedMessageRepository.clearTenantCache()
            XingDunMessageFavoriteRepository.clearTenantCache()
            XingDunForegroundNotificationManager.resetTenantState()
            XingDunCallSessionInitializer.reset()
            MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
            if (clearEnterprise) {
                XingDunSessionManager.clearEnterpriseSelection()
            } else {
                XingDunSessionManager.clear()
            }
            XingDunPushManager.onSessionCleared()
            XingDunCredentialRecoveryCoordinator.onSessionCleared()
            callbacks.forEach { it() }
        }
    }
}
