package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility

/** Only server-confirmed C2C tombstones are persisted here; group/auto-delete state is separate. */
internal object XingDunC2CDeletionRepository {
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    private var applicationContext: Context? = null
    private var markerStore: XingDunC2CDeletionMarkerStore? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        SearchMessageVisibility.setDeletedProvider(::isDeleted)
        SearchMessageVisibility.setProvider { conversationID, messages ->
            filterMessages(context.applicationContext, conversationID, messages)
        }
    }

    fun isDeleted(conversationID: String, messageID: String): Boolean = applicationContext
        ?.let { messageID in deletedIDs(it, conversationID) } == true

    fun accountKey(): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        return accountKey(session)
    }

    internal fun accountKey(session: io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession): String? {
        val tenant = XingDunTenantBoundary.identity(session)?.key ?: return null
        return JsonArray().apply {
            add(session.apiBaseUrl.trimEnd('/')); add(tenant); add(session.timUserId)
        }.toString()
    }

    fun deletedIDs(context: Context, conversationID: String): Set<String> {
        val session = XingDunSessionManager.currentSession() ?: return emptySet()
        val peer = XingDunC2CDeletionPolicy.peer(conversationID, session.timUserId) ?: return emptySet()
        return markers(context).read(peer)
    }

    internal fun displayDeletedIDs(context: Context, conversationID: String, self: String, account: String): Set<String> {
        val peer = XingDunC2CDeletionPolicy.peer(conversationID, self) ?: return emptySet()
        return markers(context).readForAccount(peer, account)
    }

    suspend fun check(context: Context, conversationID: String, messageIDs: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        val peer = peer(conversationID)
        requests(context).check(peer, messageIDs)
    }

    suspend fun delete(context: Context, conversationID: String, messageID: String) = withContext(Dispatchers.IO) {
        requests(context).delete(peer(conversationID), messageID)
    }

    suspend fun filterMessages(context: Context, conversationID: String, messages: List<MessageInfo>): List<MessageInfo> {
        if (!conversationID.startsWith("c2c_")) return messages
        val account = accountKey() ?: return emptyList()
        val candidates = SearchMessageVisibility.candidateIDs(messages).filter(XingDunC2CDeletionPolicy::validID).toSet()
        try { candidates.chunked(100).forEach { check(context, conversationID, it.toSet()) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* An unavailable lookup must not hide all existing history. */ }
        if (accountKey() != account) return emptyList()
        val deleted = deletedIDs(context, conversationID)
        return SearchMessageVisibility.redactQuotes(conversationID, messages.filterNot { it.msgID in deleted })
    }

    fun addListener(listener: (String) -> Unit) { listeners += listener }
    fun removeListener(listener: (String) -> Unit) { listeners -= listener }

    private fun peer(conversationID: String): String {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        return XingDunC2CDeletionPolicy.peer(conversationID, session.timUserId) ?: error("Not a direct conversation")
    }

    // One coordinator per process ensures rapid repeated taps do not issue duplicate mutations.
    private var coordinator: XingDunC2CDeletionRequests? = null
    @Synchronized private fun requests(context: Context): XingDunC2CDeletionRequests {
        coordinator?.let { return it }
        val appContext = context.applicationContext
        return XingDunC2CDeletionRequests(
            account = ::accountKey,
            query = { peer, ids ->
                val session = XingDunSessionManager.currentSession() ?: error("Missing session")
                val response = XingDunSessionManager.apiClient().post<JsonObject>(
                    session, "message/c2cDeleted",
                    mapOf("peer_user_id" to peer, "message_ids" to ids.toList()), JsonObject::class.java,
                )
                XingDunC2CDeletionPolicy.deletedIDs(peer, ids, response)
            },
            remove = { peer, messageID ->
                val session = XingDunSessionManager.currentSession() ?: error("Missing session")
                val response = XingDunSessionManager.apiClient().post<JsonObject>(
                    session, "message/c2cDelete",
                    mapOf("peer_user_id" to peer, "message_server_id" to messageID), JsonObject::class.java,
                )
                XingDunC2CDeletionPolicy.validateDeleted(peer, messageID, response)
            },
            remember = { account, peer, ids -> markers(appContext).remember(account, peer, ids) },
            session = XingDunSessionManager::currentAuthenticationGeneration,
            commit = { generation, action ->
                XingDunSessionManager.commitIfAuthenticationGeneration(generation as Long, action)
            },
        ).also { coordinator = it }
    }

    @Synchronized private fun markers(context: Context): XingDunC2CDeletionMarkerStore {
        markerStore?.let { return it }
        return XingDunC2CDeletionMarkerStore(context, ::accountKey) { conversationID ->
            XingDunConversationPreviewResolver.clear()
            SearchMessageVisibility.invalidate()
            XingDunPinnedMessageRepository.onC2CDeletion(conversationID)
            listeners.forEach { it(conversationID) }
        }.also { markerStore = it }
    }
}
