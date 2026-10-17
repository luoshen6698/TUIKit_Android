package io.trtc.tuikit.chat.demo.xingdun.routing

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.util.Locale

internal sealed interface XingDunNotificationDestination {
    data class Conversation(val conversationID: String) : XingDunNotificationDestination
    enum class Tab : XingDunNotificationDestination { MESSAGES, CONTACTS, WORKSPACE, PROFILE }
}

/** Stable account identity only: credential renewal must not invalidate an otherwise identical account. */
internal data class XingDunNotificationScope(
    val companyId: Int,
    val companyCode: String,
    val sdkAppId: Int,
    val apiBaseUrl: String,
    val userId: String,
) {
    val key: String
        get() = JsonArray().apply {
            add(companyId)
            add(companyCode.lowercase(Locale.ROOT))
            add(sdkAppId)
            add(apiBaseUrl)
            add(userId)
        }.toString()

    fun allows(destination: XingDunNotificationDestination): Boolean {
        if (destination !is XingDunNotificationDestination.Conversation ||
            !destination.conversationID.startsWith("c2c_")) return true
        // This is the server's ImUser::buildTimUserId contract, not a guessed tenant from the payload.
        val peer = destination.conversationID.removePrefix("c2c_")
        val prefix = "xd_${companyCode.lowercase(Locale.ROOT)}_"
        return peer.startsWith(prefix) && POSITIVE_ID.matches(peer.removePrefix(prefix))
    }

    companion object {
        private val POSITIVE_ID = Regex("[1-9][0-9]{0,19}")
    }
}

internal enum class XingDunNotificationConsumption { WAIT, DISCARD, OPEN }

internal object XingDunNotificationConsumptionPolicy {
    fun decide(
        pendingKey: String,
        current: XingDunNotificationScope?,
        imSdkAppId: Int,
        imUserId: String?,
        authenticationReady: Boolean,
        navigationReady: Boolean,
    ): XingDunNotificationConsumption {
        if (current == null || pendingKey != current.key) return XingDunNotificationConsumption.DISCARD
        if ((imSdkAppId > 0 && imSdkAppId != current.sdkAppId) ||
            (!imUserId.isNullOrEmpty() && imUserId != current.userId)) return XingDunNotificationConsumption.DISCARD
        if (!authenticationReady || !navigationReady) return XingDunNotificationConsumption.WAIT
        if (imSdkAppId <= 0 || imUserId.isNullOrEmpty()) return XingDunNotificationConsumption.WAIT
        return XingDunNotificationConsumption.OPEN
    }
}

/** Only the existing XingDun schema and verified TIMPush conversation fields are understood. */
internal object XingDunNotificationPayloadParser {
    private const val MAX_PAYLOAD_BYTES = 1_024
    private val identifier = Regex("[A-Za-z0-9_@#.-]{1,128}")

    fun parse(extension: String?): XingDunNotificationDestination? = runCatching {
        val json = readObject(extension ?: return null)
        if (json.has("xd_push_version") || json.has("xd_voip_version") || json.has("route")) {
            return parseXingDun(json)
        }
        val candidates = mutableListOf<XingDunNotificationDestination>()
        if (json.has("ChatKey")) candidates += chatKey(json.string("ChatKey")) ?: return null
        if (json.has("entity")) {
            val entity = json.get("entity")
            val body = when {
                entity.isJsonObject -> entity.asJsonObject
                entity.isJsonPrimitive && entity.asJsonPrimitive.isString -> readObject(entity.asString)
                else -> return null
            }
            candidates += parseEntity(body) ?: return null
        } else if (json.has("sender") || json.has("chatType")) {
            // OPPO's TIMPush click processor can unwrap the entity string before invoking the listener.
            candidates += parseEntity(json) ?: return null
        }
        candidates.distinct().singleOrNull()
    }.getOrNull()

    fun encode(destination: XingDunNotificationDestination): String = JsonObject().apply {
        addProperty("xd_push_version", 1)
        when (destination) {
            is XingDunNotificationDestination.Conversation -> {
                val group = destination.conversationID.startsWith("group_")
                addProperty("conversation_type", if (group) "group" else "c2c")
                addProperty("target", destination.conversationID.removePrefix(if (group) "group_" else "c2c_"))
            }
            is XingDunNotificationDestination.Tab -> addProperty("route", destination.name.lowercase(Locale.ROOT))
        }
    }.toString()

    private fun parseXingDun(json: JsonObject): XingDunNotificationDestination? {
        if (json.has("xd_voip_version")) {
            if (json.has("xd_push_version") || json.number("xd_voip_version") != 1) return null
            if (!json.has("conversation_id")) return XingDunNotificationDestination.Tab.MESSAGES
            return conversationID(json.string("conversation_id"))
        }
        if (json.has("xd_push_version") && json.number("xd_push_version") != 1) return null
        val route = if (json.has("route")) json.string("route")?.lowercase(Locale.ROOT) ?: return null else null
        val type = if (json.has("conversation_type")) json.string("conversation_type")?.lowercase(Locale.ROOT) ?: return null else null
        if (type != null) {
            if (json.number("xd_push_version") != 1) return null
            return when (type) {
                "c2c", "direct", "single", "customer_service" -> conversation("c2c_", json.string("target"))
                "group", "team" -> conversation("group_", json.string("target"))
                "system" -> if (json.string("target")?.length?.let { it in 1..256 } == true)
                    XingDunNotificationDestination.Tab.MESSAGES else null
                else -> null
            }
        }
        return when (route) {
            "friend_application", "group_application", "contacts" -> XingDunNotificationDestination.Tab.CONTACTS
            "workspace", "customer_service" -> XingDunNotificationDestination.Tab.WORKSPACE
            "profile" -> XingDunNotificationDestination.Tab.PROFILE
            "messages" -> XingDunNotificationDestination.Tab.MESSAGES
            else -> null
        }
    }

    private fun parseEntity(entity: JsonObject): XingDunNotificationDestination? {
        // action=2 is TUICallKit signaling; ordinary notification routing must not steal its launch.
        if ((entity.has("action") && entity.number("action") != 1) ||
            (entity.has("version") && entity.number("version") != 1)) return null
        return when (entity.number("chatType")) {
            1 -> conversation("c2c_", entity.string("sender"))
            2 -> conversation("group_", entity.string("sender"))
            else -> null
        }
    }

    private fun chatKey(value: String?): XingDunNotificationDestination? = when {
        value == null -> null
        value.startsWith("C2C") -> conversation("c2c_", value.removePrefix("C2C"))
        value.startsWith("GROUP") -> conversation("group_", value.removePrefix("GROUP"))
        else -> null
    }

    private fun conversationID(value: String?): XingDunNotificationDestination? = when {
        value == null -> null
        value.startsWith("c2c_") -> conversation("c2c_", value.removePrefix("c2c_"))
        value.startsWith("group_") -> conversation("group_", value.removePrefix("group_"))
        else -> null
    }

    private fun conversation(prefix: String, value: String?): XingDunNotificationDestination? {
        val id = value ?: return null
        if (!identifier.matches(id) || id.startsWith("c2c_") || id.startsWith("group_")) return null
        return XingDunNotificationDestination.Conversation(prefix + id)
    }

    private fun JsonObject.string(name: String): String? = get(name)?.let {
        if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString.takeIf(String::isNotEmpty) else null
    }

    private fun JsonObject.number(name: String): Int? = get(name)?.let {
        if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asBigDecimal.intValueExact() else null
    }

    private fun readObject(raw: String): JsonObject {
        require(raw.isNotEmpty() && raw.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
        return JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            val value = readValue(reader, 0)
            require(value.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT)
            value.asJsonObject
        }
    }

    private fun readValue(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 6)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!has(name))
                    add(name, readValue(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) add(readValue(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> throw IllegalArgumentException("Unsupported notification value")
        }
    }
}
