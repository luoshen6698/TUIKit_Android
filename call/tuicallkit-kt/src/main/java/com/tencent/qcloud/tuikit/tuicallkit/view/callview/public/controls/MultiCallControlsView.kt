package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallPhase
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MultiCallControlsView(context: Context) : ConstraintLayout(context) {
    private var subscribeStateJob: Job? = null
    private val renderPolicy = CallControlsRenderPolicy()

    var enableVirtualBackground: Boolean = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderPolicy.reset()
        registerObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeStateJob?.cancel()
    }

    private fun registerObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            val state = CallStore.shared.observerState
            combine(state.selfInfo, state.activeCall) { self, call ->
                CallControlsRenderKey(
                    NativeCallIdentity(call.callId, LoginStore.shared.sdkAppID, self.id),
                    when (self.status) {
                        CallParticipantStatus.Waiting -> NativeCallPhase.Waiting
                        CallParticipantStatus.Accept -> NativeCallPhase.Accepted
                        else -> NativeCallPhase.None
                    },
                    call.mediaType?.let { it == CallMediaType.Video },
                    self.id == call.inviterId,
                )
            }.distinctUntilChanged().collect { updateLayout(it) }
        }
    }

    private fun updateLayout(key: CallControlsRenderKey) {
        if (!renderPolicy.update(key)) return
        removeAllViews()
        if (key.phase == NativeCallPhase.None) return
        val newLayout = if (key.phase == NativeCallPhase.Waiting && !key.isCaller) {
            AudioAndVideoCalleeWaitingView(context)
        } else {
            VideoCallerAndCalleeAcceptedView(context).apply {
                this.enableVirtualBackground = this@MultiCallControlsView.enableVirtualBackground
            }
        }
        addView(newLayout)
    }
}
