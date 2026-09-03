package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReadReceiptDisplayPolicyTest {
    @Test
    fun c2cReceiptIndicatorDoesNotDependOnGroupReceiptFlag() {
        assertTrue(message(ConversationType.C2C, needReadReceipt = false).isShowReadReceipt)
    }

    @Test
    fun groupReceiptIndicatorStillRequiresGroupReceiptFlag() {
        assertTrue(message(ConversationType.GROUP, needReadReceipt = true).isShowReadReceipt)
        assertFalse(message(ConversationType.GROUP, needReadReceipt = false).isShowReadReceipt)
    }

    private fun message(
        conversationType: ConversationType,
        needReadReceipt: Boolean
    ): MessageInfo = MessageInfo().apply {
        this.conversationType = conversationType
        this.needReadReceipt = needReadReceipt
        isSentBySelf = true
        status = MessageStatus.SEND_SUCCESS
        messageType = MessageType.TEXT
    }
}
