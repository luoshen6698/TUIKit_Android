package io.trtc.tuikit.chat.demo.xingdun.call

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

class XingDunCallInitializationGateTest {
    @Test
    fun sameIdentityJoinsInitializationAndReusesTheReadyEngine() {
        val gate = XingDunCallInitializationGate()
        val first = checkNotNull(gate.begin("tenant-a:user-a"))
        val joined = checkNotNull(gate.begin("tenant-a:user-a"))

        assertEquals(XingDunCallInitializationGate.Status.START, first.status)
        assertEquals(XingDunCallInitializationGate.Status.JOIN, joined.status)
        assertEquals(first.attempt, joined.attempt)
        assertFalse(gate.isReady(first.attempt))
        assertTrue(gate.complete(first.attempt))
        assertTrue(gate.isReady(first.attempt))
        val ready = checkNotNull(gate.begin("tenant-a:user-a"))
        assertEquals(XingDunCallInitializationGate.Status.READY, ready.status)
        assertEquals(first.attempt, ready.attempt)
    }

    @Test
    fun initializationFailureAllowsRetryWithANewGeneration() {
        val gate = XingDunCallInitializationGate()
        val failed = checkNotNull(gate.begin("tenant-a:user-a")).attempt

        assertTrue(gate.fail(failed))
        val retry = checkNotNull(gate.begin("tenant-a:user-a"))
        assertEquals(XingDunCallInitializationGate.Status.START, retry.status)
        assertNotEquals(failed.generation, retry.attempt.generation)
        assertFalse(gate.complete(failed))
        assertFalse(gate.fail(failed))
        assertTrue(gate.isCurrent(retry.attempt))
    }

    @Test
    fun switchingIdentityRejectsBothOldSuccessAndOldFailure() {
        val gate = XingDunCallInitializationGate()
        val previous = checkNotNull(gate.begin("tenant-a:user-a")).attempt
        val active = checkNotNull(gate.begin("tenant-a:user-b")).attempt

        assertFalse(gate.complete(previous))
        assertFalse(gate.fail(previous))
        assertFalse(gate.isCurrent(previous))
        assertTrue(gate.isCurrent(active))
        assertTrue(gate.complete(active))
    }

    @Test
    fun changingTenantCannotReuseThePreviousReadyEngine() {
        val gate = XingDunCallInitializationGate()
        val previous = checkNotNull(gate.begin("tenant-a:user-a")).attempt
        assertTrue(gate.complete(previous))

        val active = checkNotNull(gate.begin("tenant-b:user-a"))
        assertEquals(XingDunCallInitializationGate.Status.START, active.status)
        assertFalse(gate.isReady(previous))
        assertFalse(gate.isReady(active.attempt))
    }

    @Test
    fun resetInvalidatesPendingCallbacksBeforeTheSameIdentityReturns() {
        val gate = XingDunCallInitializationGate()
        val previous = checkNotNull(gate.begin("tenant-a:user-a")).attempt

        gate.reset()
        val active = checkNotNull(gate.begin("tenant-a:user-a"))
        assertEquals(XingDunCallInitializationGate.Status.START, active.status)
        assertNotEquals(previous.generation, active.attempt.generation)
        assertFalse(gate.complete(previous))
        assertFalse(gate.fail(previous))
        assertTrue(gate.complete(active.attempt))
    }

    @Test
    fun resetInvalidatesReadinessAndAllowsACompleteNewInitialization() {
        val gate = XingDunCallInitializationGate()
        val previous = checkNotNull(gate.begin("tenant-a:user-a")).attempt
        assertTrue(gate.complete(previous))

        gate.reset()
        assertFalse(gate.isReady(previous))
        assertEquals(XingDunCallInitializationGate.Status.START, gate.begin("tenant-a:user-a")?.status)
    }

    @Test
    fun duplicateCallbacksCannotUndoAReadyEngine() {
        val gate = XingDunCallInitializationGate()
        val attempt = checkNotNull(gate.begin("tenant-a:user-a")).attempt

        assertTrue(gate.complete(attempt))
        assertFalse(gate.complete(attempt))
        assertFalse(gate.fail(attempt))
        assertTrue(gate.isReady(attempt))
    }

    @Test
    fun blankIdentityCannotReplaceTheActiveInitialization() {
        val gate = XingDunCallInitializationGate()
        assertNull(gate.begin(""))
        val active = checkNotNull(gate.begin("tenant-a:user-a")).attempt

        assertNull(gate.begin(" \t\n"))
        assertTrue(gate.isCurrent(active))
    }

    @Test
    fun concurrentRequestsStartOneInitializationAndJoinTheSameAttempt() {
        val gate = XingDunCallInitializationGate()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val calls = (1..32).map {
                executor.submit(Callable {
                    start.await()
                    checkNotNull(gate.begin("tenant-a:user-a"))
                })
            }
            start.countDown()
            val admissions = calls.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, admissions.count { it.status == XingDunCallInitializationGate.Status.START })
            assertEquals(31, admissions.count { it.status == XingDunCallInitializationGate.Status.JOIN })
            assertEquals(1, admissions.map { it.attempt }.distinct().size)
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
