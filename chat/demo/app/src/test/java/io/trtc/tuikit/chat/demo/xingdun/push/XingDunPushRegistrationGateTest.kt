package io.trtc.tuikit.chat.demo.xingdun.push

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunPushRegistrationGateTest {
    @Test
    fun sameAccountRegistersOnceWhilePendingAndAfterSuccess() {
        val gate = XingDunPushRegistrationGate()
        val attempt = checkNotNull(gate.begin("account-a"))

        assertTrue(gate.isCurrent(attempt))
        assertNull(gate.begin("account-a"))
        assertTrue(gate.complete(attempt))
        assertTrue(gate.isCurrent(attempt))
        assertNull(gate.begin("account-a"))
        assertFalse(gate.complete(attempt))
        assertFalse(gate.fail(attempt))
        assertTrue(gate.isCurrent(attempt))
    }

    @Test
    fun failedRegistrationAllowsRetryWithANewTicket() {
        val gate = XingDunPushRegistrationGate()
        val failed = checkNotNull(gate.begin("account-a"))

        assertTrue(gate.fail(failed))
        assertFalse(gate.isCurrent(failed))
        val retry = checkNotNull(gate.begin("account-a"))
        assertNotEquals(failed.generation, retry.generation)
        assertFalse(gate.complete(failed))
        assertFalse(gate.fail(failed))
        assertTrue(gate.isCurrent(retry))
        assertTrue(gate.complete(retry))
    }

    @Test
    fun previousAccountSuccessCannotCompleteTheNewAccount() {
        val gate = XingDunPushRegistrationGate()
        val previous = checkNotNull(gate.begin("account-a"))
        val active = checkNotNull(gate.begin("account-b"))

        assertFalse(gate.isCurrent(previous))
        assertFalse(gate.complete(previous))
        assertTrue(gate.isCurrent(active))
        assertNull(gate.begin("account-b"))
        assertTrue(gate.complete(active))
    }

    @Test
    fun previousAccountFailureCannotReleaseTheNewAccount() {
        val gate = XingDunPushRegistrationGate()
        val previous = checkNotNull(gate.begin("account-a"))
        val active = checkNotNull(gate.begin("account-b"))

        assertFalse(gate.fail(previous))
        assertTrue(gate.isCurrent(active))
        assertNull(gate.begin("account-b"))
        assertTrue(gate.complete(active))
    }

    @Test
    fun logoutInvalidatesPendingCallbacksBeforeTheSameAccountLogsInAgain() {
        val gate = XingDunPushRegistrationGate()
        val beforeLogout = checkNotNull(gate.begin("account-a"))

        gate.reset()
        assertFalse(gate.isCurrent(beforeLogout))
        val afterLogin = checkNotNull(gate.begin("account-a"))
        assertNotEquals(beforeLogout.generation, afterLogin.generation)
        assertFalse(gate.complete(beforeLogout))
        assertFalse(gate.fail(beforeLogout))
        assertTrue(gate.isCurrent(afterLogin))
        assertTrue(gate.complete(afterLogin))
    }

    @Test
    fun logoutAfterSuccessAllowsTheSameAccountToRegisterAgain() {
        val gate = XingDunPushRegistrationGate()
        val registered = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(registered))

        gate.reset()
        val next = checkNotNull(gate.begin("account-a"))
        assertFalse(gate.isCurrent(registered))
        assertNotEquals(registered.generation, next.generation)
        assertTrue(gate.isCurrent(next))
    }

    @Test
    fun blankIdentityIsRejectedWithoutReplacingTheActiveAccount() {
        val gate = XingDunPushRegistrationGate()
        assertNull(gate.begin(""))
        assertNull(gate.begin(" \t\n"))

        val active = checkNotNull(gate.begin("account-a"))
        assertNull(gate.begin(""))
        assertNull(gate.begin(" \t\n"))
        assertTrue(gate.isCurrent(active))
        assertTrue(gate.complete(active))
    }

    @Test
    fun concurrentRequestsForTheSameAccountCreateOnlyOneAttempt() {
        val gate = XingDunPushRegistrationGate()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val requests = (1..32).map {
                executor.submit(Callable {
                    start.await()
                    gate.begin("account-a")
                })
            }
            start.countDown()
            val accepted = requests.mapNotNull { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, accepted.size)
            assertTrue(gate.isCurrent(accepted.single()))
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrentSuccessCallbacksCompleteAnAttemptOnlyOnce() {
        val gate = XingDunPushRegistrationGate()
        val attempt = checkNotNull(gate.begin("account-a"))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val callbacks = (1..32).map {
                executor.submit(Callable {
                    start.await()
                    gate.complete(attempt)
                })
            }
            start.countDown()

            assertEquals(1, callbacks.count { it.get(5, TimeUnit.SECONDS) })
            assertTrue(gate.isCurrent(attempt))
            assertNull(gate.begin("account-a"))
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedMirrorRetriesWithTheSuccessfulSdkTicketWithoutRegisteringAgain() {
        val gate = XingDunPushRegistrationGate()
        val attempt = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(attempt))
        assertTrue(gate.beginMirror(attempt))

        assertTrue(gate.finishMirror(attempt, success = false))
        assertEquals(attempt, gate.registeredAttempt("account-a"))
        assertNull(gate.begin("account-a"))
        assertTrue(gate.beginMirror(attempt))
        assertTrue(gate.finishMirror(attempt, success = true))
    }

    @Test
    fun successfulMirrorSuppressesFurtherMirrorRequests() {
        val gate = XingDunPushRegistrationGate()
        val attempt = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(attempt))

        assertTrue(gate.beginMirror(attempt))
        assertFalse(gate.beginMirror(attempt))
        assertTrue(gate.finishMirror(attempt, success = true))
        assertFalse(gate.beginMirror(attempt))
        assertEquals(attempt, gate.registeredAttempt("account-a"))
        assertNull(gate.begin("account-a"))
    }

    @Test
    fun mirrorRequiresSuccessfulSdkRegistrationAndAnExplicitStart() {
        val gate = XingDunPushRegistrationGate()
        val failed = checkNotNull(gate.begin("account-a"))

        assertNull(gate.registeredAttempt("account-a"))
        assertFalse(gate.beginMirror(failed))
        assertFalse(gate.finishMirror(failed, success = true))
        assertTrue(gate.fail(failed))
        assertNull(gate.registeredAttempt("account-a"))
        assertFalse(gate.beginMirror(failed))

        val registered = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(registered))
        assertNull(gate.registeredAttempt("account-b"))
        assertFalse(gate.finishMirror(registered, success = true))
        assertFalse(gate.finishMirror(registered, success = false))
        assertTrue(gate.beginMirror(registered))
    }

    @Test
    fun previousAccountMirrorCannotFinishTheNewAccountMirror() {
        val gate = XingDunPushRegistrationGate()
        val previous = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(previous))
        assertTrue(gate.beginMirror(previous))

        val active = checkNotNull(gate.begin("account-b"))
        assertTrue(gate.complete(active))
        assertTrue(gate.beginMirror(active))
        assertNull(gate.registeredAttempt("account-a"))
        assertFalse(gate.finishMirror(previous, success = true))
        assertFalse(gate.finishMirror(previous, success = false))
        assertFalse(gate.beginMirror(active))
        assertTrue(gate.finishMirror(active, success = true))
        assertFalse(gate.beginMirror(active))
    }

    @Test
    fun logoutInvalidatesTheMirrorBeforeTheSameAccountRegistersAgain() {
        val gate = XingDunPushRegistrationGate()
        val beforeLogout = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(beforeLogout))
        assertTrue(gate.beginMirror(beforeLogout))

        gate.reset()
        assertNull(gate.registeredAttempt("account-a"))
        val afterLogin = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(afterLogin))
        assertTrue(gate.beginMirror(afterLogin))
        assertFalse(gate.finishMirror(beforeLogout, success = true))
        assertFalse(gate.finishMirror(beforeLogout, success = false))
        assertFalse(gate.beginMirror(afterLogin))
        assertTrue(gate.finishMirror(afterLogin, success = true))
    }

    @Test
    fun concurrentMirrorRequestsStartOnlyOneBusinessRequest() {
        val gate = XingDunPushRegistrationGate()
        val attempt = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(attempt))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val requests = (1..32).map {
                executor.submit(Callable {
                    start.await()
                    gate.beginMirror(attempt)
                })
            }
            start.countDown()

            assertEquals(1, requests.count { it.get(5, TimeUnit.SECONDS) })
            assertTrue(gate.finishMirror(attempt, success = true))
            assertFalse(gate.beginMirror(attempt))
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun duplicateMirrorCompletionCannotChangeTheFinishedResult() {
        val gate = XingDunPushRegistrationGate()
        val attempt = checkNotNull(gate.begin("account-a"))
        assertTrue(gate.complete(attempt))
        assertTrue(gate.beginMirror(attempt))

        assertTrue(gate.finishMirror(attempt, success = false))
        assertFalse(gate.finishMirror(attempt, success = true))
        assertFalse(gate.finishMirror(attempt, success = false))
        assertTrue(gate.beginMirror(attempt))
        assertTrue(gate.finishMirror(attempt, success = true))
        assertFalse(gate.finishMirror(attempt, success = false))
        assertFalse(gate.finishMirror(attempt, success = true))
        assertFalse(gate.beginMirror(attempt))
        assertEquals(attempt, gate.registeredAttempt("account-a"))
    }
}
