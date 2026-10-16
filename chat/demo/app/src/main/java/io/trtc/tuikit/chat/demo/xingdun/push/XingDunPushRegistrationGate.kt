package io.trtc.tuikit.chat.demo.xingdun.push

internal class XingDunPushRegistrationGate {
    data class Attempt(val identity: String, val generation: Long)

    private var generation = 0L
    private var current: Attempt? = null
    private var inProgress = false
    private var mirrorInProgress = false
    private var mirrored = false

    @Synchronized
    fun begin(identity: String): Attempt? {
        if (identity.isBlank() || current?.identity == identity) return null
        return Attempt(identity, ++generation).also {
            current = it
            inProgress = true
            mirrorInProgress = false
            mirrored = false
        }
    }

    @Synchronized
    fun complete(attempt: Attempt): Boolean {
        if (current != attempt || !inProgress) return false
        inProgress = false
        return true
    }

    @Synchronized
    fun fail(attempt: Attempt): Boolean {
        if (current != attempt || !inProgress) return false
        current = null
        inProgress = false
        mirrorInProgress = false
        mirrored = false
        return true
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = current == attempt

    @Synchronized
    fun registeredAttempt(identity: String): Attempt? =
        current?.takeIf { it.identity == identity && !inProgress }

    @Synchronized
    fun beginMirror(attempt: Attempt): Boolean {
        if (current != attempt || inProgress || mirrorInProgress || mirrored) return false
        mirrorInProgress = true
        return true
    }

    @Synchronized
    fun finishMirror(attempt: Attempt, success: Boolean): Boolean {
        if (current != attempt || inProgress || !mirrorInProgress) return false
        mirrorInProgress = false
        mirrored = success
        return true
    }

    @Synchronized
    fun reset() {
        generation++
        current = null
        inProgress = false
        mirrorInProgress = false
        mirrored = false
    }
}
