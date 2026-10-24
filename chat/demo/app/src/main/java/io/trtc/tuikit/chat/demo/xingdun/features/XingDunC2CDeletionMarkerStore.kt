package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context

/** Durable, account-scoped authoritative tombstones. The same store is used by the debug acceptance flow. */
internal class XingDunC2CDeletionMarkerStore(
    context: Context,
    private val account: () -> String?,
    private val onChanged: (String) -> Unit = {},
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "xingdun_c2c_authoritative_deletions_v1", Context.MODE_PRIVATE,
    )

    fun read(peer: String): Set<String> {
        if (!XingDunC2CDeletionPolicy.validID(peer)) return emptySet()
        val key = account() ?: return emptySet()
        return preferences.getStringSet("$key:$peer", emptySet()).orEmpty().toSet()
    }

    fun remember(requestedAccount: String, peer: String, ids: Set<String>) {
        require(XingDunC2CDeletionPolicy.validID(peer) && ids.all(XingDunC2CDeletionPolicy::validID))
        synchronized(preferences) {
            check(account() == requestedAccount) { "Session changed" }
            val key = "$requestedAccount:$peer"
            val previous = preferences.getStringSet(key, emptySet()).orEmpty().toSet()
            if (previous.containsAll(ids)) return
            check(preferences.edit().putStringSet(key, previous + ids).commit()) { "Deletion state was not persisted" }
        }
        onChanged("c2c_$peer")
    }
}
