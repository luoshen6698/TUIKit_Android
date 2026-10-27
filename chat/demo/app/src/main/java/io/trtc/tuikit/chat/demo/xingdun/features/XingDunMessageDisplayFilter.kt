package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal data class XingDunMessageDisplayScope(val account: String, val generation: Long, val selfUserID: String) {
    fun groupKey(conversationID: String) = "$account:$generation:$conversationID"
}

/** A render-only scope; no identity or deletion set survives a batch. All callbacks remain on the caller thread. */
internal class XingDunMessageDisplayFilter(
    private val currentScope: () -> XingDunMessageDisplayScope?,
    private val deletedIDs: (String, XingDunMessageDisplayScope) -> Set<String>,
    private val visibility: XingDunGroupDeletionVisibilityState,
    private val revision: () -> Long,
) {
    fun map(conversationID: String, messages: List<MessageInfo>, mapper: (List<MessageInfo>) -> List<MessageInfo>): List<MessageInfo> {
        if (!conversationID.startsWith("group_") && !conversationID.startsWith("c2c_")) return mapper(messages)
        val originalRevision = revision()
        val source = currentScope() ?: return emptyList()
        if (source.account.isBlank() || source.selfUserID.isBlank()) return emptyList()
        val group = conversationID.startsWith("group_")
        val deleted = deletedIDs(conversationID, source).toSet()
        val key = source.groupKey(conversationID)
        val visible = messages.filter { message ->
            if (!group) message.msgID !in deleted else {
                val own = message.isSentBySelf && message.from.userID == source.selfUserID
                visibility.canDisplay(key, message.msgID, message.msgID in deleted,
                    own && message.status == MessageStatus.SENDING, own && message.status == MessageStatus.SEND_FAIL)
            }
        }.map { message ->
            val quote = message.quoteInfo ?: return@map message
            if (quote.msgID in deleted || group && (visibility.isPending(key, quote.msgID) || !visibility.isChecked(key, quote.msgID))) {
                message.copy(quoteInfo = quote.copy(status = MessageStatus.DELETED, messagePayload = null))
            } else message
        }
        val result = mapper(visible)
        return if (source == currentScope() && originalRevision == revision()) result else emptyList()
    }
}
