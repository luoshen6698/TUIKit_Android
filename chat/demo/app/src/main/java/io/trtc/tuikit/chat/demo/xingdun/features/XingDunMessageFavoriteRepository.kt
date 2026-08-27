package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class XingDunFavoriteMessageRequest(
    val messageId: String,
    val conversationId: String,
    val messageSequence: Long?,
    val senderId: String,
    val messageType: String,
    val text: String,
    val attachment: Any,
    val sentAt: Long?,
)

/** Tenant/account-scoped recent favorite state used by the UIKit long-press action. */
internal object XingDunMessageFavoriteRepository {
    private const val CACHE_MILLIS = 30_000L
    private const val RECENT_PAGE_SIZE = 50
    private val mutex = Mutex()
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val favoriteIDsByMessageID: Map<String, Int?>,
        val fetchedAtMillis: Long,
    )

    fun isFavorite(messageID: String): Boolean = current()?.containsKey(messageID) == true

    suspend fun loadRecent(force: Boolean = false): Set<String> {
        val key = cacheKey() ?: return emptySet()
        if (!force) valid(key)?.let { return it.favoriteIDsByMessageID.keys }
        return mutex.withLock {
            if (!force) valid(key)?.let { return@withLock it.favoriteIDsByMessageID.keys }
            val session = XingDunSessionManager.currentSession() ?: error("Missing session")
            val page = XingDunSessionManager.apiClient().get<JsonObject>(
                session,
                "message/favorites",
                mapOf("page" to "1", "page_size" to RECENT_PAGE_SIZE.toString()),
                JsonObject::class.java,
            )
            val values = XingDunMessageFavoritePolicy.favoriteIDs(page)
            cache[key] = CacheEntry(values, System.currentTimeMillis())
            values.keys
        }
    }

    suspend fun favorite(request: XingDunFavoriteMessageRequest) {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val result = XingDunSessionManager.apiClient().post<JsonObject>(
            session,
            "message/favorite",
            request,
            JsonObject::class.java,
        )
        update(request.messageId, result.int("favorite_id") ?: result.int("id"))
    }

    suspend fun unfavorite(messageID: String) {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        XingDunSessionManager.apiClient().deleteEmpty(
            session,
            "message/favorite",
            mapOf("message_id" to messageID),
        )
        update(messageID, remove = true)
    }

    fun noteRemoved(messageID: String) = update(messageID, remove = true)

    fun clearTenantCache() = cache.clear()

    private fun current(): Map<String, Int?>? = cacheKey()?.let(cache::get)?.favoriteIDsByMessageID

    private fun valid(key: String): CacheEntry? = cache[key]?.takeIf {
        System.currentTimeMillis() - it.fetchedAtMillis in 0 until CACHE_MILLIS
    }

    private fun update(messageID: String, favoriteID: Int? = null, remove: Boolean = false) {
        val key = cacheKey() ?: return
        val values = cache[key]?.favoriteIDsByMessageID.orEmpty().toMutableMap()
        if (remove) values.remove(messageID) else values[messageID] = favoriteID
        cache[key] = CacheEntry(values, System.currentTimeMillis())
    }

    private fun cacheKey(): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenant = XingDunTenantBoundary.identity(session)?.key ?: return null
        return "$tenant:${session.timUserId}"
    }

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asInt }.getOrNull() }
}

internal object XingDunMessageFavoritePolicy {
    fun favoriteIDs(page: JsonObject): Map<String, Int?> {
        val items = sequenceOf("items", "list")
            .mapNotNull { key -> page.get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray }
            .firstOrNull()
            ?: JsonArray()
        return buildMap {
            items.forEach { element ->
                val favorite = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
                val message = favorite.get("message")?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: favorite
                val messageID = message.get("message_id")?.takeUnless(JsonElement::isJsonNull)?.asString?.trim().orEmpty()
                if (messageID.isNotEmpty()) {
                    val favoriteID = sequenceOf("favorite_id", "id")
                        .mapNotNull { name -> favorite.get(name)?.takeUnless(JsonElement::isJsonNull) }
                        .firstNotNullOfOrNull { runCatching { it.asInt }.getOrNull() }
                    put(messageID, favoriteID)
                }
            }
        }
    }

    fun pageAfterRemoval(currentPage: Int): Int = maxOf(0, currentPage - 1)

    fun serverMessageType(atomicType: String): String = when (atomicType.uppercase()) {
        "IMAGE" -> "PICTURE"
        "TEXT", "AUDIO", "VIDEO", "FILE" -> atomicType.uppercase()
        else -> "CUSTOM"
    }
}
