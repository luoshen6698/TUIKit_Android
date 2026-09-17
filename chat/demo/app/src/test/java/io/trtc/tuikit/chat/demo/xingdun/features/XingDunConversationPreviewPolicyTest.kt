package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunConversationPreviewPolicyTest {
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
    fun `visible custom messages remain in local history`() {
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
        assertFalse(XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(textCard))
    }
}
