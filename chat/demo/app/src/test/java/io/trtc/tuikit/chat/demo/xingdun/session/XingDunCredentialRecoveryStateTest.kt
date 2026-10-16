package io.trtc.tuikit.chat.demo.xingdun.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class XingDunCredentialRecoveryStateTest {

    @Test
    fun duplicateRefreshIsIgnoredUntilCurrentRefreshCompletes() {
        val gate = XingDunCredentialRecoveryGate()

        val ticket = gate.tryBeginRefresh()!!
        assertNull(gate.tryBeginRefresh())
        assertEquals(XingDunCredentialRecoveryState.REFRESHING, gate.current())

        assertTrue(gate.completeRefresh(ticket))
        assertNotNull(gate.tryBeginRefresh())
    }

    @Test
    fun redirectWinsOverRefreshAndOnlyRunsOnce() {
        val gate = XingDunCredentialRecoveryGate()

        val ticket = gate.tryBeginRefresh()!!
        assertTrue(gate.tryBeginRedirect())
        assertFalse(gate.tryBeginRedirect())
        assertFalse(gate.completeRefresh(ticket))
        assertEquals(XingDunCredentialRecoveryState.REDIRECTING, gate.current())
    }

    @Test
    fun authenticationResetsRedirectState() {
        val gate = XingDunCredentialRecoveryGate()
        gate.tryBeginRedirect()

        gate.markAuthenticated()

        assertEquals(XingDunCredentialRecoveryState.NORMAL, gate.current())
        assertNotNull(gate.tryBeginRefresh())
    }

    @Test
    fun oldRefreshCannotCompleteOrRedirectAfterNewAuthentication() {
        val gate = XingDunCredentialRecoveryGate()
        val oldTicket = gate.tryBeginRefresh()!!
        gate.markAuthenticated()
        val newTicket = gate.tryBeginRefresh()!!
        assertFalse(gate.completeRefresh(oldTicket))
        assertFalse(gate.tryBeginRedirect(oldTicket))
        assertEquals(XingDunCredentialRecoveryState.REFRESHING, gate.current())
        assertTrue(gate.completeRefresh(newTicket))
    }

    @Test fun interactiveLoginSupersedesRefreshBeforeItFinishes() {
        val gate = XingDunCredentialRecoveryGate()
        val oldTicket = gate.tryBeginRefresh()!!
        gate.markAuthenticating()
        assertFalse(gate.completeRefresh(oldTicket))
        assertFalse(gate.tryBeginRedirect(oldTicket))
        assertNull(gate.tryBeginRefresh())
        gate.markAuthenticated()
        assertNotNull(gate.tryBeginRefresh())
    }

    @Test
    fun staleAuthenticationFailureCannotResetANewerAuthentication() {
        val gate = XingDunCredentialRecoveryGate()
        val previousTicket = gate.markAuthenticating()
        val currentTicket = gate.markAuthenticating()

        assertFalse(gate.ownsAuthentication(previousTicket))
        assertTrue(gate.ownsAuthentication(currentTicket))
        assertFalse(gate.completeAuthenticationFailure(previousTicket))
        assertEquals(XingDunCredentialRecoveryState.AUTHENTICATING, gate.current())
        assertTrue(gate.ownsAuthentication(currentTicket))
        assertTrue(gate.completeAuthenticationFailure(currentTicket))
        assertEquals(XingDunCredentialRecoveryState.NORMAL, gate.current())
        assertNotNull(gate.tryBeginRefresh())
    }

    @Test
    fun authenticationSuccessInvalidatesItsLateFailure() {
        val gate = XingDunCredentialRecoveryGate()
        val ticket = gate.markAuthenticating()
        gate.markAuthenticated()

        assertFalse(gate.ownsAuthentication(ticket))
        assertFalse(gate.completeAuthenticationFailure(ticket))
        assertEquals(XingDunCredentialRecoveryState.NORMAL, gate.current())
    }

    @Test
    fun legacyAuthenticationFailureOnlyResetsAnAuthenticatingState() {
        val gate = XingDunCredentialRecoveryGate()
        assertFalse(gate.completeAuthenticationFailure())
        gate.markAuthenticating()
        assertTrue(gate.completeAuthenticationFailure())
        val refreshTicket = gate.tryBeginRefresh()!!

        assertFalse(gate.completeAuthenticationFailure())
        assertTrue(gate.ownsRefresh(refreshTicket))
    }

    @Test fun missingIMIdentityRecoversOnlyWithAnExistingBusinessSession() {
        assertTrue(XingDunMissingIMSessionPolicy.canRecover(true, null))
        assertTrue(XingDunMissingIMSessionPolicy.canRecover(true, ""))
        assertFalse(XingDunMissingIMSessionPolicy.canRecover(false, null))
        assertFalse(XingDunMissingIMSessionPolicy.canRecover(true, "another_account"))
    }

    @Test
    fun refreshPolicyStartsFiveMinutesBeforeExpiry() {
        val now = 1_000_000L
        val lead = XingDunCredentialRefreshPolicy.REFRESH_LEAD_TIME_MILLIS

        assertFalse(XingDunCredentialRefreshPolicy.shouldRefresh(now + lead + 1, now))
        assertTrue(XingDunCredentialRefreshPolicy.shouldRefresh(now + lead, now))
        assertTrue(XingDunCredentialRefreshPolicy.shouldRefresh(now - 1, now))
        assertEquals(1L, XingDunCredentialRefreshPolicy.delayUntilRefresh(now + lead + 1, now))
        assertEquals(0L, XingDunCredentialRefreshPolicy.delayUntilRefresh(now + lead, now))
    }
}
