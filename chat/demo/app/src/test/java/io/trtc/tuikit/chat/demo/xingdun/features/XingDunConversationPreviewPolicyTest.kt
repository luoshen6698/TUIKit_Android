package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunConversationPreviewPolicyTest {
    @Test
    fun `hidden business controls never replace the last visible conversation message`() {
        listOf("pin_message", "read_receipt_summary", "remote_delete", "config_refresh").forEach { type ->
            val message = requireNotNull(XingDunCustomMessageParser.parse(
                """{"xd_type":"$type","payload":{"scope":"account"}}""",
            ))
            assertTrue("$type must be hidden from conversation previews",
                XingDunConversationPreviewPolicy.shouldSkipInConversationList(message))
        }
    }

    @Test
    fun `pin and receipt controls are skipped without deleting their local envelopes`() {
        listOf("pin_message", "read_receipt_summary").forEach { type ->
            val message = requireNotNull(XingDunCustomMessageParser.parse("""{"type":"$type"}"""))
            assertTrue(XingDunConversationPreviewPolicy.shouldSkipInConversationList(message))
            assertFalse(XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(message))
        }
    }

    @Test
    fun `visible cards and group policy notices remain valid previews`() {
        listOf(
            """{"type":"contact_card"}""",
            """{"type":"redpacket"}""",
            """{"type":"config_refresh","payload":{"scope":"group"}}""",
            """{"type":"future_visible_card"}""",
        ).forEach { data ->
            assertFalse(XingDunConversationPreviewPolicy.shouldSkipInConversationList(
                requireNotNull(XingDunCustomMessageParser.parse(data))))
        }
        assertFalse(XingDunConversationPreviewPolicy.shouldSkipInConversationList(null))
    }

    @Test
    fun `remote delete control is removed from local history`() {
        val message = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"xd_type":"remote_delete","payload":{"message_ids":["M1"]}}""",
            ),
        )

        assertTrue(XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(message))
    }

    @Test
    fun `auto delete configuration stays in history but is skipped in conversation list`() {
        val settingNotice = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"xd_type":"auto_delete_config","payload":{"ttl_seconds":120,"version":1}}""",
            ),
        )
        val textCard = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"xd_type":"contact_card","payload":{"user_id":"u1"}}""",
            ),
        )

        assertFalse(XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(settingNotice))
        assertTrue(XingDunConversationPreviewPolicy.shouldSkipInConversationList(settingNotice))
        assertFalse(XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(textCard))
        assertFalse(XingDunConversationPreviewPolicy.shouldSkipInConversationList(textCard))
    }
}
