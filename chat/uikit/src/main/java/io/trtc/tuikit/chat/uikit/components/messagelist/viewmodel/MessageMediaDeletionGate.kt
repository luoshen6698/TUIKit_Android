package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

/** Correlates asynchronous media pages with one currently open preview. */
internal class MessageMediaDeletionGate {
    private var generation = 0L
    private var anchorID: String? = null
    private var displayedIDs: Set<String> = emptySet()
    fun begin(anchor: String): Long { invalidate(); anchorID = anchor; return generation }
    fun isCurrent(token: Long): Boolean = token == generation
    fun token(): Long = generation
    fun bind(token: Long, ids: Set<String>) {
        if (isCurrent(token)) { anchorID = null; displayedIDs = ids }
    }
    fun shouldClose(isDeleted: (String) -> Boolean): Boolean =
        anchorID?.let(isDeleted) == true || displayedIDs.any(isDeleted)
    fun invalidate() { generation++; anchorID = null; displayedIDs = emptySet() }
}
