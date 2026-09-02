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
    fun exposesVersionedAutoDeleteConfigurationValues() {
        val message = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"auto_delete_config","payload":{"ttl_seconds":604800,"version":5,"updated_at":"2026-08-27T10:00:00+08:00"}}"""
            )
        )
        assertTrue(message.isControl)
        assertEquals("604800", message.values["ttl_seconds"])
        assertEquals("5", message.values["version"])
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

    @Test
    fun dormantRedpacketCannotOpenButRemainsParsable() {
        val message = XingDunCustomMessageParser.parse(
            """{"type":"redpacket","data":{"packet_no":"P002","status":2}}"""
        )

        assertEquals("P002", message?.values?.get("packet_no"))
        assertFalse(XingDunRedpacketAccessPolicy.canOpen(featureEnabled = false))
        assertTrue(XingDunRedpacketAccessPolicy.canOpen(featureEnabled = true))
    }

    @Test
    fun parsesLegacySnakeCaseContactCardContract() {
        val card = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"xingdun_contact_card","version":1,"data":{"user_id":"xd_xc2026_59","custom_id":"b002","display_name":"不会","avatar_url":"https://example.com/avatar.png","department":"产品部"}}"""
            )?.contactCard()
        )

        assertEquals("xd_xc2026_59", card.userID)
        assertEquals("b002", card.customID)
        assertEquals("不会", card.displayName)
        assertEquals("https://example.com/avatar.png", card.avatarURL)
        assertEquals("产品部", card.department)
    }

    @Test
    fun parsesCurrentIosContactCardContract() {
        val card = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"contact_card","data":{"accid":"xd_xc2026_59","name":"不会","avatar":"https://example.com/ios-avatar.png","customId":"b002"}}"""
            )?.contactCard()
        )

        assertEquals("xd_xc2026_59", card.userID)
        assertEquals("b002", card.customID)
        assertEquals("不会", card.displayName)
        assertEquals("https://example.com/ios-avatar.png", card.avatarURL)
    }

    @Test
    fun parsesLegacyCamelCaseContactCardAndFallsBackToUserId() {
        val card = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"contact_card","payload":{"timUserId":"xd_xc2026_270","nickname":"b002"}}"""
            )?.contactCard()
        )

        assertEquals("xd_xc2026_270", card.userID)
        assertEquals("b002", card.displayName)
        assertNull(card.customID)
    }

    @Test
    fun rejectsContactCardWithoutUserIdentity() {
        val message = XingDunCustomMessageParser.parse(
            """{"type":"xingdun_contact_card","display_name":"无效名片"}"""
        )

        assertNull(message?.contactCard())
    }
}
