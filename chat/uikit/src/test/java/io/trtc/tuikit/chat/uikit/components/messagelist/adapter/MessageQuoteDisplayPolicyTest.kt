package io.trtc.tuikit.chat.uikit.components.messagelist.adapter

import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageQuoteDisplayPolicyTest {
    private val labels = MessageQuoteLabels(
        deleted = "deleted",
        revoked = "revoked",
        image = "image",
        video = "video",
        voice = "voice",
        file = "file",
        face = "face",
        custom = "custom",
        merged = "merged",
        unknown = "unknown"
    )

    @Test
    fun deletedQuoteUsesStatusInRegularConversation() {
        val displayData = MessageQuoteDisplayPolicy.resolve(
            quoteInfo = deletedTextQuote("forwarded snapshot"),
            labels = labels
        )

        assertEquals("deleted", displayData.contentText)
        assertTrue(displayData.isStatusText)
    }

    @Test
    fun deletedQuoteKeepsStoredPayloadInMergedForwardDetail() {
        val displayData = MessageQuoteDisplayPolicy.resolve(
            quoteInfo = deletedTextQuote("forwarded snapshot"),
            labels = labels,
            preferSnapshotContent = true
        )

        assertEquals("forwarded snapshot", displayData.contentText)
        assertFalse(displayData.isStatusText)
        assertTrue(displayData.shouldRenderEmoji)
    }

    @Test
    fun revokedQuoteStillUsesStatusInMergedForwardDetail() {
        val displayData = MessageQuoteDisplayPolicy.resolve(
            quoteInfo = deletedTextQuote("forwarded snapshot").copy(status = MessageStatus.REVOKED),
            labels = labels,
            preferSnapshotContent = true
        )

        assertEquals("revoked", displayData.contentText)
        assertTrue(displayData.isStatusText)
    }

    private fun deletedTextQuote(text: String): MessageQuoteInfo {
        return MessageQuoteInfo(
            msgID = "quoted-message-id",
            status = MessageStatus.DELETED,
            timestamp = 1L,
            sequence = 1L,
            sender = MessageSenderInfo(
                userID = "user-id",
                avatarURL = "",
                nickname = "nickname",
                friendRemark = "",
                nameCard = ""
            ),
            messageType = MessageType.TEXT,
            messagePayload = TextMessagePayload(
                text = text,
                translateLanguage = "",
                translatedText = emptyMap()
            )
        )
    }
}
