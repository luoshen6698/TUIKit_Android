package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Only exact server-confirmed group tombstones are durable. Neither IM hints nor role caches are authoritative. */
internal object XingDunGroupDeletionRepository {
    private var applicationContext: Context? = null
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val visibility = XingDunGroupDeletionVisibilityState()
    private val failedQueries = ConcurrentHashMap.newKeySet<String>()
    private fun scopeKey(conversationID: String) = "${XingDunC2CDeletionRepository.accountKey()}:${XingDunSessionManager.currentAuthenticationGeneration()}:$conversationID"

    fun initialize(context: Context) { applicationContext = context.applicationContext }
    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences("xingdun_group_authoritative_deletions_v1", Context.MODE_PRIVATE)
    fun deletedIDs(context: Context, conversationID: String): Set<String> {
        val group = XingDunGroupDeletionPolicy.group(conversationID) ?: return emptySet()
        val account = XingDunC2CDeletionRepository.accountKey() ?: return emptySet()
        return preferences(context).getStringSet("$account:$group", emptySet()).orEmpty().toSet()
    }

    internal fun displayFilter(context: Context): XingDunMessageDisplayFilter {
        val appContext = context.applicationContext
        return XingDunMessageDisplayFilter(
            currentScope = {
                val session = XingDunSessionManager.currentSession()
                val generation = XingDunSessionManager.currentAuthenticationGeneration()
                val account = session?.let(XingDunC2CDeletionRepository::accountKey)
                if (session == null || generation == null || account == null) null
                else XingDunMessageDisplayScope(account, generation, session.timUserId)
            },
            deletedIDs = { conversation, scope ->
                val group = XingDunGroupDeletionPolicy.group(conversation)
                if (group != null) preferences(appContext).getStringSet("${scope.account}:$group", emptySet()).orEmpty().toSet()
                else XingDunC2CDeletionRepository.displayDeletedIDs(appContext, conversation, scope.selfUserID, scope.account)
            },
            visibility = visibility,
            revision = { SearchMessageVisibility.revision.value },
        )
    }

    fun isDeleted(conversationID: String, id: String) = applicationContext?.let { id in deletedIDs(it, conversationID) } == true
    fun isHidden(conversationID: String, id: String): Boolean = isDeleted(conversationID, id) ||
        visibility.isPending(scopeKey(conversationID), id)
    fun quoteHidden(conversationID: String, id: String): Boolean = XingDunGroupDeletionPolicy.group(conversationID) != null &&
        (isHidden(conversationID, id) || !visibility.isChecked(scopeKey(conversationID), id))
    fun verificationFailed(conversationID: String) = scopeKey(conversationID) in failedQueries

    fun holdHint(conversationID: String, ids: Set<String>) {
        visibility.hold(scopeKey(conversationID), ids)
        XingDunConversationPreviewResolver.clear()
        XingDunPinnedMessageRepository.onGroupVisibilityChanged(conversationID)
        SearchMessageVisibility.invalidate()
    }

    /** Only a genuine local Sending snapshot grants immediate display; historical SendSuccess has no exemption. */
    fun canDisplay(conversationID: String, message: MessageInfo): Boolean {
        if (XingDunGroupDeletionPolicy.group(conversationID) == null) return true
        val key = scopeKey(conversationID)
        val self = XingDunSessionManager.currentSession()?.timUserId ?: return false
        val own = message.isSentBySelf && message.from.userID == self
        return visibility.canDisplay(key, message.msgID, isDeleted(conversationID, message.msgID),
            own && message.status == MessageStatus.SENDING, own && message.status == MessageStatus.SEND_FAIL)
    }

    suspend fun check(context: Context, conversationID: String, ids: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        val group = XingDunGroupDeletionPolicy.group(conversationID) ?: error("Not a group conversation")
        val key = scopeKey(conversationID)
        val requestStartedAt = System.currentTimeMillis()
        val deleted = withTimeoutOrNull(12_000) { requests(context).check(group, ids) } ?: throw IOException("Deletion lookup unavailable")
        check(key == scopeKey(conversationID)) { "Authentication instance changed" }
        val changed = visibility.checked(key, ids, requestStartedAt)
        if (changed) {
            XingDunConversationPreviewResolver.invalidateResolved()
            XingDunPinnedMessageRepository.onGroupVisibilityChanged(conversationID)
            SearchMessageVisibility.invalidate()
        }
        failedQueries.remove(key)
        deleted
    }

    suspend fun allowed(context: Context, conversationID: String, senders: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(12_000) { requests(context).allowed(XingDunGroupDeletionPolicy.group(conversationID) ?: error("Not a group conversation"), senders) }
            ?: throw IOException("Permission lookup unavailable")
    }

    suspend fun delete(context: Context, conversationID: String, id: String, sender: String) = withContext(Dispatchers.IO) {
        requests(context).delete(XingDunGroupDeletionPolicy.group(conversationID) ?: error("Not a group conversation"), id, sender)
    }

    suspend fun filterMessages(context: Context, conversationID: String, messages: List<MessageInfo>): List<MessageInfo> {
        if (XingDunGroupDeletionPolicy.group(conversationID) == null) return messages
        val key = scopeKey(conversationID)
        val ids = SearchMessageVisibility.candidateIDs(messages).filter(XingDunGroupDeletionPolicy::validMessage).toSet()
        try { ids.chunked(100).forEach {
            check(key == scopeKey(conversationID)) { "Authentication instance changed" }
            check(context, conversationID, it.toSet())
            check(key == scopeKey(conversationID)) { "Authentication instance changed" }
        } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failedQueries.add(key) /* Keep previously authorized rows, never authorize a failed lookup. */ }
        if (key != scopeKey(conversationID)) return emptyList()
        return SearchMessageVisibility.redactQuotes(conversationID, messages.filter { canDisplay(conversationID, it) })
    }

    fun addListener(listener: (String) -> Unit) { listeners += listener }
    fun removeListener(listener: (String) -> Unit) { listeners -= listener }
    fun resetSession() { visibility.clear(); failedQueries.clear(); SearchMessageVisibility.invalidate() }

    private var coordinator: XingDunGroupDeletionRequests? = null
    @Synchronized private fun requests(context: Context): XingDunGroupDeletionRequests {
        coordinator?.let { return it }
        val appContext = context.applicationContext
        return XingDunGroupDeletionRequests(
            account = XingDunC2CDeletionRepository::accountKey,
            session = XingDunSessionManager::currentAuthenticationGeneration,
            query = { group, ids ->
                val response = XingDunSessionManager.apiClient().post<JsonObject>(requireNotNull(XingDunSessionManager.currentSession()),
                    "message/groupDeleted", mapOf("group_id" to group, "message_ids" to ids.toList()), JsonObject::class.java)
                XingDunGroupDeletionPolicy.deleted(group, ids, response)
            },
            permissions = { group, senders ->
                val response = XingDunSessionManager.apiClient().post<JsonObject>(requireNotNull(XingDunSessionManager.currentSession()),
                    "message/groupDeletePermissions", mapOf("group_id" to group, "sender_ids" to senders.toList()), JsonObject::class.java)
                XingDunGroupDeletionPolicy.permissions(group, senders, response)
            },
            remove = { group, id ->
                val response = XingDunSessionManager.apiClient().post<JsonObject>(requireNotNull(XingDunSessionManager.currentSession()),
                    "message/groupDelete", mapOf("group_id" to group, "message_server_id" to id), JsonObject::class.java)
                XingDunGroupDeletionPolicy.validateDeleted(id, response)
            },
            remember = { account, group, ids ->
                val prefs = preferences(appContext)
                var changed = false
                synchronized(prefs) {
                    check(account == XingDunC2CDeletionRepository.accountKey()) { "Account changed" }
                    val key = "$account:$group"
                    val old = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
                    if (!old.containsAll(ids)) {
                        check(prefs.edit().putStringSet(key, old + ids).commit()) { "Deletion state was not persisted" }
                        changed = true
                    }
                }
                if (changed) {
                    val conversation = "group_$group"
                    XingDunConversationPreviewResolver.clear()
                    SearchMessageVisibility.invalidate()
                    XingDunPinnedMessageRepository.onGroupDeletion(conversation)
                    listeners.forEach { it(conversation) }
                }
            },
            commit = { generation, action -> XingDunSessionManager.commitIfAuthenticationGeneration(generation as Long, action) },
        ).also { coordinator = it }
    }
}
