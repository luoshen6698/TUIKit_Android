package io.trtc.tuikit.chat.demo.xingdun.call

internal class XingDunCallInitializationGate {
    data class Attempt(val identity: String, val generation: Long)
    enum class Status { START, JOIN, READY }
    data class Admission(val attempt: Attempt, val status: Status)

    private var generation = 0L
    private var current: Attempt? = null
    private var ready = false

    @Synchronized
    fun begin(identity: String): Admission? {
        if (identity.isBlank()) return null
        current?.takeIf { it.identity == identity }?.let {
            return Admission(it, if (ready) Status.READY else Status.JOIN)
        }
        val attempt = Attempt(identity, ++generation)
        current = attempt
        ready = false
        return Admission(attempt, Status.START)
    }

    @Synchronized
    fun complete(attempt: Attempt): Boolean {
        if (current != attempt || ready) return false
        ready = true
        return true
    }

    @Synchronized
    fun fail(attempt: Attempt): Boolean {
        if (current != attempt || ready) return false
        current = null
        return true
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = current == attempt

    @Synchronized
    fun isReady(attempt: Attempt): Boolean = current == attempt && ready

    @Synchronized
    fun reset() {
        generation++
        current = null
        ready = false
    }
}
