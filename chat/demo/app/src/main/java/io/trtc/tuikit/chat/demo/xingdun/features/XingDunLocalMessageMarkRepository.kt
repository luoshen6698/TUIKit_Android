package io.trtc.tuikit.chat.demo.xingdun.features

import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import java.security.MessageDigest

internal object XingDunLocalMessageMarkRepository {
    fun isMarked(conversationID: String, messageID: String): Boolean =
        messageID.isNotBlank() && markedIDs(conversationID).contains(messageID)

    fun toggle(conversationID: String, messageID: String): Boolean {
        if (messageID.isBlank()) return false
        val key = storageKey(conversationID) ?: return false
        val updated = markedIDs(conversationID).toMutableSet()
        val marked = if (updated.remove(messageID)) false else {
            updated.add(messageID)
            true
        }
        MMKV.defaultMMKV().encode(key, updated)
        return marked
    }

    private fun markedIDs(conversationID: String): Set<String> {
        val key = storageKey(conversationID) ?: return emptySet()
        return MMKV.defaultMMKV().decodeStringSet(key, emptySet())?.toSet().orEmpty()
    }

    private fun storageKey(conversationID: String): String? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenant = XingDunTenantBoundary.identity(session)?.key ?: return null
        return scopeKey(tenant, session.timUserId, conversationID)
    }

    internal fun scopeKey(tenantKey: String, userID: String, conversationID: String): String {
        val source = "$tenantKey|$userID|$conversationID"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "xingdun.message.mark.v1.$digest"
    }
}
