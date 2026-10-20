package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

/** A terminal event must win over delayed SDK snapshots for the same call and account. */
internal class NativeCallTerminationGate {
    private val endedCalls = linkedSetOf<NativeCallIdentity>()

    @Synchronized
    fun recordEnded(identity: NativeCallIdentity): Boolean {
        if (!identity.isValid()) return false
        // Replayed events neither consume capacity nor reorder more recent terminal calls.
        if (endedCalls.add(identity) && endedCalls.size > MAX_ENDED_CALLS) {
            endedCalls.iterator().apply {
                next()
                remove()
            }
        }
        return true
    }

    @Synchronized
    fun canPresent(identity: NativeCallIdentity): Boolean = identity.isValid() && identity !in endedCalls

    @Synchronized
    fun shouldClear(endedIdentity: NativeCallIdentity, currentIdentity: NativeCallIdentity?): Boolean =
        endedIdentity.isValid() && endedIdentity == currentIdentity && endedIdentity in endedCalls

    /** Only an explicit successful user-initiated join may re-enter a still-active group call. */
    @Synchronized
    fun allowJoinedCall(identity: NativeCallIdentity) {
        if (identity.isValid()) endedCalls.remove(identity)
    }

    private fun NativeCallIdentity.isValid(): Boolean =
        callId.isNotBlank() && sdkAppId > 0 && selfId.isNotBlank()

    private companion object {
        const val MAX_ENDED_CALLS = 32
    }
}
