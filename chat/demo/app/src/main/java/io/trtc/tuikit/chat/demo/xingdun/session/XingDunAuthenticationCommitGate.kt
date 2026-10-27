package io.trtc.tuikit.chat.demo.xingdun.session

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/** Network work stays outside this gate; only ownership checks and local writes are serialized. */
internal class XingDunAuthenticationCommitGate {
    private val generation = AtomicLong()
    private val activeActions = AtomicInteger()

    @Synchronized
    fun currentGeneration(): Long = generation.get()

    /** An identity snapshot cannot observe a transaction between its separate secure-store writes. */
    fun currentReadGeneration(): Long? {
        if (activeActions.get() != 0) return null
        val value = generation.get()
        return value.takeIf { activeActions.get() == 0 && value == generation.get() }
    }

    private fun <T> actionInProgress(action: () -> T): T {
        activeActions.incrementAndGet()
        try { return action() } finally { activeActions.decrementAndGet() }
    }

    @Synchronized
    fun begin(): Long = actionInProgress { generation.incrementAndGet() }

    @Synchronized
    fun <T> commit(ticket: Long, action: () -> T): T {
        if (ticket != generation.get()) throw CancellationException("Authentication attempt was replaced")
        return actionInProgress(action)
    }

    @Synchronized
    fun invalidateAndRun(action: () -> Unit) {
        actionInProgress {
            generation.incrementAndGet()
            action()
        }
    }

    @Synchronized
    fun updateContext(isSameContext: () -> Boolean, action: () -> Unit) {
        actionInProgress {
            if (!isSameContext()) generation.incrementAndGet()
            action()
        }
    }
}
