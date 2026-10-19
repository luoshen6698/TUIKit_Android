package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunForegroundNotificationPolicyTest {
    @Test
    fun activeMutedAndUnsupportedMessagesAreSilent() {
        listOf(
            XingDunForegroundNotificationPolicy.decide(true, true, false, true, true, true),
            XingDunForegroundNotificationPolicy.decide(true, false, true, true, true, true),
            XingDunForegroundNotificationPolicy.decide(true, false, false, false, true, true),
        ).forEach { decision ->
            assertEquals(XingDunForegroundNotificationDecision(false, false, false), decision)
        }
    }

    @Test
    fun differentForegroundConversationHonorsIndependentPreferences() {
        assertEquals(
            XingDunForegroundNotificationDecision(true, false, true),
            XingDunForegroundNotificationPolicy.decide(true, false, false, true, false, true),
        )
        assertEquals(
            XingDunForegroundNotificationDecision(true, true, false),
            XingDunForegroundNotificationPolicy.decide(true, false, false, true, true, false),
        )
    }

    @Test
    fun backgroundDeliveryAndSilentPreferenceStillProduceVisibleNotifications() {
        assertEquals(XingDunForegroundNotificationDecision(true, true, true),
            XingDunForegroundNotificationPolicy.decide(false, false, false, true, true, true))
        assertEquals(XingDunForegroundNotificationDecision(true, false, false),
            XingDunForegroundNotificationPolicy.decide(true, false, false, true, false, false))
    }

    @Test
    fun staleActiveChatDoesNotSuppressBackgroundMessages() {
        assertEquals(XingDunForegroundNotificationDecision(true, true, true),
            XingDunForegroundNotificationPolicy.decide(false, true, false, true, true, true))
    }
}
