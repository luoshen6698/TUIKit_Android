package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession

internal enum class XingDunSessionRestorePlan {
    USE_CACHED,
    REFRESH_SESSION,
    REFRESH_IM_CREDENTIAL,
    EXPIRED,
}

internal object XingDunSessionRestorePolicy {
    const val EXPIRY_SAFETY_MILLIS = 60_000L

    fun plan(
        session: XingDunStoredSession,
        nowMillis: Long,
        preferCachedCredentials: Boolean,
    ): XingDunSessionRestorePlan {
        val accessIsUsable = session.accessExpiresAtMillis > nowMillis + EXPIRY_SAFETY_MILLIS
        val imIsUsable = session.userSigExpiresAtMillis > nowMillis + EXPIRY_SAFETY_MILLIS
        val accessAndIMAreUsable = accessIsUsable && imIsUsable
        if (preferCachedCredentials && accessAndIMAreUsable) {
            return XingDunSessionRestorePlan.USE_CACHED
        }

        val canRefreshSession = !session.refreshToken.isNullOrBlank() &&
            (session.refreshExpiresAtMillis ?: 0L) > nowMillis
        return when {
            canRefreshSession -> XingDunSessionRestorePlan.REFRESH_SESSION
            accessAndIMAreUsable -> XingDunSessionRestorePlan.USE_CACHED
            accessIsUsable -> XingDunSessionRestorePlan.REFRESH_IM_CREDENTIAL
            else -> XingDunSessionRestorePlan.EXPIRED
        }
    }
}
