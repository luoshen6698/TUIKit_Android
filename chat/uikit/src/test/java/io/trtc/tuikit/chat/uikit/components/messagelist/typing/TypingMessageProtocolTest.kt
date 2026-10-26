package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TypingMessageProtocolTest {
    @Test fun `start and stop preserve the Android cross platform contract without input text`() {
        listOf(true, false).forEach { active ->
            val payload = TypingMessageProtocol.buildTypingMessageData(active)
            val json = JsonParser.parseString(payload).asJsonObject
            assertEquals(setOf("businessID", "typingStatus", "version", "userAction", "actionParam"), json.keySet())
            assertEquals("user_typing_status", json["businessID"].asString)
            assertEquals(0, json["version"].asInt)
            assertEquals(active, TypingMessageProtocol.parseTypingStatus(payload))
        }
    }
    @Test fun `malformed controls are ignored rather than clearing another users typing`() {
        listOf("{}", "not json", "[]", """{"businessID":"other","typingStatus":1}""",
            """{"businessID":"user_typing_status","typingStatus":2}""",
            """{"businessID":"user_typing_status","typingStatus":"1"}""",
            """{"businessID":"user_typing_status","typingStatus":true}""").forEach {
            assertNull(TypingMessageProtocol.parseTypingStatus(it))
        }
    }
    @Test fun `legacy Android action fields remain compatible`() {
        assertEquals(true, TypingMessageProtocol.parseTypingStatus("""{"businessID":"user_typing_status","actionParam":"EIMAMSG_InputStatus_Ing"}"""))
        assertEquals(false, TypingMessageProtocol.parseTypingStatus("""{"businessID":"user_typing_status","actionParam":"EIMAMSG_InputStatus_End"}"""))
    }
}
