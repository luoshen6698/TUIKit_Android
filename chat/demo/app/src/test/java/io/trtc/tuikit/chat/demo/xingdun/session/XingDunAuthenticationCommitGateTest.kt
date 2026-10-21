package io.trtc.tuikit.chat.demo.xingdun.session

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class XingDunAuthenticationCommitGateTest {
    @Test
    fun earlierLoginResponseCannotOverwriteTheLaterLogin() {
        val gate = XingDunAuthenticationCommitGate()
        val earlier = gate.begin()
        val later = gate.begin()
        var session = "original"
        gate.commit(later) { session = "later-login" }
        assertCancelled { gate.commit(earlier) { session = "earlier-login" } }
        assertEquals("later-login", session)
    }

    @Test
    fun loginAndRegistrationShareTheSameAttemptOwnership() {
        val gate = XingDunAuthenticationCommitGate()
        val login = gate.begin()
        val registration = gate.begin()
        var session = "original"
        assertCancelled { gate.commit(login) { session = "old-login" } }
        gate.commit(registration) { session = "new-registration" }
        assertEquals("new-registration", session)
    }

    @Test
    fun logoutInvalidatesAnOutstandingResponseBeforeClearing() {
        val gate = XingDunAuthenticationCommitGate()
        val attempt = gate.begin()
        var session: String? = "previous"
        gate.invalidateAndRun { session = null }
        assertCancelled { gate.commit(attempt) { session = "late-login" } }
        assertEquals(null, session)
    }

    @Test
    fun changedEnterpriseInvalidatesOutstandingAuthentication() {
        val gate = XingDunAuthenticationCommitGate()
        val attempt = gate.begin()
        var enterprise = "first"
        gate.updateContext(isSameContext = { false }) { enterprise = "second" }
        assertCancelled { gate.commit(attempt) { enterprise = "first" } }
        assertEquals("second", enterprise)
    }

    @Test
    fun sameEnterpriseConfigurationRefreshDoesNotCancelCurrentAuthentication() {
        val gate = XingDunAuthenticationCommitGate()
        val attempt = gate.begin()
        var writes = 0
        gate.updateContext(isSameContext = { true }) { writes++ }
        gate.commit(attempt) { writes++ }
        assertEquals(2, writes)
    }

    @Test
    fun aNewAttemptMayStartBeforeEarlierNetworkWorkCompletes() {
        val gate = XingDunAuthenticationCommitGate()
        val waitingForNetwork = gate.begin()
        val newerAttempt = gate.begin()
        assertEquals("new", gate.commit(newerAttempt) { "new" })
        assertCancelled { gate.commit(waitingForNetwork) { "old" } }
    }

    private fun assertCancelled(action: () -> Unit) {
        try {
            action()
            fail("Replaced authentication must not commit")
        } catch (_: CancellationException) {
            // Expected: the old operation did not execute its local commit action.
        }
    }
}
