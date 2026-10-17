package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCallNotificationPolicyTest {
    private val identity = NativeCallIdentity("first", 123, "self")

    @Test fun pendingOrPublishedSystemNotificationNeverDoubleRings() {
        listOf(null, NativeCallNotificationPolicy.IncomingDelivery.Pending,
            NativeCallNotificationPolicy.IncomingDelivery.Published).forEach { delivery ->
            val presentation = delivery?.let { NativeCallNotificationPolicy.IncomingPresentation(identity, it) }
            assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, presentation,
                true, true, true, false))
        }
    }

    @Test fun aFailedCurrentNotificationEnablesForegroundRinging() {
        val failed = NativeCallNotificationPolicy.IncomingPresentation(identity,
            NativeCallNotificationPolicy.IncomingDelivery.Failed)
        assertTrue(NativeCallNotificationPolicy.needsLocalRinging(identity, failed, true, true, true, false))
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, failed, true, false, true, false))
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, failed, true, true, false, false))
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, failed, true, true, true, true))
    }

    @Test fun aFailureFromAnOldCallCannotRingTheNextCall() {
        val old = NativeCallNotificationPolicy.IncomingPresentation(
            NativeCallIdentity("old", 123, "self"), NativeCallNotificationPolicy.IncomingDelivery.Failed)
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, old, true, true, true, false))
    }

    @Test fun blockedNotificationsRetainTheExistingForegroundFallback() {
        assertTrue(NativeCallNotificationPolicy.needsLocalRinging(identity, null, false, true, true, false))
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, null, false, false, true, false))
    }

    @Test fun oemCompatibleNotificationsUseOneLocalForegroundRingtoneEvenAfterPublishing() {
        val published = NativeCallNotificationPolicy.IncomingPresentation(identity,
            NativeCallNotificationPolicy.IncomingDelivery.Published)
        assertTrue(NativeCallNotificationPolicy.needsLocalRinging(identity, published,
            true, true, true, false, preferLocalAlert = true))
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, published,
            true, false, true, false, preferLocalAlert = true))
        assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, published,
            true, true, false, false, preferLocalAlert = true))
    }

    @Test fun expiredIncomingCallsStopEvenTheBlockedNotificationFallback() {
        val expired = NativeCallNotificationPolicy.IncomingPresentation(identity,
            NativeCallNotificationPolicy.IncomingDelivery.Expired)
        listOf(true, false).forEach { allowed ->
            assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, expired,
                allowed, true, true, false))
            assertFalse(NativeCallNotificationPolicy.needsLocalRinging(identity, expired,
                allowed, true, true, false, preferLocalAlert = true))
        }
    }

    @Test fun failureDiagnosticsNeverReturnArbitraryExceptionContents() {
        assertEquals("call_style_requires_service_or_fsi", NativeCallNotificationPolicy.failureReason(
            "CallStyle notifications must either be for a foreground Service or use a fullScreenIntent."))
        assertEquals("missing_small_icon", NativeCallNotificationPolicy.failureReason("Invalid notification (no valid small icon)"))
        assertEquals("missing_caller_name", NativeCallNotificationPolicy.failureReason("Person must have a non-empty a name"))
        assertEquals("missing_caller_name", NativeCallNotificationPolicy.failureReason("Person must have a non-empty name"))
        assertEquals("other", NativeCallNotificationPolicy.failureReason("private token or username"))
        assertEquals("unspecified", NativeCallNotificationPolicy.failureReason(null))
    }

    @Test fun onlyTheIncomingOemCallStyleRejectionRetriesWithCompatibleActions() {
        assertTrue(NativeCallNotificationPolicy.shouldUseCompatibleIncoming(NativeCallPhase.Waiting,
            true, "call_style_requires_service_or_fsi"))
        assertFalse(NativeCallNotificationPolicy.shouldUseCompatibleIncoming(NativeCallPhase.Accepted,
            true, "call_style_requires_service_or_fsi"))
        assertFalse(NativeCallNotificationPolicy.shouldUseCompatibleIncoming(NativeCallPhase.Waiting,
            false, "call_style_requires_service_or_fsi"))
        assertFalse(NativeCallNotificationPolicy.shouldUseCompatibleIncoming(NativeCallPhase.Waiting,
            true, "missing_small_icon"))
    }

    @Test
    fun anIncomingCallHasAFullScreenIntentWithoutRequiringAService() {
        val plan = NativeCallNotificationPolicy.plan(NativeCallPhase.Waiting, serviceActive = false)

        assertEquals(NativeCallNotificationPolicy.Delivery.IncomingNotification, plan.delivery)
        assertTrue(plan.fullScreenIntent)
        assertFalse(plan.onlyAlertOnce)
    }

    @Test
    fun repeatedIncomingUpdatesKeepTheirFullScreenIntentAndDoNotSilenceTheLoop() {
        listOf(false, false, true, true).forEach { serviceActive ->
            val plan = NativeCallNotificationPolicy.plan(NativeCallPhase.Waiting, serviceActive)

            assertEquals(NativeCallNotificationPolicy.Delivery.IncomingNotification, plan.delivery)
            assertTrue(plan.fullScreenIntent)
            assertFalse(plan.onlyAlertOnce)
        }
    }

    @Test
    fun acceptingBeforeTheServiceStartsDefersInsteadOfPostingAStandaloneCallStyle() {
        val plan = NativeCallNotificationPolicy.plan(NativeCallPhase.Accepted, serviceActive = false)

        assertEquals(NativeCallNotificationPolicy.Delivery.Defer, plan.delivery)
        assertFalse(plan.fullScreenIntent)
        assertTrue(plan.onlyAlertOnce)
    }

    @Test
    fun anAcceptedCallUsesItsActiveServiceWithoutAlertingAgain() {
        val plan = NativeCallNotificationPolicy.plan(NativeCallPhase.Accepted, serviceActive = true)

        assertEquals(NativeCallNotificationPolicy.Delivery.OngoingForegroundService, plan.delivery)
        assertFalse(plan.fullScreenIntent)
        assertTrue(plan.onlyAlertOnce)
    }

    @Test
    fun havingNoCallNeverPublishesANotificationEvenIfTheServiceIsStillActive() {
        listOf(false, true).forEach { serviceActive ->
            val plan = NativeCallNotificationPolicy.plan(NativeCallPhase.None, serviceActive)

            assertEquals(NativeCallNotificationPolicy.Delivery.Defer, plan.delivery)
            assertFalse(plan.fullScreenIntent)
            assertTrue(plan.onlyAlertOnce)
        }
    }
}
