package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReadReceiptCountPolicyTest {
    @Test
    fun reportedTotalIsUsedWhenOnlyFirstPageIsLoaded() {
        assertEquals(42, MessageReadReceiptCountPolicy.resolveTotal(reportedCount = 42, loadedCount = 20))
    }

    @Test
    fun loadedCountWinsWhenReportedTotalIsStale() {
        assertEquals(20, MessageReadReceiptCountPolicy.resolveTotal(reportedCount = 10, loadedCount = 20))
    }

    @Test
    fun countCannotBeNegative() {
        assertEquals(0, MessageReadReceiptCountPolicy.normalizeReadCount(-1))
        assertEquals(0, MessageReadReceiptCountPolicy.resolveTotal(reportedCount = -1, loadedCount = 0))
    }

    @Test
    fun refreshContinuesUntilAReceiptExistsAndNobodyIsUnread() {
        assertTrue(MessageReadReceiptCountPolicy.shouldAutomaticallyRefresh(readCount = 0, unreadCount = 0))
        assertTrue(MessageReadReceiptCountPolicy.shouldAutomaticallyRefresh(readCount = 1, unreadCount = 2))
        assertFalse(MessageReadReceiptCountPolicy.shouldAutomaticallyRefresh(readCount = 3, unreadCount = 0))
    }
}
