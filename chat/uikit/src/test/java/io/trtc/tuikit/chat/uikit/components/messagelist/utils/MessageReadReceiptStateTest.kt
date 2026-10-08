package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageReceipt
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import org.junit.Assert.*
import org.junit.Test

class MessageReadReceiptStateTest {
    private fun group(read: Int, unread: Int) = MessageInfo(
        msgID = "video", isSentBySelf = true, needReadReceipt = true,
        status = MessageStatus.SEND_SUCCESS, conversationType = ConversationType.GROUP,
        readReceiptInfo = MessageReceipt(readCount = read, unreadCount = unread)
    )

    @Test fun emptyReceiptIsNotEvidenceThatEveryoneRead() {
        val message = group(0, 0)
        assertEquals(MessageReadReceiptDisplayState.UNREAD, message.readReceiptDisplayState)
        assertFalse(message.isAllRead)
        assertTrue(message.isUnread)
    }

    @Test fun partialAndCompleteReceiptsRemainAccurate() {
        assertEquals(MessageReadReceiptDisplayState.READ, group(1, 2).readReceiptDisplayState)
        assertEquals(MessageReadReceiptDisplayState.ALL_READ, group(3, 0).readReceiptDisplayState)
    }

    @Test fun sendingAndFailedMessagesNeverShowReceipt() {
        val message = group(3, 0)
        message.status = MessageStatus.SENDING
        assertFalse(message.shouldShowReadReceiptIndicator())
        message.status = MessageStatus.SEND_FAIL
        assertFalse(message.shouldShowReadReceiptIndicator())
    }

    @Test fun directMessageUsesPeerReceipt() {
        val message = group(0, 0).copy(conversationType = ConversationType.C2C)
        message.readReceiptInfo!!.isPeerRead = true
        assertEquals(MessageReadReceiptDisplayState.READ, message.readReceiptDisplayState)
        assertTrue(message.isAllRead)
    }
}
