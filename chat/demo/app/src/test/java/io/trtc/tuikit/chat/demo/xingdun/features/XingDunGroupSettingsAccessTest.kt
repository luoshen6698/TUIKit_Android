package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class XingDunGroupSettingsAccessTest {
    @Test fun `authorization must match the exact group action target and boolean result`() {
        val response = JsonParser.parseString("""{"group_id":"g1","action":"private_chat","target_id":"u2","allowed":true}""").asJsonObject
        assertTrue(XingDunGroupSettingsAccess.matches(response, "g1", "private_chat", "u2"))
        assertFalse(XingDunGroupSettingsAccess.matches(response, "g2", "private_chat", "u2"))
        assertFalse(XingDunGroupSettingsAccess.matches(response, "g1", "history", "u2"))
        assertFalse(XingDunGroupSettingsAccess.matches(response, "g1", "private_chat", "u3"))
        response.addProperty("allowed", "true")
        assertFalse(XingDunGroupSettingsAccess.matches(response, "g1", "private_chat", "u2"))
    }
}
