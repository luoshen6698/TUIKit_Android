package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import com.google.gson.Gson
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessage
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessagePage
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessageSnapshot
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared pin state. Group pins are server-backed; direct pins are account-local like iOS. */
internal object XingDunPinnedMessageRepository {
    private const val CACHE_MILLIS = 30_000L
    private const val PREFERENCES = "xingdun_direct_pinned_messages"
    private const val READ_PREFERENCES = "xingdun_pinned_message_read_tokens"
    private val gson = Gson()
    private val loadMutex = Mutex()
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    private data class CacheEntry(val page: XingDunPinnedMessagePage, val fetchedAtMillis: Long)

    fun cached(conversationID: String): XingDunPinnedMessagePage? {
        val key = cacheKey(conversationID) ?: return null
        val value = cache[key] ?: return null
        return value.page.takeIf { System.currentTimeMillis() - value.fetchedAtMillis in 0 until CACHE_MILLIS }
            ?.let { visibleGroupPage(conversationID, it) }
    }

    suspend fun load(context: Context, conversationID: String, force: Boolean = false): XingDunPinnedMessagePage {
        if (conversationID.startsWith("c2c_")) {
            val account = XingDunC2CDeletionRepository.accountKey()
            val pin = directPin(context, conversationID)
            if (pin != null && XingDunC2CDeletionPolicy.validID(pin.messageId)) {
                try { XingDunC2CDeletionRepository.check(context, conversationID, setOf(pin.messageId)) }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: Exception) { /* Known tombstones still filter the persisted snapshot offline. */ }
            }
            return if (account == XingDunC2CDeletionRepository.accountKey()) directPage(context, conversationID)
                else XingDunPinnedMessagePage()
        }
        if (!force) cached(conversationID)?.let { return it }
        return loadMutex.withLock {
            if (!force) cached(conversationID)?.let { return@withLock it }
            val session = XingDunSessionManager.currentSession() ?: error("Missing session")
            val account = XingDunC2CDeletionRepository.accountKey()
            val generation = XingDunSessionManager.currentAuthenticationGeneration()
            val page = XingDunSessionManager.apiClient().get<XingDunPinnedMessagePage>(
                session,
                "message/pins",
                mapOf("conversation_id" to conversationID, "page" to "1", "page_size" to "100"),
                XingDunPinnedMessagePage::class.java,
            )
            check(account == XingDunC2CDeletionRepository.accountKey() && generation == XingDunSessionManager.currentAuthenticationGeneration()) { "Authentication instance changed" }
            if (XingDunGroupDeletionPolicy.group(conversationID) != null) {
                page.items.map { it.messageId }.filter(XingDunGroupDeletionPolicy::validMessage).chunked(100).forEach {
                    check(account == XingDunC2CDeletionRepository.accountKey() && generation == XingDunSessionManager.currentAuthenticationGeneration()) { "Authentication instance changed" }
                    XingDunGroupDeletionRepository.check(context, conversationID, it.toSet())
                }
            }
            check(account == XingDunC2CDeletionRepository.accountKey() && generation == XingDunSessionManager.currentAuthenticationGeneration()) { "Authentication instance changed" }
            store(conversationID, visibleGroupPage(conversationID, page))
        }
    }

    suspend fun pinGroup(
        conversationID: String,
        messageID: String,
        groupID: String,
        messageSequence: Long?,
    ): XingDunPinnedMessage {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val pin = XingDunSessionManager.apiClient().post<XingDunPinnedMessage>(
            session,
            "message/pin",
            mapOf(
                "message_id" to messageID,
                "group_id" to groupID,
                "message_sequence" to (messageSequence ?: 0L),
            ),
            XingDunPinnedMessage::class.java,
        ).let { value ->
            if (value.conversationId.isBlank()) value.copy(conversationId = conversationID) else value
        }
        merge(conversationID, pin)
        return pin
    }

    suspend fun unpinGroup(conversationID: String, messageID: String): XingDunPinnedMessage {
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val pin = XingDunSessionManager.apiClient().post<XingDunPinnedMessage>(
            session,
            "message/unpin",
            mapOf("message_id" to messageID),
            XingDunPinnedMessage::class.java,
        ).let { value ->
            if (value.conversationId.isBlank()) value.copy(conversationId = conversationID) else value
        }
        merge(conversationID, pin)
        return pin
    }

    fun toggleDirect(
        context: Context,
        conversationID: String,
        messageID: String,
        messageSequence: Long?,
        senderID: String?,
        senderNickname: String?,
        messageType: String,
        text: String,
    ): Boolean {
        val current = directPin(context, conversationID)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = directPreferenceKey(conversationID) ?: return false
        val nowPinned = current?.messageId != messageID
        if (nowPinned) {
            val session = XingDunSessionManager.currentSession() ?: return false
            val pin = XingDunPinnedMessage(
                messageId = messageID,
                conversationId = conversationID,
                isPinned = true,
                version = (current?.version ?: 0) + 1,
                operator = session.timUserId,
                operatorNickname = session.nickname,
                messageSequence = messageSequence,
                message = XingDunPinnedMessageSnapshot(
                    messageId = messageID,
                    sender = senderID,
                    senderNickname = senderNickname,
                    messageType = messageType,
                    text = text,
                ),
            )
            preferences.edit().putString(key, gson.toJson(pin)).apply()
        } else {
            preferences.edit().remove(key).apply()
        }
        cacheKey(conversationID)?.let(cache::remove)
        notifyChanged(conversationID)
        return nowPinned
    }

    fun unreadPins(context: Context, conversationID: String, items: List<XingDunPinnedMessage>): List<XingDunPinnedMessage> {
        val tokens = readTokens(context, conversationID)
        val deleted = if (conversationID.startsWith("c2c_")) XingDunC2CDeletionRepository.deletedIDs(context, conversationID)
            else items.filter { XingDunGroupDeletionRepository.isHidden(conversationID, it.messageId) }.map { it.messageId }.toSet()
        return XingDunPinnedMessagePolicy.visiblePins(items, deleted).filterNot { XingDunPinnedMessagePolicy.readToken(it) in tokens }
    }

    fun markRead(context: Context, pin: XingDunPinnedMessage) {
        val key = readPreferenceKey(pin.conversationId) ?: return
        val preferences = context.getSharedPreferences(READ_PREFERENCES, Context.MODE_PRIVATE)
        val tokens = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (tokens.add(XingDunPinnedMessagePolicy.readToken(pin))) {
            preferences.edit().putStringSet(key, tokens).apply()
            notifyChanged(pin.conversationId)
        }
    }

    fun directPin(context: Context, conversationID: String): XingDunPinnedMessage? {
        val key = directPreferenceKey(conversationID) ?: return null
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(key, null) ?: return null
        return runCatching { gson.fromJson(raw, XingDunPinnedMessage::class.java) }
            .getOrNull()
            ?.takeIf { it.conversationId == conversationID && it.isPinned && it.messageId.isNotBlank() }
            ?.takeUnless { it.messageId in XingDunC2CDeletionRepository.deletedIDs(context, conversationID) }
    }

    fun isPinned(context: Context, conversationID: String, messageID: String): Boolean =
        if (conversationID.startsWith("c2c_")) directPin(context, conversationID)?.messageId == messageID
        else currentItems(conversationID).any { it.messageId == messageID && it.isPinned }

    fun applyRemote(conversationID: String, values: Map<String, String>) {
        val messageID = values["message_id"]?.trim()?.takeIf(String::isNotEmpty) ?: return
        val version = values["version"]?.toIntOrNull() ?: return
        val action = values["action"]?.lowercase() ?: return
        if (action !in setOf("pin", "unpin")) return
        val key = cacheKey(conversationID) ?: return
        val currentPage = cache[key]?.page
        val currentVersion = currentPage?.items?.firstOrNull { it.messageId == messageID }?.version
        if (!XingDunPinnedMessagePolicy.shouldApply(currentVersion, version)) return
        cache.remove(key)
        notifyChanged(conversationID)
    }

    fun addListener(listener: (String) -> Unit) { listeners += listener }
    fun removeListener(listener: (String) -> Unit) { listeners -= listener }

    fun clearTenantCache() {
        cache.clear()
    }

    fun onC2CDeletion(conversationID: String) {
        if (!conversationID.startsWith("c2c_")) return
        cacheKey(conversationID)?.let(cache::remove)
        notifyChanged(conversationID)
    }

    fun cachedIDs(conversationID: String): Set<String> = currentItems(conversationID).map { it.messageId }.toSet()
    fun onGroupVisibilityChanged(conversationID: String) { notifyChanged(conversationID) }

    fun onGroupDeletion(conversationID: String) {
        if (XingDunGroupDeletionPolicy.group(conversationID) == null) return
        cacheKey(conversationID)?.let { key -> cache[key]?.let { old -> cache[key] = old.copy(page = visibleGroupPage(conversationID, old.page)) } }
        notifyChanged(conversationID)
    }

    private fun visibleGroupPage(conversationID: String, page: XingDunPinnedMessagePage): XingDunPinnedMessagePage {
        if (XingDunGroupDeletionPolicy.group(conversationID) == null) return page
        val items = page.items.filterNot { XingDunGroupDeletionRepository.isHidden(conversationID, it.messageId) }
        return page.copy(items = items, total = items.size)
    }

    private fun directPage(context: Context, conversationID: String): XingDunPinnedMessagePage {
        val items = listOfNotNull(directPin(context, conversationID))
        return XingDunPinnedMessagePage(items = items, total = items.size)
    }

    private fun currentItems(conversationID: String): List<XingDunPinnedMessage> =
        cacheKey(conversationID)?.let(cache::get)?.page?.items.orEmpty()

    private fun merge(conversationID: String, value: XingDunPinnedMessage) {
        val key = cacheKey(conversationID) ?: return
        val existing = cache[key]?.page ?: XingDunPinnedMessagePage()
        val items = existing.items.filterNot { it.messageId == value.messageId }.toMutableList()
        if (value.isPinned) items.add(0, value)
        store(conversationID, existing.copy(items = items, total = items.size))
        notifyChanged(conversationID)
    }

    private fun store(conversationID: String, page: XingDunPinnedMessagePage): XingDunPinnedMessagePage {
        val key = cacheKey(conversationID) ?: return page
        val normalized = page.copy(items = XingDunPinnedMessagePolicy.visiblePins(page.items), total = page.items.count { it.isPinned })
        cache[key] = CacheEntry(normalized, System.currentTimeMillis())
        return normalized
    }

    private fun notifyChanged(conversationID: String) = listeners.forEach { it(conversationID) }

    private fun directPreferenceKey(conversationID: String): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenant = XingDunTenantBoundary.identity(session)?.key ?: return null
        return "$tenant:${session.timUserId}:$conversationID"
    }

    private fun readTokens(context: Context, conversationID: String): Set<String> {
        val key = readPreferenceKey(conversationID) ?: return emptySet()
        return context.getSharedPreferences(READ_PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(key, emptySet())
            .orEmpty()
    }

    private fun readPreferenceKey(conversationID: String): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenant = XingDunTenantBoundary.identity(session)?.key ?: return null
        return "$tenant:${session.timUserId}:$conversationID"
    }

    private fun cacheKey(conversationID: String): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenant = XingDunTenantBoundary.identity(session)?.key ?: return null
        return "$tenant:${session.timUserId}:$conversationID"
    }
}
