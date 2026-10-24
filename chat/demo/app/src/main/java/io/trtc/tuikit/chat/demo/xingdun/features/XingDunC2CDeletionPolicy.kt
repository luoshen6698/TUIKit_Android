package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

internal object XingDunC2CDeletionPolicy {
    private val identifier = Regex("[A-Za-z0-9_@#:.\\-]{1,128}")

    fun validID(value: String): Boolean = identifier.matches(value)

    fun peer(conversationID: String, currentUserID: String): String? = conversationID
        .takeIf { it.startsWith("c2c_") }
        ?.removePrefix("c2c_")
        ?.takeIf { validID(it) && it != currentUserID }

    fun canDelete(conversationID: String, currentUserID: String, messageID: String, isSelf: Boolean,
                  sentSuccessfully: Boolean, isControl: Boolean, customType: String? = null): Boolean =
        validID(currentUserID) && peer(conversationID, currentUserID) != null && validID(messageID) && isSelf &&
            sentSuccessfully && !isControl && !customType.equals("redpacket", ignoreCase = true)

    fun deletedIDs(peer: String, requested: Set<String>, response: JsonObject): Set<String> {
        require(response.keySet() == setOf("peer_user_id", "message_ids"))
        require(response["peer_user_id"]?.isJsonPrimitive == true && response["peer_user_id"].asJsonPrimitive.isString)
        require(response["peer_user_id"].asString == peer)
        val array = response["message_ids"]?.takeIf { it.isJsonArray }?.asJsonArray ?: error("Invalid deletion result")
        val ids = array.map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            value.asString.also { require(validID(it) && it in requested) }
        }
        require(ids.size <= 100 && ids.distinct().size == ids.size)
        return ids.toSet()
    }

    fun validateDeleted(peer: String, messageID: String, response: JsonObject) {
        require(response.keySet() == setOf("peer_user_id", "message_id", "already_deleted"))
        require(response["peer_user_id"]?.isJsonPrimitive == true && response["peer_user_id"].asJsonPrimitive.isString)
        require(response["message_id"]?.isJsonPrimitive == true && response["message_id"].asJsonPrimitive.isString)
        require(response["peer_user_id"].asString == peer && response["message_id"].asString == messageID)
        require(response["already_deleted"]?.isJsonPrimitive == true && response["already_deleted"].asJsonPrimitive.isBoolean)
    }
}

/** Notification IDs are hints only. No write to the deletion cache occurs before an authoritative answer. */
internal class XingDunC2CDeletionRequests(
    private val account: () -> String?,
    private val query: suspend (String, Set<String>) -> Set<String>,
    private val remove: suspend (String, String) -> Unit,
    private val remember: (String, String, Set<String>) -> Unit,
    private val session: () -> Any? = account,
    private val commit: (Any, () -> Unit) -> Unit = { expected, action ->
        check(session() == expected) { "Authentication instance changed" }; action()
    },
) {
    private val operations = ConcurrentHashMap.newKeySet<String>()

    suspend fun check(peer: String, messageIDs: Set<String>): Set<String> {
        require(XingDunC2CDeletionPolicy.validID(peer))
        require(messageIDs.isNotEmpty() && messageIDs.size <= 100 && messageIDs.all(XingDunC2CDeletionPolicy::validID))
        val requestedAccount = account() ?: error("Missing session")
        val requestedSession = session() ?: error("Missing authentication instance")
        return queryFor(requestedAccount, requestedSession, peer, messageIDs)
    }

    private suspend fun queryFor(requestedAccount: String, requestedSession: Any, peer: String,
                                 messageIDs: Set<String>): Set<String> {
        ensureCurrent(requestedAccount, requestedSession)
        val deleted = query(peer, messageIDs)
        ensureCurrent(requestedAccount, requestedSession)
        require(deleted.all { it in messageIDs })
        if (deleted.isNotEmpty()) persist(requestedAccount, requestedSession, peer, deleted)
        return deleted
    }

    suspend fun delete(peer: String, messageID: String) {
        require(XingDunC2CDeletionPolicy.validID(peer) && XingDunC2CDeletionPolicy.validID(messageID))
        val requestedAccount = account() ?: error("Missing session")
        val requestedSession = session() ?: error("Missing authentication instance")
        val key = "$requestedAccount:$peer:$messageID"
        check(operations.add(key)) { "Deletion already in progress" }
        try {
            if (messageID in queryFor(requestedAccount, requestedSession, peer, setOf(messageID))) return
            ensureCurrent(requestedAccount, requestedSession)
            try {
                remove(peer, messageID)
                persist(requestedAccount, requestedSession, peer, setOf(messageID))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureCurrent(requestedAccount, requestedSession)
                // A lost response is never success unless the server's deletion record proves it.
                if (messageID !in queryFor(requestedAccount, requestedSession, peer, setOf(messageID))) throw error
            }
        } finally {
            operations.remove(key)
        }
    }

    private fun ensureCurrent(requestedAccount: String, requestedSession: Any) {
        check(account() == requestedAccount && session() == requestedSession) { "Authentication instance changed" }
    }

    private fun persist(requestedAccount: String, requestedSession: Any, peer: String, ids: Set<String>) {
        ensureCurrent(requestedAccount, requestedSession)
        commit(requestedSession) {
            ensureCurrent(requestedAccount, requestedSession)
            remember(requestedAccount, peer, ids)
        }
    }
}
