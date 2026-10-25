package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

/** Correlates asynchronous media pages with one currently open preview. */
internal class MessageMediaDeletionGate(private val session: (() -> String?)? = null) {
    private var generation = 0L
    private var anchorID: String? = null
    private var displayedIDs: Set<String> = emptySet()
    private var sourceSession: String? = null
    fun begin(anchor: String): Long { invalidate(); anchorID = anchor; sourceSession = session?.invoke(); return generation }
    fun isCurrent(token: Long): Boolean = token == generation &&
        (session == null || (sourceSession != null && sourceSession == session.invoke()))
    fun token(): Long = generation
    fun bind(token: Long, ids: Set<String>) {
        if (isCurrent(token)) { anchorID = null; displayedIDs = ids }
    }
    fun shouldClose(isDeleted: (String) -> Boolean): Boolean =
        anchorID?.let(isDeleted) == true || displayedIDs.any(isDeleted)
    fun invalidate() { generation++; anchorID = null; displayedIDs = emptySet(); sourceSession = null }
}
