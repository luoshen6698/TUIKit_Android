package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/** Group IDs, message IDs and sender IDs are different protocol fields. Never infer authority from a role label. */
internal object XingDunGroupDeletionPolicy {
    private val identifier = Regex("[A-Za-z0-9_@#:.\\-]+")
    fun validGroup(value: String) = value.length in 1..128 && identifier.matches(value)
    fun validMessage(value: String) = value.length in 1..64 && identifier.matches(value)
    fun validSender(value: String) = value.length in 1..128 && identifier.matches(value)
    fun group(conversationID: String): String? = conversationID.takeIf { it.startsWith("group_") }
        ?.removePrefix("group_")?.takeIf(::validGroup)
    fun canOffer(conversationID: String, currentUser: String, messageID: String, sender: String,
                 isSelf: Boolean, sentSuccessfully: Boolean, control: Boolean, allowedByServer: Boolean): Boolean =
        group(conversationID) != null && validSender(currentUser) && validMessage(messageID) && validSender(sender) &&
            !isSelf && sender != currentUser && sentSuccessfully && !control && allowedByServer

    private fun string(response: JsonObject, key: String): String {
        val value = response[key] ?: error("Missing response field")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun ids(response: JsonObject, key: String, requested: Set<String>, valid: (String) -> Boolean): Set<String> {
        val values = response[key]?.takeIf { it.isJsonArray }?.asJsonArray ?: error("Invalid response array")
        require(values.size() <= 100)
        val ids = values.map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            value.asString.also { require(valid(it) && it in requested) }
        }
        require(ids.distinct().size == ids.size)
        return ids.toSet()
    }

    fun deleted(group: String, requested: Set<String>, response: JsonObject): Set<String> {
        require(response.keySet() == setOf("group_id", "message_ids") && string(response, "group_id") == group)
        return ids(response, "message_ids", requested, ::validMessage)
    }

    fun permissions(group: String, requested: Set<String>, response: JsonObject): Set<String> {
        require(response.keySet() == setOf("group_id", "allowed_sender_ids") && string(response, "group_id") == group)
        return ids(response, "allowed_sender_ids", requested, ::validSender)
    }

    fun validateDeleted(messageID: String, response: JsonObject) {
        require(response.keySet() == setOf("message_id", "already_deleted"))
        require(string(response, "message_id") == messageID)
        require(response["already_deleted"]?.isJsonPrimitive == true && response["already_deleted"].asJsonPrimitive.isBoolean)
    }

    /** A bounded, exact-scope hint can schedule reads, but can never create a tombstone. */
    fun hintIDs(conversationID: String, rawData: String?): Set<String> {
        val raw = rawData?.takeIf { it.length <= 65_536 } ?: return emptySet()
        return runCatching<Set<String>> {
            var payload = JsonParser.parseString(raw).asJsonObject
            val type = payload["xd_type"]
            val version = payload["xd_version"]
            require(type?.isJsonPrimitive == true && type.asJsonPrimitive.isString && type.asString == "remote_delete")
            require(version?.isJsonPrimitive == true && version.asJsonPrimitive.isNumber && version.asString == "1")
            repeat(4) { depth ->
                if (payload.has("conversation_id") && payload.has("message_ids")) {
                    val conversation = payload["conversation_id"]
                    require(conversation.isJsonPrimitive && conversation.asJsonPrimitive.isString)
                    return hintIDs(conversationID, mapOf("conversation_id" to conversation.asString, "message_ids" to payload["message_ids"].toString()))
                }
                if (depth == 3) return emptySet()
                val nested = payload["payload"] ?: payload["data"] ?: return emptySet()
                payload = if (nested.isJsonObject) nested.asJsonObject
                    else if (nested.isJsonPrimitive && nested.asJsonPrimitive.isString) JsonParser.parseString(nested.asString).asJsonObject
                    else return emptySet()
            }
            emptySet()
        }.getOrDefault(emptySet())
    }

    private fun hintIDs(conversationID: String, values: Map<String, String>): Set<String> {
        if (group(conversationID) == null || values["conversation_id"] != conversationID) return emptySet()
        val raw = values["message_ids"]?.takeIf { it.length <= 4096 } ?: return emptySet()
        return runCatching {
            val array = JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray ?: return emptySet()
            if (array.size() !in 1..50) return emptySet()
            array.map { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                value.asString.also { require(validMessage(it)) }
            }.toSet()
        }.getOrDefault(emptySet())
    }
}

/** Capture before an SDK/network request, and consult again immediately before publishing a cached snapshot. */
internal class XingDunDeletionPublicationGuard(
    private val account: () -> String?, private val generation: () -> Long?, private val revision: () -> Long,
) {
    private val requestedAccount = account()
    private val requestedGeneration = generation()
    private val requestedRevision = revision()
    fun isCurrent() = requestedAccount != null && requestedGeneration != null && requestedAccount == account() &&
        requestedGeneration == generation() && requestedRevision == revision()
}

/** One mutation per message. Lost responses reconcile through the read endpoint, never an automatic write replay. */
internal class XingDunGroupDeletionRequests(
    private val account: () -> String?,
    private val session: () -> Any?,
    private val query: suspend (String, Set<String>) -> Set<String>,
    private val permissions: suspend (String, Set<String>) -> Set<String>,
    private val remove: suspend (String, String) -> Unit,
    private val remember: (String, String, Set<String>) -> Unit,
    private val commit: (Any, () -> Unit) -> Unit,
) {
    private val operations = ConcurrentHashMap.newKeySet<String>()

    suspend fun check(group: String, ids: Set<String>): Set<String> {
        require(XingDunGroupDeletionPolicy.validGroup(group) && ids.isNotEmpty() && ids.size <= 100 && ids.all(XingDunGroupDeletionPolicy::validMessage))
        val account = account() ?: error("Missing account")
        val session = session() ?: error("Missing session")
        return queryFor(account, session, group, ids)
    }

    suspend fun allowed(group: String, senders: Set<String>): Set<String> {
        require(XingDunGroupDeletionPolicy.validGroup(group) && senders.isNotEmpty() && senders.size <= 100 && senders.all(XingDunGroupDeletionPolicy::validSender))
        val account = account() ?: error("Missing account")
        val session = session() ?: error("Missing session")
        ensureCurrent(account, session)
        val allowed = permissions(group, senders)
        ensureCurrent(account, session)
        require(allowed.all { it in senders })
        return allowed
    }

    suspend fun delete(group: String, id: String, sender: String) {
        require(XingDunGroupDeletionPolicy.validGroup(group) && XingDunGroupDeletionPolicy.validMessage(id) && XingDunGroupDeletionPolicy.validSender(sender))
        val account = account() ?: error("Missing account")
        val session = session() ?: error("Missing session")
        val key = "$account:$group:$id"
        check(operations.add(key)) { "Deletion already in progress" }
        try {
            if (id in queryFor(account, session, group, setOf(id))) return
            check(sender in allowed(group, setOf(sender))) { "Permission changed" }
            ensureCurrent(account, session)
            try {
                remove(group, id)
                persist(account, session, group, setOf(id))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                ensureCurrent(account, session)
                if (id !in queryFor(account, session, group, setOf(id))) throw error
            }
        } finally { operations.remove(key) }
    }

    private suspend fun queryFor(account: String, session: Any, group: String, ids: Set<String>): Set<String> {
        ensureCurrent(account, session)
        val deleted = query(group, ids)
        ensureCurrent(account, session)
        require(deleted.all { it in ids })
        if (deleted.isNotEmpty()) persist(account, session, group, deleted)
        return deleted
    }

    private fun persist(account: String, session: Any, group: String, ids: Set<String>) {
        ensureCurrent(account, session)
        commit(session) { ensureCurrent(account, session); remember(account, group, ids) }
    }

    private fun ensureCurrent(account: String, session: Any) {
        check(account == this.account() && session == this.session()) { "Authentication instance changed" }
    }
}
