package io.trtc.tuikit.chat.demo.xingdun.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunCallHistoryNotificationPolicyTest {
    private val incoming = XingDunCallHistoryNotificationPolicy.Call("scope-a", "call-1", "caller", "Caller", false, false)

    @Test fun acceptedVoiceAndVideoCallsBecomeEndedRecords() {
        listOf(false, true).forEach { video ->
            val policy = XingDunCallHistoryNotificationPolicy()
            policy.observe(incoming.copy(video = video), "self", true)
            policy.observe(incoming.copy(video = video, accepted = true), "self", true)
            // A delayed waiting/profile event cannot undo acceptance.
            policy.observe(incoming.copy(video = video), "self", true)
            val record = policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP)
            assertEquals(XingDunCallHistoryNotificationPolicy.Result.ENDED, record?.result)
            assertEquals(video, record?.call?.video)
        }
    }

    @Test fun unansweredReasonsRetainTheirActualOutcome() {
        val outcomes = mapOf(
            XingDunCallHistoryNotificationPolicy.EndReason.REJECT to XingDunCallHistoryNotificationPolicy.Result.REJECTED,
            XingDunCallHistoryNotificationPolicy.EndReason.NO_RESPONSE to XingDunCallHistoryNotificationPolicy.Result.MISSED,
            XingDunCallHistoryNotificationPolicy.EndReason.CANCELED to XingDunCallHistoryNotificationPolicy.Result.CANCELED,
            XingDunCallHistoryNotificationPolicy.EndReason.LINE_BUSY to XingDunCallHistoryNotificationPolicy.Result.MISSED,
        )
        outcomes.forEach { (reason, result) ->
            val policy = XingDunCallHistoryNotificationPolicy()
            policy.observe(incoming, "self", true)
            assertEquals(result, policy.finish("scope-a", "call-1", reason)?.result)
        }
    }

    @Test fun outgoingGroupAndUnknownCallsNeverCreateRecords() {
        val policy = XingDunCallHistoryNotificationPolicy()
        policy.observe(incoming.copy(callerId = "self"), "self", true)
        assertNull(policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
        policy.observe(incoming, "self", false)
        assertNull(policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
        assertNull(policy.finish("scope-a", "unknown", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
    }

    @Test fun repeatedTerminalAndLateProfileCallbacksDoNotRestoreDismissedRecords() {
        val policy = XingDunCallHistoryNotificationPolicy()
        policy.observe(incoming, "self", true)
        policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.NO_RESPONSE)
        policy.observe(incoming.copy(callerName = "Updated"), "self", true)
        assertNull(policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.NO_RESPONSE))
    }

    @Test fun logoutAccountChangeAndLateOldAccountEventsCannotPublish() {
        val policy = XingDunCallHistoryNotificationPolicy()
        policy.observe(incoming, "self", true)
        assertNull(policy.finish(null, "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
        assertNull(policy.finish("scope-b", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
        policy.clear()
        assertNull(policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
        policy.observe(incoming, "self", true)
        policy.observe(incoming.copy(accountKey = "scope-b", callId = "call-2"), "self", true)
        assertNull(policy.finish("scope-a", "call-1", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
        assertEquals(XingDunCallHistoryNotificationPolicy.Result.ENDED,
            policy.finish("scope-b", "call-2", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP)?.result)
    }

    @Test fun newCallsPreserveCachedNamesAndEvictUnboundedPendingCalls() {
        val policy = XingDunCallHistoryNotificationPolicy()
        policy.observe(incoming, "self", true)
        policy.observe(incoming.copy(callerName = ""), "self", true)
        assertEquals("Caller", policy.finish("scope-a", "call-1",
            XingDunCallHistoryNotificationPolicy.EndReason.HANGUP)?.call?.callerName)
        repeat(9) { policy.observe(incoming.copy(callId = "new-$it"), "self", true) }
        assertNull(policy.finish("scope-a", "new-0", XingDunCallHistoryNotificationPolicy.EndReason.HANGUP))
    }
}
