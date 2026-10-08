package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageReceipt
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import org.junit.Assert.*
import org.junit.Test

class MessageListDisplayMapperTest {
    @Test fun sdkMutationDoesNotChangePreviouslySubmittedStatus() {
        val source = MessageInfo(msgID = "video", status = MessageStatus.SENDING)
        val mapper = MessageListDisplayMapper()
        val before = mapper.map(listOf(source))
        source.status = MessageStatus.SEND_SUCCESS
        val after = mapper.map(listOf(source))
        assertEquals(MessageStatus.SENDING, before.single().status)
        assertEquals(MessageStatus.SEND_SUCCESS, after.single().status)
        assertNotEquals(before, after)
    }

    @Test fun receiptMutationDoesNotChangePreviouslySubmittedReceipt() {
        val source = MessageInfo(msgID = "video", readReceiptInfo = MessageReceipt(readCount = 0, unreadCount = 2))
        val mapper = MessageListDisplayMapper()
        val before = mapper.map(listOf(source))
        source.readReceiptInfo!!.readCount = 1
        source.readReceiptInfo!!.unreadCount = 1
        val after = mapper.map(listOf(source))
        assertEquals(0, before.single().readReceiptInfo!!.readCount)
        assertEquals(1, after.single().readReceiptInfo!!.readCount)
        assertNotEquals(before, after)
    }
}
