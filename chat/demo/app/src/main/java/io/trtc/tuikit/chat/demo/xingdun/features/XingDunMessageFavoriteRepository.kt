package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class XingDunFavoriteMessageRequest(
    @field:SerializedName("message_id")
    val messageId: String,
    @field:SerializedName("conversation_id")
    val conversationId: String,
    @field:SerializedName("message_sequence")
    val messageSequence: Long?,
    @field:SerializedName("sender_id")
    val senderId: String,
    @field:SerializedName("message_type")
    val messageType: String,
    @field:SerializedName("text")
    val text: String,
    @field:SerializedName("attachment")
    val attachment: Any,
    @field:SerializedName("sent_at")
    val sentAt: Long?,
)

/** Tenant/account-scoped recent favorite state used by the UIKit long-press action. */
internal object XingDunMessageFavoriteRepository {
    private const val CACHE_MILLIS = 30_000L
    private const val RECENT_PAGE_SIZE = 50
    private val mutex = Mutex()
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    @Volatile private var generation = 0L

    fun currentContext(): String? = cacheKey()?.let { "$it:$generation" }

    private data class CacheEntry(
        val favoriteIDsByMessageID: Map<String, Int?>,
        val fetchedAtMillis: Long,
        val pendingMutations: Map<String, Int?> = emptyMap(),
    )

    fun isFavorite(messageID: String): Boolean = current()?.containsKey(messageID) == true

    suspend fun loadRecent(force: Boolean = false): Set<String> {
        val key = cacheKey() ?: return emptySet()
        val context = currentContext()
        if (!force) valid(key)?.let { return it.favoriteIDsByMessageID.keys }
        return mutex.withLock {
            if (!force) valid(key)?.let { return@withLock it.favoriteIDsByMessageID.keys }
            val readStart = cache[key]?.favoriteIDsByMessageID
            val session = XingDunSessionManager.currentSession() ?: error("Missing session")
            val page = XingDunSessionManager.apiClient().get<JsonObject>(
                session,
                "message/favorites",
                mapOf("page" to "1", "page_size" to RECENT_PAGE_SIZE.toString()),
                JsonObject::class.java,
            )
            val values = XingDunMessageFavoritePolicy.favoriteIDs(page)
            check(context == currentContext()) { "Favorite session changed" }
            // A POST/DELETE completed while this GET was in flight; keep the newer local state.
            val merged = cache.compute(key) { _, latest ->
                val mutations = if (readStart === latest?.favoriteIDsByMessageID) emptyMap() else latest?.pendingMutations.orEmpty()
                CacheEntry(XingDunMessageFavoritePolicy.recentIDsAfterRead(values, mutations),
                    System.currentTimeMillis(), mutations)
            }
            merged?.favoriteIDsByMessageID?.keys.orEmpty()
        }
    }

    suspend fun favorite(request: XingDunFavoriteMessageRequest): JsonObject {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val context = currentContext() ?: error("Missing favorite context")
        val result = XingDunFavoriteCreationRecovery.save(
            create = {
                check(context == currentContext()) { "Favorite session changed" }
                XingDunSessionManager.apiClient().post<JsonObject>(
                    session, "message/favorite", request, JsonObject::class.java,
                ).also { XingDunMessageFavoritePolicy.validateResponse(it, request) }
            },
            confirm = {
                withTimeoutOrNull(12_000L) {
                    for (pageNumber in 1..3) {
                        check(context == currentContext()) { "Favorite session changed" }
                        val page = XingDunSessionManager.apiClient().get<JsonObject>(
                            session, "message/favorites",
                            mapOf("page" to pageNumber.toString(), "page_size" to "50"), JsonObject::class.java,
                        )
                        val items = page.getAsJsonArray("items") ?: page.getAsJsonArray("list")
                            ?: error("Invalid favorites page")
                        items.firstOrNull { it.isJsonObject && XingDunMessageFavoritePolicy.matches(it.asJsonObject, request) }
                            ?.let { return@withTimeoutOrNull XingDunFavoriteConfirmation.Found(it.asJsonObject) }
                        val total = page.get("total")?.asInt ?: error("Missing favorite total")
                        if (pageNumber * 50 >= total) return@withTimeoutOrNull XingDunFavoriteConfirmation.Absent
                    }
                    XingDunFavoriteConfirmation.Unknown
                } ?: XingDunFavoriteConfirmation.Unknown
            },
            isCurrent = { context == currentContext() },
        )
        check(context == currentContext()) { "Favorite session changed" }
        XingDunMessageFavoritePolicy.validateResponse(result, request)
        update(request.messageId, result.int("favorite_id") ?: result.int("id"))
        return result
    }

    suspend fun updateMedia(favoriteID: Int, request: XingDunFavoriteMessageRequest): JsonObject {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val context = currentContext() ?: error("Missing favorite context")
        val result = XingDunSessionManager.apiClient().post<JsonObject>(
            session, "message/favorite/media", mapOf(
                "favorite_id" to favoriteID, "message_id" to request.messageId,
                "conversation_id" to request.conversationId, "attachment" to request.attachment,
            ), JsonObject::class.java,
        )
        check(context == currentContext()) { "Favorite session changed" }
        XingDunMessageFavoritePolicy.validateResponse(result, request)
        check((result.int("favorite_id") ?: result.int("id")) == favoriteID) { "Favorite identity mismatch" }
        return result
    }

    suspend fun unfavorite(messageID: String) {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val context = currentContext()
        XingDunSessionManager.apiClient().deleteEmpty(
            session,
            "message/favorite",
            mapOf("message_id" to messageID),
        )
        check(context == currentContext()) { "Favorite session changed" }
        update(messageID, remove = true)
    }

    fun noteRemoved(messageID: String) = update(messageID, remove = true)

    fun clearTenantCache() { generation += 1; cache.clear() }

    private fun current(): Map<String, Int?>? = cacheKey()?.let(cache::get)?.favoriteIDsByMessageID

    private fun valid(key: String): CacheEntry? = cache[key]?.takeIf {
        System.currentTimeMillis() - it.fetchedAtMillis in 0 until CACHE_MILLIS
    }

    private fun update(messageID: String, favoriteID: Int? = null, remove: Boolean = false) {
        val key = cacheKey() ?: return
        cache.compute(key) { _, latest ->
            val values = latest?.favoriteIDsByMessageID.orEmpty().toMutableMap()
            if (remove) values.remove(messageID) else values[messageID] = favoriteID
            CacheEntry(values, System.currentTimeMillis(), latest?.pendingMutations.orEmpty() +
                (messageID to if (remove) null else favoriteID))
        }
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
    fun recentIDsAfterRead(fetched: Map<String, Int?>, mutations: Map<String, Int?>): Map<String, Int?> =
        fetched.toMutableMap().apply {
            mutations.forEach { (messageID, favoriteID) ->
                if (favoriteID == null) remove(messageID) else put(messageID, favoriteID)
            }
        }

    fun canApplyPage(readMutation: Long, currentMutation: Long): Boolean = readMutation == currentMutation

    // Native SDK uses origin=0/thumb=1/large=2; REST TIMImageElem uses 1/3/2.
    fun canonicalImageType(sdkType: Int): Int? = when (sdkType) {
        com.tencent.imsdk.v2.V2TIMImageElem.V2TIM_IMAGE_TYPE_ORIGIN -> 1
        com.tencent.imsdk.v2.V2TIMImageElem.V2TIM_IMAGE_TYPE_THUMB -> 3
        com.tencent.imsdk.v2.V2TIMImageElem.V2TIM_IMAGE_TYPE_LARGE -> 2
        else -> null
    }

    fun imageAttachment(originalURL: String?, thumbnailURL: String?, largeURL: String?): Any {
        val original = originalURL?.takeIf(String::isNotBlank)
        val thumbnail = thumbnailURL?.takeIf(String::isNotBlank)
            ?: largeURL?.takeIf(String::isNotBlank)
            ?: original
        val imageInfo = buildList {
            original?.let { add(mapOf("Type" to 1, "URL" to it)) }
            thumbnail?.let { add(mapOf("Type" to 3, "URL" to it)) }
        }
        return if (imageInfo.isEmpty()) emptyList<Map<String, Any>>() else listOf(
            mapOf("MsgType" to "TIMImageElem", "MsgContent" to mapOf("ImageInfoArray" to imageInfo)),
        )
    }

    fun videoAttachment(snapshotURL: String?, videoURL: String?): Any {
        val content = mapOf(
            "ThumbUrl" to snapshotURL.orEmpty(),
            "VideoUrl" to videoURL.orEmpty(),
        ).filterValues(String::isNotBlank)
        return if (content.isEmpty()) emptyList<Map<String, Any>>() else listOf(
            mapOf("MsgType" to "TIMVideoFileElem", "MsgContent" to content),
        )
    }

    fun hasCompleteMedia(messageType: String, previewURL: String?, playbackURL: String?): Boolean =
        when (messageType.uppercase()) {
            "PICTURE" -> !previewURL.isNullOrBlank()
            "VIDEO" -> !playbackURL.isNullOrBlank()
            else -> true
        }

    fun matches(favorite: JsonObject, request: XingDunFavoriteMessageRequest): Boolean = runCatching {
        val message = favorite.getAsJsonObject("message") ?: favorite
        message.get("message_id")?.asString == request.messageId &&
            favorite.get("conversation_id")?.asString == request.conversationId &&
            message.get("sender")?.asString == request.senderId &&
            message.get("message_type")?.asString == request.messageType
    }.getOrDefault(false)

    fun validateResponse(favorite: JsonObject, request: XingDunFavoriteMessageRequest) {
        check(matches(favorite, request)) { "Favorite response identity mismatch" }
        check((favorite.get("favorite_id") ?: favorite.get("id"))?.asInt?.let { it > 0 } == true) {
            "Missing favorite identity"
        }
    }

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

internal sealed interface XingDunFavoriteConfirmation {
    data class Found(val favorite: JsonObject) : XingDunFavoriteConfirmation
    data object Absent : XingDunFavoriteConfirmation
    data object Unknown : XingDunFavoriteConfirmation
}

internal class XingDunFavoriteResultPending : Exception("Favorite result pending")

/** An uncertain POST is confirmed before a single idempotent retry; a failed GET never means absent. */
internal object XingDunFavoriteCreationRecovery {
    suspend fun save(
        create: suspend () -> JsonObject,
        confirm: suspend () -> XingDunFavoriteConfirmation,
        isCurrent: () -> Boolean,
    ): JsonObject {
        for (attempt in 0..1) {
            check(isCurrent()) { "Favorite session changed" }
            try { return create().also { check(isCurrent()) { "Favorite session changed" } } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is XingDunApiException &&
                    (error.httpStatus?.let { it in 400..499 } == true ||
                        error.businessCode?.let { it in 400..499 } == true)) throw error
                check(isCurrent()) { "Favorite session changed" }
                val outcome = try { confirm() } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { XingDunFavoriteConfirmation.Unknown }
                check(isCurrent()) { "Favorite session changed" }
                when (outcome) {
                    is XingDunFavoriteConfirmation.Found -> return outcome.favorite
                    XingDunFavoriteConfirmation.Unknown -> throw XingDunFavoriteResultPending()
                    XingDunFavoriteConfirmation.Absent -> if (attempt == 1) throw error
                }
            }
        }
        throw XingDunFavoriteResultPending()
    }
}
