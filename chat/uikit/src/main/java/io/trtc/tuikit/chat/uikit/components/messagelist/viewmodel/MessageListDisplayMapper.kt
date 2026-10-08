package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class MessageListDisplayMapper(
    private val reverseSource: Boolean = true,
    private val shouldDisplayMessage: (MessageInfo) -> Boolean = { true }
) {
    fun map(source: List<MessageInfo>): List<MessageInfo> {
        val orderedSource = if (reverseSource) {
            source.asReversed().asSequence()
        } else {
            source.asSequence()
        }
        return orderedSource
            .filter { !it.msgID.isNullOrEmpty() }
            .filter(shouldDisplayMessage)
            .distinctBy { it.msgID }
            // SDK models are mutable. DiffUtil needs an independent old status/receipt
            // to observe sending -> success/failure rather than comparing the same object.
            .map { it.copy(readReceiptInfo = it.readReceiptInfo?.copy()) }
            .toList()
    }
}
