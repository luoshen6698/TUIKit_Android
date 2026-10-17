package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEndActionPolicyTest {
    private val call = NativeCallIdentity("call-a", 1_000, "self-a")

    @Test
    fun repeatedClicksCannotDispatchAnotherEndRequest() {
        val policy = attachedPolicy()
        assertNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertNull(policy.begin(call, attached = true, actionAllowed = true))
    }

    @Test
    fun failureOrTimeoutReleasesOnlyTheSameActiveCallForRetry() {
        val policy = attachedPolicy()
        val attempt = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertTrue(policy.release(attempt, call, attached = true, actionAllowed = true))
        assertNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertFalse(policy.release(attempt, call, attached = true, actionAllowed = true))
    }

    @Test
    fun endedCallCannotRestoreControlsOrSubmitAnotherEndRequest() {
        val policy = attachedPolicy()
        val attempt = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertFalse(policy.release(attempt, call, attached = true, actionAllowed = false))
        assertNull(policy.begin(call, attached = true, actionAllowed = false))
    }

    @Test
    fun differentCallOrAccountCannotReceiveThePreviousCallsRecovery() {
        listOf(call.copy(callId = "call-b"), call.copy(sdkAppId = 2_000), call.copy(selfId = "self-b"))
            .forEach { replacement ->
                val policy = attachedPolicy()
                val attempt = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
                assertFalse(policy.release(attempt, replacement, attached = true, actionAllowed = true))
                assertNull(policy.begin(replacement, attached = true, actionAllowed = true))
            }
    }

    @Test
    fun detachedViewsDoNotDispatchOrRestoreAnAction() {
        val policy = attachedPolicy()
        assertNull(policy.begin(call, attached = false, actionAllowed = true))
        val attempt = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertFalse(policy.release(attempt, call, attached = false, actionAllowed = true))
    }

    @Test
    fun reattachmentInvalidatesOldTimeoutAndCompletionEvenForTheSameCall() {
        val policy = attachedPolicy()
        val previous = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
        policy.reset()
        policy.attach(call)
        val next = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertFalse(policy.isPending(previous))
        assertFalse(policy.release(previous, call, attached = true, actionAllowed = true))
        assertTrue(policy.isPending(next))
    }

    @Test
    fun incompleteIdentityCannotSubmitAnEndRequest() {
        listOf(call.copy(callId = ""), call.copy(sdkAppId = 0), call.copy(selfId = " "))
            .forEach { incomplete ->
                val policy = CallEndActionPolicy()
                policy.attach(incomplete)
                assertNull(policy.begin(incomplete, attached = true, actionAllowed = true))
            }
    }

    @Test
    fun lateOutgoingIdentityCanBindOnceButNotBeReplacedByTheNextCall() {
        val policy = CallEndActionPolicy()
        policy.attach(call.copy(callId = ""))
        val attempt = checkNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertTrue(policy.release(attempt, call, attached = true, actionAllowed = true))
        assertNull(policy.begin(call.copy(callId = "call-b"), attached = true, actionAllowed = true))
    }

    @Test
    fun ignoredActionsExposeOnlyFixedReasonsAndKeepTheIdentityGateStrict() {
        val policy = attachedPolicy()
        assertEquals("not_attached", policy.blockedReason(call, attached = false, actionAllowed = true))
        assertEquals("invalid_identity", policy.blockedReason(call.copy(callId = ""), true, true))
        assertEquals("not_allowed", policy.blockedReason(call, attached = true, actionAllowed = false))
        val next = call.copy(callId = "call-b")
        assertEquals("identity_changed", policy.blockedReason(next, attached = true, actionAllowed = true))
        assertNull(policy.begin(next, attached = true, actionAllowed = true))
        assertNotNull(policy.begin(call, attached = true, actionAllowed = true))
        assertEquals("pending", policy.blockedReason(call, attached = true, actionAllowed = true))
    }

    @Test
    fun assignedSdkIdentityIsNeverReplacedByTheLocalToken() {
        assertEquals(call, localIdentity(call, NativeCallPhase.Waiting, isCaller = true))
        assertEquals(call, localIdentity(call, NativeCallPhase.Accepted, isCaller = false))
    }

    @Test
    fun authenticatedOutgoingWaitingCallCanCancelBeforeSdkAssignsItsId() {
        val sdkIdentity = call.copy(callId = "")
        val local = localIdentity(sdkIdentity, NativeCallPhase.Waiting, isCaller = true)
        val policy = CallEndActionPolicy().apply { attach(local) }
        assertEquals("", sdkIdentity.callId)
        assertEquals("local-caller-view:view-a", local.callId)
        assertNotNull(policy.begin(local, attached = true, actionAllowed = true))
    }

    @Test
    fun unassignedCalleeAcceptedOrIdleCallsCannotUseTheCallerFallback() {
        listOf(
            NativeCallPhase.Waiting to false,
            NativeCallPhase.Accepted to true,
            NativeCallPhase.Accepted to false,
            NativeCallPhase.None to true,
        ).forEach { (phase, caller) ->
            val identity = localIdentity(call.copy(callId = ""), phase, caller)
            val policy = CallEndActionPolicy().apply { attach(identity) }
            assertEquals("", identity.callId)
            assertNull(policy.begin(identity, attached = true, actionAllowed = true))
        }
    }

    @Test
    fun missingAuthenticationInviteesOrViewTokenCannotCreateALocalIdentity() {
        listOf(call.copy(callId = "", sdkAppId = 0), call.copy(callId = "", selfId = " "))
            .forEach { invalid ->
                assertEquals(invalid, localIdentity(invalid, NativeCallPhase.Waiting, isCaller = true))
            }
        val unassigned = call.copy(callId = "")
        assertEquals(unassigned, CallEndActionPolicy.identityForLocalAction(
            unassigned, NativeCallPhase.Waiting, true, false, "view-a"))
        assertEquals(unassigned, CallEndActionPolicy.identityForLocalAction(
            unassigned, NativeCallPhase.Waiting, true, true, " "))
    }

    @Test
    fun separateViewsCannotReuseOrCompleteEachOthersLocalEndActions() {
        val unassigned = call.copy(callId = "")
        val first = localIdentity(unassigned, NativeCallPhase.Waiting, true, "view-a")
        val next = localIdentity(unassigned, NativeCallPhase.Waiting, true, "view-b")
        val oldPolicy = CallEndActionPolicy().apply { attach(first) }
        val oldAction = checkNotNull(oldPolicy.begin(first, attached = true, actionAllowed = true))
        assertFalse(oldPolicy.release(oldAction, next, attached = true, actionAllowed = true))
        assertNull(oldPolicy.begin(next, attached = true, actionAllowed = true))
        val newPolicy = CallEndActionPolicy().apply { attach(next) }
        val newAction = checkNotNull(newPolicy.begin(next, attached = true, actionAllowed = true))
        assertFalse(newPolicy.release(oldAction, next, attached = true, actionAllowed = true))
        assertTrue(newPolicy.isPending(newAction))
    }

    @Test
    fun assignedSdkIdRequiresNewControlsAndCannotReceiveTheUnassignedCallsCompletion() {
        val local = localIdentity(call.copy(callId = ""), NativeCallPhase.Waiting, true)
        val oldPolicy = CallEndActionPolicy().apply { attach(local) }
        val oldAction = checkNotNull(oldPolicy.begin(local, attached = true, actionAllowed = true))
        assertNull(oldPolicy.begin(call, attached = true, actionAllowed = true))
        oldPolicy.reset() // A real SDK ID changes the render key and detaches the old helper.
        val nextPolicy = attachedPolicy()
        val nextAction = checkNotNull(nextPolicy.begin(call, attached = true, actionAllowed = true))
        assertFalse(nextPolicy.release(oldAction, call, attached = true, actionAllowed = true))
        assertTrue(nextPolicy.isPending(nextAction))
    }

    private fun localIdentity(
        identity: NativeCallIdentity, phase: NativeCallPhase, isCaller: Boolean, viewToken: String = "view-a",
    ) = CallEndActionPolicy.identityForLocalAction(identity, phase, isCaller, true, viewToken)

    private fun attachedPolicy() = CallEndActionPolicy().apply { attach(call) }
}
