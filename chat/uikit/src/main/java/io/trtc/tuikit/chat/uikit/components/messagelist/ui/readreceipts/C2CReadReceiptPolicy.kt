package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal object C2CReadReceiptPolicy {
    fun shouldMarkRead(
        message: MessageInfo,
        conversationPeerUserID: String,
        receiptPeerUserID: String?,
        receiptTimestamp: Long
    ): Boolean {
        val messageTimestamp = message.timestamp ?: return false
        return receiptTimestamp > 0 &&
            receiptPeerUserID == conversationPeerUserID &&
            message.conversationType == ConversationType.C2C &&
            message.isSentBySelf &&
            message.needReadReceipt &&
            message.readReceiptInfo?.isPeerRead != true &&
            messageTimestamp <= receiptTimestamp
    }
}
