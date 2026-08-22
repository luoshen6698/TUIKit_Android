package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunCustomMessageParserTest {
    @Test
    fun parsesNestedRedpacketAsDisplayOnlyBusinessMessage() {
        val message = XingDunCustomMessageParser.parse(
            """{"type":"redpacket","data":{"packet_no":"P001","greeting":"恭喜发财","scene":"c2c"}}"""
        )

        requireNotNull(message)
        assertEquals("redpacket", message.type)
        assertEquals("P001", message.values["packet_no"])
        assertEquals("恭喜发财", message.values["greeting"])
        assertFalse(message.isControl)
    }

    @Test
    fun hidesControlMessagesButKeepsGroupRefreshVisible() {
        assertTrue(
            requireNotNull(XingDunCustomMessageParser.parse("""{"type":"remote_delete","data":{"message_id":"M1"}}""")).isControl
        )
        assertTrue(
            requireNotNull(XingDunCustomMessageParser.parse("""{"type":"config_refresh","scope":"user"}""")).isControl
        )
        assertFalse(
            requireNotNull(XingDunCustomMessageParser.parse("""{"type":"config_refresh","scope":"group"}""")).isControl
        )
    }

    @Test
    fun doesNotCaptureUnrelatedTencentCustomMessages() {
        assertNull(XingDunCustomMessageParser.parse("""{"businessID":"TUICallKit","version":1}"""))
    }

    @Test
    fun acceptsExplicitXingDunDescriptionEnvelope() {
        val message = XingDunCustomMessageParser.parse("{}", "XingDun:report_notice")
        assertEquals("report_notice", message?.type)
    }
}
