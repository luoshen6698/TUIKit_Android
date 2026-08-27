package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAutoDeleteConfiguration
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/** Small tenant-bound cache around the existing shared auto-delete contract. */
internal object XingDunAutoDeleteRepository {
    private const val CACHE_MILLIS = 5 * 60 * 1000L

    private data class CacheEntry(
        val configuration: XingDunAutoDeleteConfiguration,
        val fetchedAtMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val listeners = CopyOnWriteArraySet<(String, XingDunAutoDeleteConfiguration) -> Unit>()

    fun cached(conversationID: String): XingDunAutoDeleteConfiguration? {
        val key = cacheKey(conversationID) ?: return null
        val entry = cache[key] ?: return null
        return entry.configuration.takeIf { System.currentTimeMillis() - entry.fetchedAtMillis in 0 until CACHE_MILLIS }
    }

    suspend fun load(conversationID: String, force: Boolean = false): XingDunAutoDeleteConfiguration {
        if (!force) cached(conversationID)?.let { return it }
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val configuration = XingDunSessionManager.apiClient().get<XingDunAutoDeleteConfiguration>(
            session = session,
            path = "message/auto-delete",
            query = mapOf("conversation_id" to conversationID),
            responseType = XingDunAutoDeleteConfiguration::class.java
        )
        return store(conversationID, configuration)
    }

    suspend fun update(conversationID: String, ttlSeconds: Int): XingDunAutoDeleteConfiguration {
        require(ttlSeconds in XingDunAutoDeletePolicy.DEFAULT_TTL_SECONDS)
        val session = XingDunSessionManager.currentSession() ?: error("Missing session")
        val configuration = XingDunSessionManager.apiClient().post<XingDunAutoDeleteConfiguration>(
            session = session,
            path = "message/auto-delete",
            body = mapOf("conversation_id" to conversationID, "ttl_seconds" to ttlSeconds),
            responseType = XingDunAutoDeleteConfiguration::class.java
        )
        return store(conversationID, configuration)
    }

    fun applyRemote(conversationID: String, values: Map<String, String>) {
        val ttlSeconds = values["ttl_seconds"]?.toIntOrNull() ?: return
        val version = values["version"]?.toIntOrNull() ?: return
        if (ttlSeconds !in XingDunAutoDeletePolicy.DEFAULT_TTL_SECONDS) return
        val key = cacheKey(conversationID) ?: return
        val current = cache[key]?.configuration
        if (!XingDunAutoDeletePolicy.shouldApplyRemote(current?.version, version)) return
        store(
            conversationID,
            XingDunAutoDeleteConfiguration(
                conversationId = conversationID,
                ttlSeconds = ttlSeconds,
                enabled = ttlSeconds > 0,
                version = version,
                updatedAt = values["updated_at"],
                updatedBy = values["updated_by"] ?: values["operator"],
                allowedTtlSeconds = XingDunAutoDeletePolicy.DEFAULT_TTL_SECONDS
            )
        )
    }

    fun addListener(listener: (String, XingDunAutoDeleteConfiguration) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (String, XingDunAutoDeleteConfiguration) -> Unit) {
        listeners -= listener
    }

    fun clearTenantCache() {
        cache.clear()
    }

    private fun store(
        requestedConversationID: String,
        configuration: XingDunAutoDeleteConfiguration
    ): XingDunAutoDeleteConfiguration {
        val conversationID = configuration.conversationId.ifBlank { requestedConversationID }
        val key = cacheKey(conversationID) ?: return configuration
        val normalized = configuration.copy(
            conversationId = conversationID,
            allowedTtlSeconds = XingDunAutoDeletePolicy.normalizedOptions(configuration.allowedTtlSeconds)
        )
        val current = cache[key]?.configuration
        if (current != null && current.version > normalized.version) return current
        cache[key] = CacheEntry(normalized, System.currentTimeMillis())
        listeners.forEach { it(conversationID, normalized) }
        return normalized
    }

    private fun cacheKey(conversationID: String): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenantKey = XingDunTenantBoundary.identity(session)?.key ?: return null
        return "$tenantKey:$conversationID"
    }
}
