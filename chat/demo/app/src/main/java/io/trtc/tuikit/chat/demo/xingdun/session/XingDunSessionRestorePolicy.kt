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

    @Suppress("UNUSED_PARAMETER")
    fun plan(
        session: XingDunStoredSession,
        nowMillis: Long,
        preferCachedCredentials: Boolean,
    ): XingDunSessionRestorePlan {
        val accessIsUsable = session.accessExpiresAtMillis > nowMillis + EXPIRY_SAFETY_MILLIS
        // Local expiry estimates can be stale or affected by a changed device clock.
        // Only the server can accept or reject the retained refresh credential.
        val canRefreshSession = !session.refreshToken.isNullOrBlank()
        return when {
            canRefreshSession -> XingDunSessionRestorePlan.REFRESH_SESSION
            // A locally unexpired UserSig does not prove ownership after another phone logs in.
            accessIsUsable -> XingDunSessionRestorePlan.REFRESH_IM_CREDENTIAL
            else -> XingDunSessionRestorePlan.EXPIRED
        }
    }
}
