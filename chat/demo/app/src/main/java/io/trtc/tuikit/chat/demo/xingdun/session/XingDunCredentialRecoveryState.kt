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
    fun markAuthenticating(): Long {
        state = XingDunCredentialRecoveryState.AUTHENTICATING
        return ++generation
    }

    @Synchronized
    fun ownsAuthentication(ticket: Long): Boolean =
        state == XingDunCredentialRecoveryState.AUTHENTICATING && ticket == generation

    @Synchronized
    fun completeAuthenticationFailure(ticket: Long? = null): Boolean {
        if (state != XingDunCredentialRecoveryState.AUTHENTICATING ||
            (ticket != null && !ownsAuthentication(ticket))
        ) {
            return false
        }
        generation++
        state = XingDunCredentialRecoveryState.NORMAL
        return true
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

internal enum class XingDunProtectedSessionDestination { CONTENT, RECOVERY, LOGIN }

/** An unfinished SDK login must never expose a protected Activity or erase recoverable credentials. */
internal object XingDunProtectedSessionPolicy {
    fun destination(
        hasBusinessSession: Boolean,
        hasStoredSession: Boolean,
        recoveryState: XingDunCredentialRecoveryState,
        sdkIdentityMatches: Boolean,
        imIsLoggedIn: Boolean,
    ): XingDunProtectedSessionDestination = when {
        recoveryState == XingDunCredentialRecoveryState.REDIRECTING -> XingDunProtectedSessionDestination.LOGIN
        recoveryState == XingDunCredentialRecoveryState.AUTHENTICATING ||
            recoveryState == XingDunCredentialRecoveryState.REFRESHING -> XingDunProtectedSessionDestination.RECOVERY
        !hasBusinessSession && !hasStoredSession -> XingDunProtectedSessionDestination.LOGIN
        hasBusinessSession && sdkIdentityMatches && imIsLoggedIn -> XingDunProtectedSessionDestination.CONTENT
        else -> XingDunProtectedSessionDestination.RECOVERY
    }
}

/** Callback ownership is checked on the main thread before any global login or navigation effect. */
internal object XingDunAuthenticationCallbackPolicy {
    fun canApply(
        expectedTicket: Long,
        activityTicket: Long?,
        authenticationOwned: Boolean,
        activityActive: Boolean,
    ): Boolean = activityActive && expectedTicket == activityTicket && authenticationOwned
}

internal object XingDunLaunchRecoveryPolicy {
    fun shouldRecover(autoRestoreAllowed: Boolean, hasBusinessSession: Boolean, hasStoredSession: Boolean): Boolean =
        autoRestoreAllowed && (hasBusinessSession || hasStoredSession)
}
