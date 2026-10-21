package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunSessionRestorePolicyTest {

    @Test
    fun coldStartValidatesServerOwnershipEvenWhileBothCredentialsAreUsable() {
        assertEquals(
            XingDunSessionRestorePlan.REFRESH_SESSION,
            XingDunSessionRestorePolicy.plan(session(), NOW, preferCachedCredentials = true),
        )
    }

    @Test
    fun legacyRegistrationSessionRenewsExpiredUserSigWithValidAccessToken() {
        val legacy = session(
            refreshToken = null,
            refreshExpiresAtMillis = null,
            userSigExpiresAtMillis = NOW,
        )

        assertEquals(
            XingDunSessionRestorePlan.REFRESH_IM_CREDENTIAL,
            XingDunSessionRestorePolicy.plan(legacy, NOW, preferCachedCredentials = true),
        )
    }

    @Test
    fun validRefreshTokenRenewsEntireSessionAfterLongIdle() {
        val longIdle = session(
            accessExpiresAtMillis = NOW,
            userSigExpiresAtMillis = NOW,
            refreshExpiresAtMillis = NOW + 1,
        )

        assertEquals(
            XingDunSessionRestorePlan.REFRESH_SESSION,
            XingDunSessionRestorePolicy.plan(longIdle, NOW, preferCachedCredentials = true),
        )
    }

    @Test
    fun refreshTokenAtLocalExpiryBoundaryStillRequiresServerValidation() {
        val expired = session(
            accessExpiresAtMillis = NOW,
            userSigExpiresAtMillis = NOW,
            refreshExpiresAtMillis = NOW,
        )

        assertEquals(
            XingDunSessionRestorePlan.REFRESH_SESSION,
            XingDunSessionRestorePolicy.plan(expired, NOW, preferCachedCredentials = true),
        )
    }

    @Test
    fun locallyExpiredRefreshCredentialIsNotDiscardedWhenDeviceClockMovesForward() {
        val locallyExpired = session(
            accessExpiresAtMillis = NOW,
            userSigExpiresAtMillis = NOW,
            refreshExpiresAtMillis = NOW - 1,
        )
        assertEquals(
            XingDunSessionRestorePlan.REFRESH_SESSION,
            XingDunSessionRestorePolicy.plan(locallyExpired, NOW + 365L * 24 * 60 * 60 * 1000,
                preferCachedCredentials = false),
        )
    }

    @Test
    fun retainedRefreshCredentialWithoutCachedExpiryStillRequiresServerValidation() {
        assertEquals(
            XingDunSessionRestorePlan.REFRESH_SESSION,
            XingDunSessionRestorePolicy.plan(session(refreshExpiresAtMillis = null), NOW,
                preferCachedCredentials = true),
        )
    }

    @Test
    fun blankRefreshCredentialCannotRestoreAnExpiredAccessToken() {
        assertEquals(
            XingDunSessionRestorePlan.EXPIRED,
            XingDunSessionRestorePolicy.plan(session(accessExpiresAtMillis = NOW, refreshToken = " "),
                NOW, preferCachedCredentials = true),
        )
    }

    private fun session(
        accessExpiresAtMillis: Long = NOW + 3_600_000,
        refreshToken: String? = "refresh",
        refreshExpiresAtMillis: Long? = NOW + 15_552_000_000,
        userSigExpiresAtMillis: Long = NOW + 3_600_000,
    ) = XingDunStoredSession(
        accessToken = "access",
        tokenType = "Bearer",
        accessExpiresAtMillis = accessExpiresAtMillis,
        refreshToken = refreshToken,
        refreshExpiresAtMillis = refreshExpiresAtMillis,
        companyCode = "xc2026",
        companyId = 1,
        companyName = "XingDun",
        apiBaseUrl = "https://example.com/prod/im/v1",
        sdkAppId = 1_000,
        timUserId = "xd_xc2026_1",
        userSig = "user-sig",
        userSigExpiresAtMillis = userSigExpiresAtMillis,
        nickname = "User",
    )

    companion object {
        private const val NOW = 1_000_000L
    }
}
