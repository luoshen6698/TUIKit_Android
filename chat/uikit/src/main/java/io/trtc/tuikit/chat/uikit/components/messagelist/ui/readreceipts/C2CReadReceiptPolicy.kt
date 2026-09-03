package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal object C2CReadReceiptPolicy {
    fun shouldMarkRead(
        message: MessageInfo,
        conversationPeerUserID: String,
        receiptPeerUserID: String?,
        receiptTimestamp: Long,
        readMessageIDs: Set<String> = emptySet()
    ): Boolean {
        val messageTimestamp = message.timestamp ?: return false
        val matchesReadBoundary = receiptTimestamp > 0 && messageTimestamp <= receiptTimestamp
        val matchesMessageID = message.msgID.isNotBlank() && message.msgID in readMessageIDs
        return receiptPeerUserID == conversationPeerUserID &&
            message.conversationType == ConversationType.C2C &&
            message.isSentBySelf &&
            message.readReceiptInfo?.isPeerRead != true &&
            (matchesReadBoundary || matchesMessageID)
    }
}
