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
