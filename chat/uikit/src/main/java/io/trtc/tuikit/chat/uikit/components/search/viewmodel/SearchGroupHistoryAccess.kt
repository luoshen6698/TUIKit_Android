package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import kotlinx.coroutines.CancellationException

/** App-owned history policy, separate from message deletion and independent directory search. */
object SearchGroupHistoryAccess {
    @Volatile private var provider: (suspend (String) -> Boolean)? = null
    fun setProvider(value: (suspend (String) -> Boolean)?) { provider = value }

    suspend fun allows(conversationID: String, owner: String?, isCurrent: () -> Boolean = { true }): Boolean {
        if (!conversationID.startsWith("group_")) return isCurrent()
        if (conversationID.removePrefix("group_").isBlank() || owner == null || !isCurrent() ||
            owner != SearchMessageVisibility.currentSessionKey()) return false
        val currentProvider = provider ?: return false
        val allowed = try { currentProvider(conversationID) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        return allowed && currentProvider === provider && isCurrent() && owner == SearchMessageVisibility.currentSessionKey()
    }

}
