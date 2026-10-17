package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallPhase
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.UUID

/** Restores controls after a failed end request; only the SDK's idle state closes the call UI. */
internal class CallEndActionController(private val host: View) {
    private val handler = Handler(Looper.getMainLooper())
    private val policy = CallEndActionPolicy()
    private val viewToken = UUID.randomUUID().toString()
    private var timeout: Runnable? = null
    private var restoreControls: (() -> Unit)? = null
    private var loadingImage: ImageFilterView? = null

    fun attach() = policy.attach(currentIdentity())

    fun detach() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        policy.reset()
        restoreControls = null
        loadingImage?.let { ImageLoader.clear(host.context, it) }
        loadingImage = null
    }

    fun run(reject: Boolean, actionView: View, image: ImageFilterView, vararg relatedControls: View) {
        val sdkIdentity = currentSdkIdentity()
        val identity = currentIdentity(sdkIdentity)
        val attached = host.isAttachedToWindow
        val allowed = isActionAllowed(reject)
        val attempt = policy.begin(identity, attached, allowed) ?: run {
            val reason = policy.blockedReason(identity, attached, allowed) ?: "unavailable"
            Log.i(TAG, "stage=native_call event=end_action_ignored reason=$reason" +
                " has_sdk=${sdkIdentity.sdkAppId > 0} has_self=${sdkIdentity.selfId.isNotBlank()}" +
                " has_call_id=${sdkIdentity.callId.isNotBlank()}")
            return
        }
        val controls = (listOf(actionView) + relatedControls).distinct()
        val enabledStates = controls.map { Triple(it, it.isEnabled, it.alpha) }
        val originalDrawable = image.drawable
        val originalBackground = image.background?.let { it.constantState?.newDrawable()?.mutate() ?: it }
        val originalRoundPercent = image.roundPercent
        restoreControls = {
            ImageLoader.clear(host.context, image)
            image.roundPercent = originalRoundPercent
            image.background = originalBackground
            image.setImageDrawable(originalDrawable)
            enabledStates.forEach { (view, enabled, alpha) ->
                view.isEnabled = enabled
                view.alpha = alpha
            }
        }
        loadingImage = image
        controls.forEach {
            it.isEnabled = false
            it.alpha = 0.8f
        }
        timeout = Runnable { release(attempt, reject, "timeout") }.also {
            handler.postDelayed(it, ACTION_TIMEOUT_MS)
        }
        val completion = object : CompletionHandler {
            override fun onSuccess() {
                // Do not hide an active SDK call. Idle state or the bounded UI timeout takes over.
            }

            override fun onFailure(code: Int, desc: String) {
                handler.post { release(attempt, reject, "failure", code) }
            }
        }
        try {
            image.roundPercent = 1.0f
            image.setBackgroundColor(ContextCompat.getColor(host.context, R.color.callview_button_bg_red))
            ImageLoader.loadGif(host.context, image, R.drawable.callview_hangup_loading)
            Log.i(TAG, "stage=native_call event=end_action_requested action=${if (reject) "reject" else "hangup"}")
            if (reject) CallStore.shared.reject(completion) else CallStore.shared.hangup(completion)
        } catch (error: RuntimeException) {
            Log.w(TAG, "stage=native_call event=end_action_unavailable type=${error.javaClass.simpleName}")
            release(attempt, reject, "unavailable")
        }
    }

    private fun release(attempt: CallEndActionPolicy.Attempt, reject: Boolean, reason: String, code: Int = 0) {
        if (!policy.isPending(attempt)) return
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val restore = restoreControls
        restoreControls = null
        if (!policy.release(attempt, currentIdentity(), host.isAttachedToWindow, isActionAllowed(reject))) return
        restore?.invoke()
        loadingImage = null
        Log.w(TAG, "stage=native_call event=end_action_retry reason=$reason code=$code")
        AtomicToast.show(host.context, host.context.getString(R.string.callkit_end_call_retry), AtomicToast.Style.ERROR)
    }

    private fun currentSdkIdentity() = NativeCallIdentity(
        CallStore.shared.observerState.activeCall.value.callId,
        LoginStore.shared.sdkAppID,
        CallStore.shared.observerState.selfInfo.value.id,
    )

    private fun currentIdentity(sdkIdentity: NativeCallIdentity = currentSdkIdentity()): NativeCallIdentity {
        val state = CallStore.shared.observerState
        val self = state.selfInfo.value
        val call = state.activeCall.value
        return CallEndActionPolicy.identityForLocalAction(
            sdkIdentity,
            when (self.status) {
                CallParticipantStatus.Waiting -> NativeCallPhase.Waiting
                CallParticipantStatus.Accept -> NativeCallPhase.Accepted
                else -> NativeCallPhase.None
            },
            self.id == call.inviterId,
            call.inviteeIds.isNotEmpty(),
            viewToken,
        )
    }

    private fun isActionAllowed(reject: Boolean): Boolean {
        val state = CallStore.shared.observerState
        val self = state.selfInfo.value
        val isCaller = self.id == state.activeCall.value.inviterId
        return if (reject) self.status == CallParticipantStatus.Waiting && !isCaller
        else self.status == CallParticipantStatus.Accept || self.status == CallParticipantStatus.Waiting && isCaller
    }

    private companion object {
        const val TAG = "CallEndAction"
        const val ACTION_TIMEOUT_MS = 8_000L
    }
}
