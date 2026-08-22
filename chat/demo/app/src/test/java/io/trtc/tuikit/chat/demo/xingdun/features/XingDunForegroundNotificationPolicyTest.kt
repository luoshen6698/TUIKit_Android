package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunForegroundNotificationPolicyTest {
    @Test
    fun activeMutedBackgroundAndUnsupportedMessagesAreSilent() {
        listOf(
            XingDunForegroundNotificationPolicy.decide(false, false, false, true, true, true),
            XingDunForegroundNotificationPolicy.decide(true, true, false, true, true, true),
            XingDunForegroundNotificationPolicy.decide(true, false, true, true, true, true),
            XingDunForegroundNotificationPolicy.decide(true, false, false, false, true, true),
        ).forEach { decision ->
            assertEquals(XingDunForegroundNotificationDecision(false, false), decision)
        }
    }

    @Test
    fun differentForegroundConversationHonorsIndependentPreferences() {
        assertEquals(
            XingDunForegroundNotificationDecision(false, true),
            XingDunForegroundNotificationPolicy.decide(true, false, false, true, false, true),
        )
        assertEquals(
            XingDunForegroundNotificationDecision(true, false),
            XingDunForegroundNotificationPolicy.decide(true, false, false, true, true, false),
        )
    }
}
