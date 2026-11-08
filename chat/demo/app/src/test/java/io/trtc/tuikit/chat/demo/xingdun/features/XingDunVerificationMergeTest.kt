package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity.ApplicationUser
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity.Direction
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity.FriendApplication
import org.junit.Assert.*
import org.junit.Test

class XingDunVerificationMergeTest {
    private fun server(id: Int, status: Int, peer: String = "peer", direction: Direction = Direction.RECEIVED) =
        FriendApplication(id = id, status = status, createdAt = "2026-11-08 12:00:00", direction = direction,
            fromUser = ApplicationUser(timUserId = peer), toUser = ApplicationUser(timUserId = peer))
    private fun native(peer: String = "peer", direction: Direction = Direction.RECEIVED) =
        server(0, 0, peer, direction).copy(nativeUserID = peer, createdAt = null)
    private fun merge(server: List<FriendApplication>, native: List<FriendApplication>) =
        XingDunVerificationMessagesActivity.mergeFriendApplications(server, native)

    @Test fun sdkOnlyRequestAppearsAbovePreviouslyAcceptedHistory() {
        val rows = merge(listOf(server(62, 1)), listOf(native()))
        assertEquals(listOf(0, 62), rows.map { it.id })
        assertEquals("peer", rows.first().nativeUserID)
        assertEquals(Direction.RECEIVED, rows.first().direction)
    }
    @Test fun samePendingRequestUsesBusinessIdForActionsWithoutDuplicateSdkRow() {
        val rows = merge(listOf(server(111, 0)), listOf(native(), native()))
        assertEquals(1, rows.size)
        assertEquals(111, rows.single().id)
        assertNull(rows.single().nativeUserID)
    }
    @Test fun oppositeDirectionsAndDifferentPeersAreNotDropped() {
        val rows = merge(listOf(server(111, 0)), listOf(native(direction = Direction.SENT), native("other")))
        assertEquals(3, rows.size)
    }
    @Test fun incomingRequestsAppearBeforeOutgoingPendingRequests() {
        val rows = merge(emptyList(), listOf(native("sent", Direction.SENT), native("received")))
        assertEquals("received", rows.first().nativeUserID)
    }
    @Test fun completedHistoryRemainsVisibleAfterSdkPendingDisappears() {
        assertEquals(listOf(112, 62), merge(listOf(server(62, 1), server(112, 2)), emptyList()).map { it.id })
    }
}
