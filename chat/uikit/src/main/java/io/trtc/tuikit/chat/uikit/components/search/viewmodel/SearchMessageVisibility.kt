package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingMessageProtocol
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** App-owned authoritative deletion filtering, shared by conversation and global search results. */
object SearchMessageVisibility {
    @Volatile private var provider: (suspend (String, List<MessageInfo>) -> List<MessageInfo>)? = null
    @Volatile private var deletedProvider: (String, String) -> Boolean = { _, _ -> false }
    @Volatile private var quoteHiddenProvider: ((String, String) -> Boolean)? = null
    @Volatile private var confirmedDeletedProvider: ((String, String) -> Boolean)? = null
    @Volatile private var verificationFailureProvider: (String) -> Boolean = { false }
    @Volatile private var sessionProvider: () -> String? = { null }
    // App-owned and process-scoped; never captures an Activity or a per-chat lifetime.
    @Volatile private var displayProvider: ((String, List<MessageInfo>, (List<MessageInfo>) -> List<MessageInfo>) -> List<MessageInfo>)? = null
    val hasDisplayProvider: Boolean get() = displayProvider != null
    fun setDisplayProvider(value: ((String, List<MessageInfo>, (List<MessageInfo>) -> List<MessageInfo>) -> List<MessageInfo>)?) { displayProvider = value }

    /** One synchronous batch, including the existing mapper, before the app verifies its source again. */
    fun mapForDisplay(conversationID: String, messages: List<MessageInfo>, mapper: (List<MessageInfo>) -> List<MessageInfo>): List<MessageInfo> {
        val visible = messages.filterNot(TypingMessageProtocol::isTypingMessage)
        val display = displayProvider
        if (display != null) return try { display(conversationID, visible, mapper) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() } // A failed authority snapshot never falls back to visible rows.
        return mapper(redactQuotes(conversationID, visible.filterNot { isDeleted(conversationID, it.msgID) }))
    }

    private val searchVerificationFailedState = MutableStateFlow(false)
    val searchVerificationFailed: StateFlow<Boolean> = searchVerificationFailedState
    private val revisionState = MutableStateFlow(0L)
    val revision: StateFlow<Long> = revisionState

    fun setProvider(value: suspend (String, List<MessageInfo>) -> List<MessageInfo>) { provider = value }
    fun setDeletedProvider(value: (String, String) -> Boolean) { deletedProvider = value }
    fun setQuoteHiddenProvider(value: ((String, String) -> Boolean)?) { quoteHiddenProvider = value }
    fun setConfirmedDeletedProvider(value: ((String, String) -> Boolean)?) { confirmedDeletedProvider = value }
    fun setVerificationFailureProvider(value: (String) -> Boolean) { verificationFailureProvider = value }
    fun setSessionProvider(value: () -> String?) { sessionProvider = value }
    fun currentSessionKey(): String? = sessionProvider()
    fun isDeleted(conversationID: String, messageID: String): Boolean =
        (conversationID.startsWith("c2c_") || conversationID.startsWith("group_")) && deletedProvider(conversationID, messageID)
    fun isConfirmedDeleted(conversationID: String, messageID: String) = confirmedDeletedProvider?.invoke(conversationID, messageID)
        ?: isDeleted(conversationID, messageID)
    fun isQuoteHidden(conversationID: String, messageID: String) = isDeleted(conversationID, messageID) ||
        quoteHiddenProvider?.invoke(conversationID, messageID) == true
    fun invalidate() { revisionState.update { it + 1 } }

    suspend fun authoritativeMessages(conversationID: String, messages: List<MessageInfo>): List<MessageInfo> =
        (provider?.invoke(conversationID, messages.filterNot(TypingMessageProtocol::isTypingMessage)) ?: messages)
            .filterNot { TypingMessageProtocol.isTypingMessage(it) || isDeleted(conversationID, it.msgID) }

    /** An action on a retained source snapshot needs a fresh successful read, unlike an already displayed safe row. */
    suspend fun messagesForAction(conversationID: String, messages: List<MessageInfo>): List<MessageInfo> {
        if (!conversationID.startsWith("group_")) return messages.filterNot(TypingMessageProtocol::isTypingMessage)
        val session = currentSessionKey() ?: error("Missing authentication instance")
        val checked = authoritativeMessages(conversationID, messages)
        check(session == currentSessionKey() && !verificationFailureProvider(conversationID)) { "Source verification unavailable" }
        return redactQuotes(conversationID, checked.filterNot { isDeleted(conversationID, it.msgID) })
    }

    fun quoteForSend(conversationID: String, message: MessageInfo?): MessageInfo? = message?.takeUnless {
        conversationID.startsWith("group_") && isQuoteHidden(conversationID, it.msgID)
    }

    /** A quote can outlive the original history page. Check its stable target ID as well. */
    fun candidateIDs(messages: List<MessageInfo>): Set<String> = messages.flatMap {
        listOfNotNull(it.msgID, it.quoteInfo?.msgID)
    }.filter(String::isNotBlank).toSet()

    fun redactQuotes(conversationID: String, messages: List<MessageInfo>): List<MessageInfo> = messages.map { message ->
        val quote = message.quoteInfo
        if (quote != null && isQuoteHidden(conversationID, quote.msgID)) {
            message.copy(quoteInfo = quote.copy(status = MessageStatus.DELETED, messagePayload = null))
        } else message
    }

    /** Synchronous removal closes the hint-to-network window in an already displayed search list. */
    fun filterKnown(results: List<MessageSearchResultItem>): List<MessageSearchResultItem> = results.mapNotNull { result ->
        val rows = mapForDisplay(result.conversationID, result.messageList) { it }
        if (rows.isEmpty()) null else result.copy(messageList = rows,
            messageCount = (result.messageCount - (result.messageList.size - rows.size)).coerceAtLeast(rows.size))
    }

    suspend fun filter(results: List<MessageSearchResultItem>, sourceSession: String? = currentSessionKey()): List<MessageSearchResultItem> =
        filterResults(results, sourceSession, publishFailure = true)

    internal suspend fun filterHistoryUnit(results: List<MessageSearchResultItem>, sourceSession: String?): List<MessageSearchResultItem> =
        filterResults(results, sourceSession, publishFailure = false)

    internal fun batchVerificationFailed(results: List<MessageSearchResultItem>): Boolean =
        results.any { verificationFailureProvider(it.conversationID) }

    internal fun publishVerificationFailure(failed: Boolean) { searchVerificationFailedState.value = failed }

    private suspend fun filterResults(results: List<MessageSearchResultItem>, sourceSession: String?, publishFailure: Boolean): List<MessageSearchResultItem> {
        val filter = provider ?: return filterKnown(results)
        val groupBatch = results.any { it.conversationID.startsWith("group_") }
        fun isCurrent() = !groupBatch || (sourceSession != null && sourceSession == currentSessionKey())
        if (!isCurrent()) return emptyList()
        val visible = results.mapNotNull { result ->
            if (!isCurrent()) return emptyList()
            val visible = filter(result.conversationID, result.messageList.filterNot(TypingMessageProtocol::isTypingMessage))
                .filterNot(TypingMessageProtocol::isTypingMessage)
            if (!isCurrent()) return emptyList()
            if (visible.isEmpty()) null else result.copy(
                messageList = visible,
                messageCount = (result.messageCount - (result.messageList.size - visible.size)).coerceAtLeast(visible.size),
            )
        }
        if (!isCurrent()) return emptyList()
        if (publishFailure) publishVerificationFailure(batchVerificationFailed(results))
        return visible
    }
}
