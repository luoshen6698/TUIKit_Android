package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** App-owned authoritative deletion filtering, shared by conversation and global search results. */
object SearchMessageVisibility {
    @Volatile private var provider: (suspend (String, List<MessageInfo>) -> List<MessageInfo>)? = null
    @Volatile private var deletedProvider: (String, String) -> Boolean = { _, _ -> false }
    private val revisionState = MutableStateFlow(0L)
    val revision: StateFlow<Long> = revisionState

    fun setProvider(value: suspend (String, List<MessageInfo>) -> List<MessageInfo>) { provider = value }
    fun setDeletedProvider(value: (String, String) -> Boolean) { deletedProvider = value }
    fun isDeleted(conversationID: String, messageID: String): Boolean =
        conversationID.startsWith("c2c_") && deletedProvider(conversationID, messageID)
    fun invalidate() { revisionState.update { it + 1 } }

    suspend fun authoritativeMessages(conversationID: String, messages: List<MessageInfo>): List<MessageInfo> =
        (provider?.invoke(conversationID, messages) ?: messages).filterNot { isDeleted(conversationID, it.msgID) }

    /** A quote can outlive the original history page. Check its stable target ID as well. */
    fun candidateIDs(messages: List<MessageInfo>): Set<String> = messages.flatMap {
        listOfNotNull(it.msgID, it.quoteInfo?.msgID)
    }.filter(String::isNotBlank).toSet()

    fun redactQuotes(conversationID: String, messages: List<MessageInfo>): List<MessageInfo> = messages.map { message ->
        val quote = message.quoteInfo
        if (quote != null && isDeleted(conversationID, quote.msgID)) {
            message.copy(quoteInfo = quote.copy(status = MessageStatus.DELETED, messagePayload = null))
        } else message
    }

    suspend fun filter(results: List<MessageSearchResultItem>): List<MessageSearchResultItem> {
        val filter = provider ?: return results
        return results.mapNotNull { result ->
            val visible = filter(result.conversationID, result.messageList)
            if (visible.isEmpty()) null else result.copy(
                messageList = visible,
                messageCount = (result.messageCount - (result.messageList.size - visible.size)).coerceAtLeast(visible.size),
            )
        }
    }
}
