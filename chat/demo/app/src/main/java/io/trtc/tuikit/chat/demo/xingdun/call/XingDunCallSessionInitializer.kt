package io.trtc.tuikit.chat.demo.xingdun.call

import android.content.Context
import android.util.Log
import com.tencent.cloud.tuikit.engine.call.TUICallEngine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingBellFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingVibratorFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.NotificationFeature
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the official call engine attached to the currently restored XingDun IM identity. */
object XingDunCallSessionInitializer {
    private data class Pending(
        val attempt: XingDunCallInitializationGate.Attempt,
        val ready: CompletableDeferred<Boolean> = CompletableDeferred(),
        var timeout: Job? = null,
    )

    private val lock = Any()
    private val gate = XingDunCallInitializationGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Pending? = null

    fun initialize(context: Context, sdkAppId: Int, userId: String, userSig: String) {
        scope.launch { awaitReady(context.applicationContext, sdkAppId, userId, userSig) }
    }

    /** Caller cancellation stops waiting without cancelling another caller's shared initialization. */
    suspend fun awaitReady(context: Context, sdkAppId: Int, userId: String, userSig: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (sdkAppId <= 0 || userId.isBlank() || userSig.isBlank()) return@withContext false
            val identity = currentIdentity(sdkAppId, userId) ?: return@withContext false
            val imMatches = matchesIM(sdkAppId, userId)
            var displaced: Pending? = null
            var shouldStart = false
            val request = synchronized(lock) {
                var admission = gate.begin(identity) ?: return@withContext false
                if (admission.status == XingDunCallInitializationGate.Status.READY && !imMatches) {
                    gate.reset()
                    admission = checkNotNull(gate.begin(identity))
                }
                when (admission.status) {
                    XingDunCallInitializationGate.Status.READY ->
                        Pending(admission.attempt, CompletableDeferred(true))
                    XingDunCallInitializationGate.Status.JOIN ->
                        pending?.takeIf { it.attempt == admission.attempt } ?: return@withContext false
                    XingDunCallInitializationGate.Status.START -> {
                        displaced = pending
                        shouldStart = true
                        Pending(admission.attempt).also { pending = it }
                    }
                }
            }
            displaced?.timeout?.cancel()
            displaced?.ready?.complete(false)
            if (shouldStart) {
                val timeout = scope.launch {
                    delay(INITIALIZATION_TIMEOUT_MILLIS)
                    if (finish(request.attempt, false)) Log.w(TAG, "Call initialization timed out")
                }
                synchronized(lock) {
                    if (pending === request) request.timeout = timeout else timeout.cancel()
                }
                start(context.applicationContext, sdkAppId, userId, userSig, request.attempt)
            }
            request.ready.await() && gate.isReady(request.attempt) &&
                currentIdentity(sdkAppId, userId) == request.attempt.identity && matchesIM(sdkAppId, userId)
        }

    private fun start(
        context: Context,
        sdkAppId: Int,
        userId: String,
        userSig: String,
        attempt: XingDunCallInitializationGate.Attempt,
    ) {
        if (!isCurrent(attempt, sdkAppId, userId)) {
            finish(attempt, false)
            return
        }
        try {
            if (matchesIM(sdkAppId, userId)) {
                initializeEngine(context, sdkAppId, userId, userSig, attempt)
            } else {
                LoginStore.shared.login(context, sdkAppId, userId, userSig, object : CompletionHandler {
                    override fun onSuccess() {
                        scope.launch { initializeEngine(context, sdkAppId, userId, userSig, attempt) }
                    }

                    override fun onFailure(code: Int, desc: String) {
                        scope.launch {
                            if (finish(attempt, false)) Log.e(TAG, "IM synchronization failed: $code")
                        }
                    }
                })
            }
        } catch (error: Exception) {
            if (finish(attempt, false)) Log.e(TAG, "Call initialization could not start: ${error.javaClass.simpleName}")
        }
    }

    private fun initializeEngine(
        context: Context,
        sdkAppId: Int,
        userId: String,
        userSig: String,
        attempt: XingDunCallInitializationGate.Attempt,
    ) {
        if (!isCurrent(attempt, sdkAppId, userId) || !matchesIM(sdkAppId, userId)) {
            finish(attempt, false)
            return
        }
        try {
            // Constructing CallStore attaches its Engine observer before init can deliver an invitation.
            val callStore = CallStore.shared
            val engine = TUICallEngine.createInstance(context)
            engine.init(sdkAppId, userId, userSig, object : TUICommonDefine.Callback {
                override fun onSuccess() {
                    scope.launch {
                        if (!isCurrent(attempt, sdkAppId, userId) || gate.isReady(attempt) ||
                            !matchesIM(sdkAppId, userId)
                        ) {
                            if (!gate.isReady(attempt)) finish(attempt, false)
                            return@launch
                        }
                        try {
                            NotificationFeature(context).registerNotificationBannerChannel()
                            CallingBellFeature(context)
                            CallingVibratorFeature(context)
                            engine.enableMultiDeviceAbility(true, null)
                            // Its StateFlow observer must exist before the explicit offline query.
                            TUICallKit.createInstance(context).enableIncomingBanner(true)
                            if (callStore.observerState.selfInfo.value.status == CallParticipantStatus.None) {
                                runCatching { engine.queryOfflineCall() }.onFailure { error ->
                                    Log.w(TAG, "Offline call query could not start: ${error.javaClass.simpleName}")
                                }
                            }
                            if (finish(attempt, true)) Log.i(TAG, "Call engine ready for the active XingDun session")
                        } catch (error: Exception) {
                            if (finish(attempt, false)) Log.e(TAG, "Call UI initialization failed: ${error.javaClass.simpleName}")
                        }
                    }
                }

                override fun onError(errCode: Int, errMsg: String) {
                    scope.launch {
                        if (finish(attempt, false)) Log.e(TAG, "Call engine initialization failed: $errCode")
                    }
                }
            })
        } catch (error: Exception) {
            if (finish(attempt, false)) Log.e(TAG, "Call engine initialization could not start: ${error.javaClass.simpleName}")
        }
    }

    fun reset() {
        val displaced = synchronized(lock) {
            gate.reset()
            pending.also { pending = null }
        }
        displaced?.timeout?.cancel()
        displaced?.ready?.complete(false)
    }

    private fun finish(attempt: XingDunCallInitializationGate.Attempt, ready: Boolean): Boolean {
        val completed = synchronized(lock) {
            val accepted = if (ready) gate.complete(attempt) else gate.fail(attempt)
            if (!accepted) return false
            pending?.takeIf { it.attempt == attempt }.also { pending = null }
        }
        completed?.timeout?.cancel()
        completed?.ready?.complete(ready)
        return true
    }

    private fun isCurrent(attempt: XingDunCallInitializationGate.Attempt, sdkAppId: Int, userId: String): Boolean =
        gate.isCurrent(attempt) && currentIdentity(sdkAppId, userId) == attempt.identity

    private fun currentIdentity(sdkAppId: Int, userId: String): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        if (session.sdkAppId != sdkAppId || session.timUserId != userId) return null
        val tenant = XingDunTenantBoundary.identity(session) ?: return null
        return "${session.apiBaseUrl}:${tenant.key}:$userId"
    }

    private fun matchesIM(sdkAppId: Int, userId: String): Boolean =
        V2TIMManager.getInstance().loginStatus == V2TIMManager.V2TIM_STATUS_LOGINED &&
            LoginStore.shared.sdkAppID == sdkAppId && TUILogin.getLoginUser() == userId

    private const val TAG = "XingDunCallSession"
    private const val INITIALIZATION_TIMEOUT_MILLIS = 10_000L
}
