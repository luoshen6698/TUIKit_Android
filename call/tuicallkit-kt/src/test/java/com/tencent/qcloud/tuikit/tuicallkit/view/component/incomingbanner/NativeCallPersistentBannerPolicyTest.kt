package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCallPersistentBannerPolicyTest {
    @Test fun waitingOnAnUnlockedDesktopUsesTheGrantedOverlay() {
        assertTrue(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, false, false, true, true, false, false))
    }

    @Test fun openingTheAppUsesTheExistingForegroundAnswerUiInstead() {
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, true, false, true, true, false, false))
    }

    @Test fun aLockedScreenKeepsTheSystemIncomingCallPresentation() {
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, false, true, true, true, false, false))
    }

    @Test fun ungrantedOverlayOrBlockedNotificationsNeverGetBypassed() {
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, false, false, false, true, false, false))
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, false, false, true, false, false, false))
    }

    @Test fun acceptedEndedExpiredOrSuspendedCallsCannotLeaveAnOverlay() {
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            false, false, false, true, true, false, false))
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, false, false, true, true, true, false))
        assertFalse(NativeCallNotificationPolicy.needsPersistentAnswerBanner(
            true, false, false, true, true, false, true))
    }

    @Test fun compatibleAudioMovesBackToTheSystemWhenTheDesktopSurfaceCloses() {
        assertTrue(NativeCallNotificationPolicy.usesLocalAudio(true, true, false, true))
        assertFalse(NativeCallNotificationPolicy.usesLocalAudio(true, true, false, false))
        assertTrue(NativeCallNotificationPolicy.usesLocalAudio(true, true, true, false))
    }

    @Test fun normalSystemCallsAndAcceptedCallsNeverGetASecondLocalRingtone() {
        assertFalse(NativeCallNotificationPolicy.usesLocalAudio(true, false, false, true))
        assertFalse(NativeCallNotificationPolicy.usesLocalAudio(true, false, true, false))
        assertFalse(NativeCallNotificationPolicy.usesLocalAudio(false, true, true, true))
    }

    @Test fun publishedAudioOwnerChangesAreObservableEvenWhenTransientPendingIsConflated() {
        val system = NativeCallNotificationPolicy.IncomingPresentation(NativeCallIdentity("call", 1, "self"),
            NativeCallNotificationPolicy.IncomingDelivery.Published, localAudio = false)
        assertNotEquals(system, system.copy(localAudio = true))
    }
}
