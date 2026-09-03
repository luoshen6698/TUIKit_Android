package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageReceipt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class C2CReadReceiptPolicyTest {
    @Test
    fun marksOutgoingC2CMessagesAtOrBeforePeerReadTimestamp() {
        assertTrue(shouldMark(message(timestamp = 100)))
        assertTrue(shouldMark(message(timestamp = 101), receiptTimestamp = 101))
    }

    @Test
    fun ignoresMessagesOutsideTheMatchingC2CReceiptRange() {
        assertFalse(shouldMark(message(timestamp = 102), receiptTimestamp = 101))
        assertFalse(shouldMark(message(timestamp = 100, isSentBySelf = false)))
        assertFalse(shouldMark(message(timestamp = 100, conversationType = ConversationType.GROUP)))
        assertFalse(shouldMark(message(timestamp = 100), receiptPeerUserID = "another-user"))
    }

    @Test
    fun ignoresDisabledOrAlreadyReadMessages() {
        assertFalse(shouldMark(message(timestamp = 100, needReadReceipt = false)))
        assertFalse(shouldMark(message(timestamp = 100, isPeerRead = true)))
    }

    private fun shouldMark(
        message: MessageInfo,
        receiptTimestamp: Long = 101,
        receiptPeerUserID: String = PEER_USER_ID
    ): Boolean {
        return C2CReadReceiptPolicy.shouldMarkRead(
            message = message,
            conversationPeerUserID = PEER_USER_ID,
            receiptPeerUserID = receiptPeerUserID,
            receiptTimestamp = receiptTimestamp
        )
    }

    private fun message(
        timestamp: Long,
        isSentBySelf: Boolean = true,
        conversationType: ConversationType = ConversationType.C2C,
        needReadReceipt: Boolean = true,
        isPeerRead: Boolean = false
    ): MessageInfo {
        return MessageInfo().apply {
            this.timestamp = timestamp
            this.isSentBySelf = isSentBySelf
            this.conversationType = conversationType
            this.needReadReceipt = needReadReceipt
            this.readReceiptInfo = MessageReceipt(isPeerRead = isPeerRead)
        }
    }

    private companion object {
        const val PEER_USER_ID = "peer-user"
    }
}
