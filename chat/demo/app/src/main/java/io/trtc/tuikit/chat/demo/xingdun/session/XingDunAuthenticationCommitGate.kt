package io.trtc.tuikit.chat.demo.xingdun.session

import kotlinx.coroutines.CancellationException

/** Network work stays outside this gate; only ownership checks and local writes are serialized. */
internal class XingDunAuthenticationCommitGate {
    private var generation = 0L

    @Synchronized
    fun currentGeneration(): Long = generation

    @Synchronized
    fun begin(): Long = ++generation

    @Synchronized
    fun <T> commit(ticket: Long, action: () -> T): T {
        if (ticket != generation) throw CancellationException("Authentication attempt was replaced")
        return action()
    }

    @Synchronized
    fun invalidateAndRun(action: () -> Unit) {
        generation++
        action()
    }

    @Synchronized
    fun updateContext(isSameContext: () -> Boolean, action: () -> Unit) {
        if (!isSameContext()) generation++
        action()
    }
}
