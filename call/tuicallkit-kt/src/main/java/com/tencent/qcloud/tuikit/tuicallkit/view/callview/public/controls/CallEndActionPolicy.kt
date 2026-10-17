package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallPhase

/** Keeps a control view's pending end action bound to the call it actually displayed. */
internal class CallEndActionPolicy {
    data class Attempt(val identity: NativeCallIdentity, val generation: Long)

    private var boundIdentity: NativeCallIdentity? = null
    private var pending: Attempt? = null
    private var generation = 0L

    fun attach(identity: NativeCallIdentity) {
        reset()
        boundIdentity = identity.takeIf { it.isValid() }
    }

    fun begin(identity: NativeCallIdentity, attached: Boolean, actionAllowed: Boolean): Attempt? {
        if (blockedReason(identity, attached, actionAllowed) != null) return null
        // The SDK may assign an outgoing call ID after the view was attached.
        boundIdentity = identity
        return Attempt(identity, ++generation).also { pending = it }
    }

    fun blockedReason(identity: NativeCallIdentity, attached: Boolean, actionAllowed: Boolean): String? = when {
        !attached -> "not_attached"
        !identity.isValid() -> "invalid_identity"
        !actionAllowed -> "not_allowed"
        pending != null -> "pending"
        boundIdentity != null && boundIdentity != identity -> "identity_changed"
        else -> null
    }

    fun isPending(attempt: Attempt): Boolean = pending == attempt

    fun release(
        attempt: Attempt, current: NativeCallIdentity, attached: Boolean, actionAllowed: Boolean
    ): Boolean {
        if (pending != attempt) return false
        pending = null
        return attached && actionAllowed && boundIdentity == current && attempt.identity == current
    }

    fun reset() {
        generation++
        pending = null
        boundIdentity = null
    }

    private fun NativeCallIdentity.isValid(): Boolean =
        callId.isNotBlank() && sdkAppId > 0 && selfId.isNotBlank()

    companion object {
        /** The SDK can allow cancel before assigning an outgoing call ID. This ID stays local to one view. */
        fun identityForLocalAction(
            sdkIdentity: NativeCallIdentity, phase: NativeCallPhase, isCaller: Boolean,
            hasInvitees: Boolean, viewToken: String,
        ): NativeCallIdentity {
            if (sdkIdentity.callId.isNotBlank() || sdkIdentity.sdkAppId <= 0 || sdkIdentity.selfId.isBlank() ||
                phase != NativeCallPhase.Waiting || !isCaller || !hasInvitees || viewToken.isBlank()
            ) return sdkIdentity
            return sdkIdentity.copy(callId = "local-caller-view:$viewToken")
        }
    }
}
