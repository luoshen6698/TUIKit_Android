package io.trtc.tuikit.chat.demo.xingdun.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunCredentialRecoveryStateTest {

    @Test
    fun duplicateRefreshIsIgnoredUntilCurrentRefreshCompletes() {
        val gate = XingDunCredentialRecoveryGate()

        assertTrue(gate.tryBeginRefresh())
        assertFalse(gate.tryBeginRefresh())
        assertEquals(XingDunCredentialRecoveryState.REFRESHING, gate.current())

        assertTrue(gate.completeRefresh())
        assertTrue(gate.tryBeginRefresh())
    }

    @Test
    fun redirectWinsOverRefreshAndOnlyRunsOnce() {
        val gate = XingDunCredentialRecoveryGate()

        assertTrue(gate.tryBeginRefresh())
        assertTrue(gate.tryBeginRedirect())
        assertFalse(gate.tryBeginRedirect())
        assertFalse(gate.completeRefresh())
        assertEquals(XingDunCredentialRecoveryState.REDIRECTING, gate.current())
    }

    @Test
    fun authenticationResetsRedirectState() {
        val gate = XingDunCredentialRecoveryGate()
        gate.tryBeginRedirect()

        gate.markAuthenticated()

        assertEquals(XingDunCredentialRecoveryState.NORMAL, gate.current())
        assertTrue(gate.tryBeginRefresh())
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
