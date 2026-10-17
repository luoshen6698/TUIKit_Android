package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

internal data class NativeCallIdentity(val callId: String, val sdkAppId: Int, val selfId: String)

internal enum class NativeCallPhase { None, Waiting, Accepted }

internal enum class NativeCallAction { Accept, Reject, Hangup }

/** Binds notification actions to the current restored call and rejects stale completions. */
internal class NativeCallActionGate {
    data class Attempt(val identity: NativeCallIdentity, val action: NativeCallAction, val generation: Long)

    private var identity: NativeCallIdentity? = null
    private var phase = NativeCallPhase.None
    private var generation = 0L
    private var inProgress: Attempt? = null
    private val completedActions = mutableSetOf<NativeCallAction>()

    @Synchronized
    fun update(identity: NativeCallIdentity?, phase: NativeCallPhase) {
        val nextIdentity = identity?.takeIf { it.isValid() && phase != NativeCallPhase.None }
        val nextPhase = if (nextIdentity == null) NativeCallPhase.None else phase
        if (this.identity == nextIdentity && this.phase == nextPhase) return

        generation++
        this.identity = nextIdentity
        this.phase = nextPhase
        inProgress = null
        completedActions.clear()
    }

    @Synchronized
    fun canOpen(identity: NativeCallIdentity): Boolean =
        this.identity == identity && phase != NativeCallPhase.None

    @Synchronized
    fun begin(identity: NativeCallIdentity, action: NativeCallAction): Attempt? {
        if (this.identity != identity || inProgress != null || action in completedActions) return null
        val allowed = when (phase) {
            NativeCallPhase.None -> false
            NativeCallPhase.Waiting -> action == NativeCallAction.Accept || action == NativeCallAction.Reject
            NativeCallPhase.Accepted -> action == NativeCallAction.Hangup
        }
        if (!allowed) return null

        return Attempt(identity, action, ++generation).also { inProgress = it }
    }

    @Synchronized
    fun finish(attempt: Attempt, success: Boolean): Boolean {
        if (inProgress != attempt) return false
        inProgress = null
        if (success) completedActions += attempt.action
        return true
    }

    @Synchronized
    fun reset() {
        generation++
        identity = null
        phase = NativeCallPhase.None
        inProgress = null
        completedActions.clear()
    }

    private fun NativeCallIdentity.isValid(): Boolean =
        callId.isNotBlank() && sdkAppId > 0 && selfId.isNotBlank()
}
