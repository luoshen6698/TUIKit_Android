package io.trtc.tuikit.chat.demo.xingdun.session

internal enum class XingDunCredentialRecoveryState {
    NORMAL,
    REFRESHING,
    AUTHENTICATING,
    REDIRECTING,
}

internal class XingDunCredentialRecoveryGate {
    private var state = XingDunCredentialRecoveryState.NORMAL
    private var generation = 0L

    @Synchronized
    fun tryBeginRefresh(): Long? {
        if (state != XingDunCredentialRecoveryState.NORMAL) return null
        state = XingDunCredentialRecoveryState.REFRESHING
        return ++generation
    }

    @Synchronized
    fun completeRefresh(ticket: Long): Boolean {
        if (!ownsRefresh(ticket)) return false
        state = XingDunCredentialRecoveryState.NORMAL
        return true
    }

    @Synchronized
    fun tryBeginRedirect(ticket: Long? = null): Boolean {
        if (ticket != null && !ownsRefresh(ticket)) return false
        if (state == XingDunCredentialRecoveryState.REDIRECTING) return false
        generation++
        state = XingDunCredentialRecoveryState.REDIRECTING
        return true
    }

    @Synchronized
    fun markAuthenticated() {
        generation++
        state = XingDunCredentialRecoveryState.NORMAL
    }

    @Synchronized
    fun markAuthenticating() {
        generation++
        state = XingDunCredentialRecoveryState.AUTHENTICATING
    }

    @Synchronized
    fun ownsRefresh(ticket: Long): Boolean =
        state == XingDunCredentialRecoveryState.REFRESHING && ticket == generation

    @Synchronized
    fun current(): XingDunCredentialRecoveryState = state
}

internal object XingDunCredentialRefreshPolicy {
    const val REFRESH_LEAD_TIME_MILLIS = 5 * 60 * 1000L

    fun shouldRefresh(userSigExpiresAtMillis: Long, nowMillis: Long): Boolean =
        userSigExpiresAtMillis - nowMillis <= REFRESH_LEAD_TIME_MILLIS

    fun delayUntilRefresh(userSigExpiresAtMillis: Long, nowMillis: Long): Long =
        (userSigExpiresAtMillis - nowMillis - REFRESH_LEAD_TIME_MILLIS).coerceAtLeast(0L)
}

internal object XingDunMissingIMSessionPolicy {
    fun canRecover(hasBusinessSession: Boolean, loginUserId: String?): Boolean =
        hasBusinessSession && loginUserId.isNullOrBlank()
}
