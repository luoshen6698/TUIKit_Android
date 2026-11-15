package io.trtc.tuikit.chat.uikit.components.common

import org.junit.Assert.*
import org.junit.Test

class MessageOfflinePushInfoFactoryTest {
    @Test fun directChatUsesTheCommunicationCategoryWithoutAnUnregisteredLegacyChannel() {
        val push = MessageOfflinePushInfoFactory.create("Friend", "hello", false, "peer", "Friend", null)
        assertEquals("IM", push.extensionInfo["AndroidOPPOCategory"])
        assertEquals(2, push.extensionInfo["AndroidOPPONotifyLevel"])
        assertFalse(push.extensionInfo.containsKey("AndroidOPPOChannelID"))
    }

    @Test fun groupChatRetainsTheGroupClickPayloadAndCommunicationCategory() {
        val push = MessageOfflinePushInfoFactory.create("Group", "hello", true, "peer", "Friend", null)
        assertEquals("IM", push.extensionInfo["AndroidOPPOCategory"])
        val ext = com.google.gson.JsonParser.parseString(push.extensionInfo["ext"] as String).asJsonObject
        assertEquals(2, ext.getAsJsonObject("entity").get("chatType").asInt)
        assertEquals("peer", ext.getAsJsonObject("entity").get("sender").asString)
    }
}
