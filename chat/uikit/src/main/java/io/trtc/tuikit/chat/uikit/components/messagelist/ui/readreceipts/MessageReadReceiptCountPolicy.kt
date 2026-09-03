package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts

internal object MessageReadReceiptCountPolicy {
    fun resolveTotal(reportedCount: Int?, loadedCount: Int): Int {
        return maxOf(reportedCount ?: 0, loadedCount, 0)
    }

    fun normalizeReadCount(readCount: Int): Int = maxOf(readCount, 0)

    fun shouldAutomaticallyRefresh(readCount: Int, unreadCount: Int): Boolean {
        return unreadCount > 0 || readCount == 0
    }
}
