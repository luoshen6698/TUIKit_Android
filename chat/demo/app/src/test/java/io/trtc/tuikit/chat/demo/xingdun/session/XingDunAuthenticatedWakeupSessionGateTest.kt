package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunAuthenticatedWakeupSessionGateTest {
    @Test
    fun storedCredentialsWithoutInProcessAuthenticationAreNotProof() {
        assertNull(resolve(XingDunAuthenticatedWakeupSessionGate()))
    }

    @Test
    fun recentAuthenticationAcceptsAnEqualReloadedSessionWithoutRenewingProof() {
        val gate = authenticatedGate()

        assertEquals(session(), resolve(gate, current = session().copy(), ageMillis = 59_999L))
        assertNull(resolve(gate, ageMillis = 60_000L))
    }

    @Test
    fun elapsedClockBeforeAuthenticationInvalidatesProof() {
        val gate = authenticatedGate()

        assertNull(resolve(gate, ageMillis = -1L))
        assertNull(resolve(gate))
    }

    @Test
    fun changedCredentialsOrTenantCannotReuseProof() {
        val original = session()
        val replacements = listOf(
            original.copy(accessToken = "replacement-access"),
            original.copy(refreshToken = "replacement-refresh"),
            original.copy(userSig = "replacement-signature"),
            original.copy(timUserId = "other-user"),
            original.copy(companyId = 2),
            original.copy(companyCode = "other-company"),
            original.copy(sdkAppId = 2_000),
            original.copy(apiBaseUrl = "https://other.example.test/im/v1"),
            original.copy(tenantSchemaVersion = 1),
        )
        replacements.forEach { replacement ->
            val gate = authenticatedGate(original)
            assertNull(resolve(gate, current = replacement))
            // Restoring the old snapshot cannot revive proof after an observed replacement.
            assertNull(resolve(gate, current = original))
        }
    }

    @Test
    fun completeSessionSnapshotMustMatchIncludingBusinessMetadata() {
        val gate = authenticatedGate()

        assertNull(resolve(gate, current = session().copy(nickname = "Updated profile")))
    }

    @Test
    fun removedStoredSessionInvalidatesProof() {
        val gate = authenticatedGate()

        assertNull(resolve(gate, current = null))
        assertNull(resolve(gate))
    }

    @Test
    fun imMustBeLoggedInToExactlyTheAuthenticatedIdentity() {
        assertNull(resolve(authenticatedGate(), imIsLoggedIn = false))
        assertNull(resolve(authenticatedGate(), imSdkAppId = 2_000))
        assertNull(resolve(authenticatedGate(), imUserId = "other-user"))
        assertNull(resolve(authenticatedGate(), imUserId = null))
    }

    @Test
    fun accessTokenAtSafetyBoundaryCannotBeReused() {
        val expiring = session().copy(
            accessExpiresAtMillis = NOW_EPOCH + XingDunAuthenticatedWakeupSessionGate.ACCESS_EXPIRY_SAFETY_MILLIS,
        )

        assertNull(resolve(authenticatedGate(expiring), current = expiring))
    }

    @Test
    fun userSigAtProactiveRefreshBoundaryCannotBeReused() {
        val expiring = session().copy(
            userSigExpiresAtMillis = NOW_EPOCH + XingDunAuthenticatedWakeupSessionGate.USER_SIG_EXPIRY_SAFETY_MILLIS,
        )

        assertNull(resolve(authenticatedGate(expiring), current = expiring))
    }

    @Test
    fun credentialsJustOutsideSafetyBoundariesCanBeReused() {
        val usable = session().copy(
            accessExpiresAtMillis = NOW_EPOCH + XingDunAuthenticatedWakeupSessionGate.ACCESS_EXPIRY_SAFETY_MILLIS + 1L,
            userSigExpiresAtMillis = NOW_EPOCH + XingDunAuthenticatedWakeupSessionGate.USER_SIG_EXPIRY_SAFETY_MILLIS + 1L,
        )

        assertEquals(usable, resolve(authenticatedGate(usable), current = usable))
    }

    @Test
    fun authenticationInvalidationClearsTheProof() {
        val gate = authenticatedGate()
        gate.clear()

        assertNull(resolve(gate))
    }

    @Test
    fun logoutBlocksLateAuthenticationUntilCleanupAndStillRequiresNewProof() {
        val gate = authenticatedGate()
        gate.beginSessionLeave()
        gate.recordAuthenticated(session(), NOW_ELAPSED)
        assertNull(resolve(gate))

        gate.clear()
        gate.recordAuthenticated(session(), NOW_ELAPSED)
        assertNull(resolve(gate))

        gate.finishSessionLeave()
        assertNull(resolve(gate))
        gate.recordAuthenticated(session(), NOW_ELAPSED)
        assertEquals(session(), resolve(gate))
    }

    @Test
    fun aNewServerAuthenticationReplacesThePreviousProof() {
        val gate = authenticatedGate()
        val renewed = session().copy(accessToken = "renewed-access")
        gate.recordAuthenticated(renewed, NOW_ELAPSED + 60_000L)

        assertEquals(renewed, resolve(gate, current = renewed, ageMillis = 60_000L))
    }

    private fun authenticatedGate(
        session: XingDunStoredSession = session(),
    ) = XingDunAuthenticatedWakeupSessionGate().apply {
        recordAuthenticated(session, NOW_ELAPSED)
    }

    private fun resolve(
        gate: XingDunAuthenticatedWakeupSessionGate,
        current: XingDunStoredSession? = session(),
        imSdkAppId: Int = 1_000,
        imUserId: String? = "test-user",
        imIsLoggedIn: Boolean = true,
        ageMillis: Long = 0L,
    ) = gate.authenticatedSession(
        currentSession = current,
        imSdkAppId = imSdkAppId,
        imUserId = imUserId,
        imIsLoggedIn = imIsLoggedIn,
        nowElapsedMillis = NOW_ELAPSED + ageMillis,
        nowEpochMillis = NOW_EPOCH + ageMillis,
    )

    private fun session() = XingDunStoredSession(
        accessToken = "test-access",
        tokenType = "Bearer",
        accessExpiresAtMillis = NOW_EPOCH + 3_600_000L,
        refreshToken = "test-refresh",
        refreshExpiresAtMillis = NOW_EPOCH + 86_400_000L,
        companyCode = "test-company",
        companyId = 1,
        companyName = "Test company",
        apiBaseUrl = "https://example.test/im/v1",
        sdkAppId = 1_000,
        timUserId = "test-user",
        userSig = "test-signature",
        userSigExpiresAtMillis = NOW_EPOCH + 3_600_000L,
        nickname = "Test user",
    )

    companion object {
        private const val NOW_EPOCH = 1_000_000L
        private const val NOW_ELAPSED = 10_000L
    }
}
