package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCallActionGateTest {
    private val call = NativeCallIdentity("call-a", 1_000, "callee-a")

    @Test
    fun anUnrestoredCallCannotBeOpenedOrControlled() {
        val gate = NativeCallActionGate()

        assertFalse(gate.canOpen(call))
        NativeCallAction.values().forEach { assertNull(gate.begin(call, it)) }
    }

    @Test
    fun invalidCallOrAccountIdentityClearsThePreviousCall() {
        val invalidIdentities = listOf(
            call.copy(callId = ""),
            call.copy(callId = " \t"),
            call.copy(sdkAppId = 0),
            call.copy(sdkAppId = -1),
            call.copy(selfId = ""),
            call.copy(selfId = "\n"),
        )
        invalidIdentities.forEach { invalid ->
            val gate = waitingGate()
            val previous = checkNotNull(gate.begin(call, NativeCallAction.Accept))

            gate.update(invalid, NativeCallPhase.Waiting)

            assertFalse(gate.canOpen(call))
            assertFalse(gate.canOpen(invalid))
            NativeCallAction.values().forEach { assertNull(gate.begin(invalid, it)) }
            assertFalse(gate.finish(previous, true))
        }
    }

    @Test
    fun aMissingIdentityCannotRestoreAWaitingOrAcceptedCall() {
        listOf(NativeCallPhase.Waiting, NativeCallPhase.Accepted).forEach { phase ->
            val gate = waitingGate()
            val previous = checkNotNull(gate.begin(call, NativeCallAction.Accept))

            gate.update(null, phase)

            assertFalse(gate.canOpen(call))
            assertNull(gate.begin(call, NativeCallAction.Accept))
            assertFalse(gate.finish(previous, true))
        }
    }

    @Test
    fun anActionForAnotherCallOrAccountDoesNotControlTheCurrentCall() {
        val gate = waitingGate()
        val otherIdentities = listOf(
            call.copy(callId = "call-b"),
            call.copy(sdkAppId = 2_000),
            call.copy(selfId = "callee-b"),
        )

        otherIdentities.forEach { other ->
            assertFalse(gate.canOpen(other))
            NativeCallAction.values().forEach { assertNull(gate.begin(other, it)) }
        }
        assertTrue(gate.canOpen(call))
        assertTrue(gate.begin(call, NativeCallAction.Accept) != null)
    }

    @Test
    fun aWaitingCallAllowsAcceptOrRejectButNotHangup() {
        listOf(NativeCallAction.Accept, NativeCallAction.Reject).forEach { action ->
            val gate = waitingGate()

            assertTrue(gate.canOpen(call))
            assertNull(gate.begin(call, NativeCallAction.Hangup))
            assertTrue(gate.begin(call, action) != null)
        }
    }

    @Test
    fun anAcceptedCallAllowsHangupButNotAcceptOrReject() {
        val gate = NativeCallActionGate()
        gate.update(call, NativeCallPhase.Accepted)

        assertTrue(gate.canOpen(call))
        assertNull(gate.begin(call, NativeCallAction.Accept))
        assertNull(gate.begin(call, NativeCallAction.Reject))
        assertTrue(gate.begin(call, NativeCallAction.Hangup) != null)
    }

    @Test
    fun duplicateCallUpdatesPreserveAnInFlightAndSuccessfulAction() {
        val gate = waitingGate()
        val attempt = checkNotNull(gate.begin(call, NativeCallAction.Accept))

        gate.update(call.copy(), NativeCallPhase.Waiting)
        assertNull(gate.begin(call, NativeCallAction.Accept))
        assertNull(gate.begin(call, NativeCallAction.Reject))
        assertTrue(gate.finish(attempt, true))

        gate.update(call.copy(), NativeCallPhase.Waiting)
        assertNull(gate.begin(call, NativeCallAction.Accept))
    }

    @Test
    fun replacingTheCallInvalidatesItsPendingAction() {
        val gate = waitingGate()
        val previous = checkNotNull(gate.begin(call, NativeCallAction.Reject))
        val replacement = call.copy(callId = "call-b")

        gate.update(replacement, NativeCallPhase.Waiting)
        val active = checkNotNull(gate.begin(replacement, NativeCallAction.Accept))

        assertFalse(gate.canOpen(call))
        assertTrue(gate.canOpen(replacement))
        assertFalse(gate.finish(previous, true))
        assertFalse(gate.finish(previous, false))
        assertTrue(gate.finish(active, true))
    }

    @Test
    fun reusingACallIdAcrossAccountsCannotReuseAnOldAction() {
        listOf(call.copy(sdkAppId = 2_000), call.copy(selfId = "callee-b")).forEach { replacement ->
            val gate = waitingGate()
            val previous = checkNotNull(gate.begin(call, NativeCallAction.Accept))

            gate.update(replacement, NativeCallPhase.Waiting)
            val active = checkNotNull(gate.begin(replacement, NativeCallAction.Accept))

            assertFalse(gate.finish(previous, true))
            assertTrue(gate.finish(active, true))
        }
    }

    @Test
    fun acceptingTheCallInvalidatesItsWaitingActionsAndAllowsHangup() {
        val gate = waitingGate()
        val waiting = checkNotNull(gate.begin(call, NativeCallAction.Accept))

        gate.update(call, NativeCallPhase.Accepted)
        val hangup = checkNotNull(gate.begin(call, NativeCallAction.Hangup))

        assertFalse(gate.finish(waiting, true))
        assertFalse(gate.finish(waiting, false))
        assertTrue(gate.finish(hangup, true))
        gate.update(call, NativeCallPhase.Accepted)
        assertNull(gate.begin(call, NativeCallAction.Hangup))
    }

    @Test
    fun returningToTheSamePhaseCannotReviveAnOldAttempt() {
        val gate = waitingGate()
        val previous = checkNotNull(gate.begin(call, NativeCallAction.Accept))

        gate.update(call, NativeCallPhase.Accepted)
        gate.update(call, NativeCallPhase.Waiting)
        val active = checkNotNull(gate.begin(call, NativeCallAction.Accept))

        assertTrue(active.generation > previous.generation)
        assertFalse(gate.finish(previous, true))
        assertTrue(gate.finish(active, true))
    }

    @Test
    fun aFailedActionCanRetryWithoutAcceptingTheOldCompletion() {
        val gate = waitingGate()
        val failed = checkNotNull(gate.begin(call, NativeCallAction.Reject))
        assertTrue(gate.finish(failed, false))
        val retry = checkNotNull(gate.begin(call, NativeCallAction.Reject))

        assertTrue(retry.generation > failed.generation)
        assertFalse(gate.finish(failed, true))
        assertFalse(gate.finish(failed, false))
        assertNull(gate.begin(call, NativeCallAction.Accept))
        assertTrue(gate.finish(retry, true))
        assertNull(gate.begin(call, NativeCallAction.Reject))
    }

    @Test
    fun repeatedCompletionCannotUndoSuccessfulDeduplication() {
        val gate = waitingGate()
        val attempt = checkNotNull(gate.begin(call, NativeCallAction.Accept))

        assertTrue(gate.finish(attempt, true))
        assertFalse(gate.finish(attempt, false))
        assertFalse(gate.finish(attempt, true))
        assertNull(gate.begin(call, NativeCallAction.Accept))
    }

    @Test
    fun endingACallClearsItsActionsBeforeTheSameIdentityReturns() {
        val gate = waitingGate()
        val previous = checkNotNull(gate.begin(call, NativeCallAction.Accept))
        assertTrue(gate.finish(previous, true))

        gate.update(call, NativeCallPhase.None)
        assertFalse(gate.canOpen(call))
        assertNull(gate.begin(call, NativeCallAction.Accept))

        gate.update(call, NativeCallPhase.Waiting)
        val active = checkNotNull(gate.begin(call, NativeCallAction.Accept))
        assertTrue(active.generation > previous.generation)
        assertFalse(gate.finish(previous, false))
        assertTrue(gate.finish(active, true))
    }

    @Test
    fun resetInvalidatesPendingAndSuccessfulActionsForTheSameIdentity() {
        val gate = waitingGate()
        val previous = checkNotNull(gate.begin(call, NativeCallAction.Accept))

        gate.reset()
        assertFalse(gate.canOpen(call))
        assertNull(gate.begin(call, NativeCallAction.Accept))
        gate.update(call, NativeCallPhase.Waiting)
        val active = checkNotNull(gate.begin(call, NativeCallAction.Accept))
        assertFalse(gate.finish(previous, true))
        assertTrue(gate.finish(active, true))

        gate.reset()
        gate.update(call, NativeCallPhase.Waiting)
        assertTrue(gate.begin(call, NativeCallAction.Accept) != null)
    }

    @Test
    fun concurrentAcceptAndRejectRequestsAdmitOneActionForTheCall() {
        val gate = waitingGate()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val calls = (0 until 32).map { index ->
                executor.submit(Callable {
                    start.await()
                    val action = if (index % 2 == 0) NativeCallAction.Accept else NativeCallAction.Reject
                    gate.begin(call, action)
                })
            }
            start.countDown()
            val attempts = calls.mapNotNull { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, attempts.size)
            assertTrue(gate.canOpen(call))
            assertTrue(gate.finish(attempts.single(), true))
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    private fun waitingGate() = NativeCallActionGate().apply {
        update(call, NativeCallPhase.Waiting)
    }
}
