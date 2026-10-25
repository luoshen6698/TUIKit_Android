package io.trtc.tuikit.chat.demo.xingdun.features

import java.util.concurrent.ConcurrentHashMap

/** Process-only negative checks and bounded hints. Durable positive records are deliberately owned elsewhere. */
internal class XingDunGroupDeletionVisibilityState(private val clock: () -> Long = System::currentTimeMillis) {
    private val checked = ConcurrentHashMap<String, MutableSet<String>>()
    private val liveSending = ConcurrentHashMap<String, MutableSet<String>>()
    private val pending = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    fun checked(scope: String, ids: Set<String>, requestStartedAt: Long = clock()): Boolean {
        var changed = checked.getOrPut(scope) { ConcurrentHashMap.newKeySet() }.addAll(ids)
        // A pre-commit negative reply, or any failed read, must never release a hinted target.
        ids.forEach { id ->
            val deadline = pending[scope]?.get(id)
            if (deadline != null && requestStartedAt >= deadline && pending[scope]?.remove(id, deadline) == true) changed = true
        }
        return changed
    }
    fun isChecked(scope: String, id: String) = id in checked[scope].orEmpty()
    fun hold(scope: String, ids: Set<String>) {
        val targets = pending.getOrPut(scope) { ConcurrentHashMap() }
        val deadline = clock() + 11_000
        ids.forEach { id -> targets.merge(id, deadline, ::maxOf) }
    }
    fun isPending(scope: String, id: String) = pending[scope]?.containsKey(id) == true

    fun canDisplay(scope: String, id: String, deleted: Boolean, ownSending: Boolean, ownFailed: Boolean): Boolean {
        if (deleted || isPending(scope, id)) return false
        if (ownSending) liveSending.getOrPut(scope) { ConcurrentHashMap.newKeySet() }.add(id)
        if (ownFailed) liveSending[scope]?.remove(id)
        return isChecked(scope, id) || id in liveSending[scope].orEmpty() || ownFailed
    }
    fun clear() { checked.clear(); liveSending.clear(); pending.clear() }
}
