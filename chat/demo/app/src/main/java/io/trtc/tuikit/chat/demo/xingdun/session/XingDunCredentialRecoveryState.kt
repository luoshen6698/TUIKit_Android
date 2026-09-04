package io.trtc.tuikit.chat.demo.xingdun.session

internal enum class XingDunCredentialRecoveryState {
    NORMAL,
    REFRESHING,
    REDIRECTING,
}

internal class XingDunCredentialRecoveryGate {
    private var state = XingDunCredentialRecoveryState.NORMAL

    @Synchronized
    fun tryBeginRefresh(): Boolean {
        if (state != XingDunCredentialRecoveryState.NORMAL) return false
        state = XingDunCredentialRecoveryState.REFRESHING
        return true
    }

    @Synchronized
    fun completeRefresh(): Boolean {
        if (state != XingDunCredentialRecoveryState.REFRESHING) return false
        state = XingDunCredentialRecoveryState.NORMAL
        return true
    }

    @Synchronized
    fun tryBeginRedirect(): Boolean {
        if (state == XingDunCredentialRecoveryState.REDIRECTING) return false
        state = XingDunCredentialRecoveryState.REDIRECTING
        return true
    }

    @Synchronized
    fun markAuthenticated() {
        state = XingDunCredentialRecoveryState.NORMAL
    }

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
