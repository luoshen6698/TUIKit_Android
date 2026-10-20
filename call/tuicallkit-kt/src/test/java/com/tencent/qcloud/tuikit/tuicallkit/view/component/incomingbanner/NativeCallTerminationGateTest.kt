package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCallTerminationGateTest {
    private val call = NativeCallIdentity("call-a", 1_000, "callee-a")

    @Test
    fun aCancelledCallCannotReturnFromADelayedWaitingSnapshot() {
        val gate = NativeCallTerminationGate()
        assertTrue(gate.canPresent(call))

        assertTrue(gate.recordEnded(call))

        assertTrue(gate.shouldClear(call, call))
        repeat(3) { assertFalse(gate.canPresent(call.copy())) }
    }

    @Test
    fun aTerminalEventBeforeTheFirstPresentationStillBlocksTheCall() {
        val gate = NativeCallTerminationGate()

        gate.recordEnded(call)

        assertFalse(gate.canPresent(call))
        assertTrue(gate.shouldClear(call, call))
    }

    @Test
    fun repeatedTerminalEventsAreIdempotent() {
        val gate = NativeCallTerminationGate()

        repeat(100) { assertTrue(gate.recordEnded(call.copy())) }

        assertFalse(gate.canPresent(call))
        assertTrue(gate.shouldClear(call, call))
        assertTrue(gate.canPresent(call.copy(callId = "call-b")))
    }

    @Test
    fun anOldCallEndingCannotClearANewerIncomingCall() {
        val gate = NativeCallTerminationGate()
        val next = call.copy(callId = "call-b")

        gate.recordEnded(call)

        assertFalse(gate.shouldClear(call, next))
        assertTrue(gate.canPresent(next))
        assertFalse(gate.canPresent(call))
    }

    @Test
    fun terminalIdentityIsScopedToTheSdkApplicationAndAccount() {
        val gate = NativeCallTerminationGate()
        gate.recordEnded(call)

        listOf(call.copy(sdkAppId = 2_000), call.copy(selfId = "callee-b")).forEach { other ->
            assertTrue(gate.canPresent(other))
            assertFalse(gate.shouldClear(call, other))
            assertFalse(gate.shouldClear(other, call))
        }
        assertFalse(gate.canPresent(call))
    }

    @Test
    fun aValidCallWithoutATerminalEventCannotBeCleared() {
        val gate = NativeCallTerminationGate()

        assertFalse(gate.shouldClear(call, call))
        assertFalse(gate.shouldClear(call, null))
        assertTrue(gate.canPresent(call))
    }

    @Test
    fun invalidIdentitiesNeverPresentOrConsumeTerminalCapacity() {
        val invalidIdentities = listOf(
            call.copy(callId = ""), call.copy(callId = " \t"),
            call.copy(sdkAppId = 0), call.copy(sdkAppId = -1),
            call.copy(selfId = ""), call.copy(selfId = "\n"),
        )
        val gate = NativeCallTerminationGate()
        gate.recordEnded(call)

        repeat(33) {
            invalidIdentities.forEach { invalid ->
                assertFalse(gate.recordEnded(invalid))
                assertFalse(gate.canPresent(invalid))
                assertFalse(gate.shouldClear(invalid, invalid))
                assertFalse(gate.shouldClear(invalid, call))
            }
        }

        assertFalse(gate.canPresent(call))
    }

    @Test
    fun allThirtyTwoRecentTerminalCallsRemainBlocked() {
        val gate = NativeCallTerminationGate()
        val calls = (1..32).map { call.copy(callId = "call-$it") }

        calls.forEach { gate.recordEnded(it) }

        calls.forEach { assertFalse(gate.canPresent(it)) }
        assertTrue(gate.canPresent(call.copy(callId = "call-new")))
    }

    @Test
    fun theThirtyThirdUniqueTerminalCallEvictsOnlyTheOldest() {
        val gate = NativeCallTerminationGate()
        val calls = (1..33).map { call.copy(callId = "call-$it") }

        calls.forEach { gate.recordEnded(it) }

        assertTrue(gate.canPresent(calls.first()))
        assertFalse(gate.shouldClear(calls.first(), calls.first()))
        calls.drop(1).forEach { assertFalse(gate.canPresent(it)) }
    }

    @Test
    fun duplicateOldEventsDoNotEvictTheCurrentTerminalCall() {
        val gate = NativeCallTerminationGate()
        val calls = (1..32).map { call.copy(callId = "call-$it") }
        calls.forEach { gate.recordEnded(it) }

        repeat(100) { gate.recordEnded(calls.first()) }

        calls.forEach { assertFalse(gate.canPresent(it)) }
        assertTrue(gate.shouldClear(calls.last(), calls.last()))
        val next = call.copy(callId = "call-33")
        gate.recordEnded(next)
        assertTrue(gate.canPresent(calls.first()))
        assertFalse(gate.canPresent(calls.last()))
        assertFalse(gate.canPresent(next))
    }

    @Test fun anExplicitSuccessfulJoinReentersOnlyThatGroupCall() {
        val gate = NativeCallTerminationGate()
        val other = call.copy(callId = "other")
        gate.recordEnded(call)
        gate.recordEnded(other)

        gate.allowJoinedCall(call)

        assertTrue(gate.canPresent(call))
        assertFalse(gate.shouldClear(call, call))
        assertFalse(gate.canPresent(other))
    }

    @Test fun joiningAnotherAccountCannotResetTheEndedCall() {
        val gate = NativeCallTerminationGate()
        gate.recordEnded(call)

        gate.allowJoinedCall(call.copy(selfId = "other-account"))

        assertFalse(gate.canPresent(call))
    }
}
