package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallPhase

/** Changes only when the displayed controls or their bound call identity must change. */
internal data class CallControlsRenderKey(
    val identity: NativeCallIdentity,
    val phase: NativeCallPhase,
    val isVideo: Boolean?,
    val isCaller: Boolean,
)

internal class CallControlsRenderPolicy {
    private var displayed: CallControlsRenderKey? = null

    fun update(key: CallControlsRenderKey): Boolean {
        if (displayed == key) return false
        displayed = key
        return true
    }

    fun reset() {
        displayed = null
    }
}
