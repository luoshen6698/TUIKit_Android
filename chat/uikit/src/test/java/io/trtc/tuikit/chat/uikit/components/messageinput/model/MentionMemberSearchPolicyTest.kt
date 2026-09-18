package io.trtc.tuikit.chat.uikit.components.messageinput.model

import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionMemberSearchPolicyTest {
    private val members = listOf(
        member(
            userID = "account-001",
            nickname = "Alice",
            friendRemark = "Project Lead",
            nameCard = "Team Owner"
        ),
        member(
            userID = "b002",
            nickname = "Bob",
            friendRemark = "",
            nameCard = "Product"
        )
    )

    @Test
    fun emptyQueryKeepsOriginalOrder() {
        assertEquals(members, MentionMemberSearchPolicy.filter(members, "   "))
    }

    @Test
    fun matchesAccountCaseInsensitively() {
        assertEquals(
            listOf("account-001"),
            MentionMemberSearchPolicy.filter(members, "ACCOUNT").map { it.userID }
        )
    }

    @Test
    fun matchesNameCardRemarkAndNickname() {
        assertEquals(
            listOf("account-001"),
            MentionMemberSearchPolicy.filter(members, "owner").map { it.userID }
        )
        assertEquals(
            listOf("account-001"),
            MentionMemberSearchPolicy.filter(members, "lead").map { it.userID }
        )
        assertEquals(
            listOf("b002"),
            MentionMemberSearchPolicy.filter(members, "bob").map { it.userID }
        )
    }

    @Test
    fun trimsQueryBeforeMatching() {
        assertEquals(
            listOf("b002"),
            MentionMemberSearchPolicy.filter(members, "  product  ").map { it.userID }
        )
    }

    @Test
    fun mergingFilteredSelectionKeepsHiddenMembersSelected() {
        assertEquals(
            linkedSetOf("hidden-001", "visible-002"),
            MentionMemberSearchPolicy.mergeSelectedIDs(
                currentSelectedIDs = listOf("hidden-001", "visible-001"),
                visibleMemberIDs = listOf("visible-001", "visible-002"),
                visibleSelectedIDs = listOf("visible-002")
            )
        )
    }

    @Test
    fun mergingFilteredSelectionCanDeselectVisibleMember() {
        assertEquals(
            linkedSetOf("hidden-001"),
            MentionMemberSearchPolicy.mergeSelectedIDs(
                currentSelectedIDs = listOf("hidden-001", "visible-001"),
                visibleMemberIDs = listOf("visible-001"),
                visibleSelectedIDs = emptyList()
            )
        )
    }

    private fun member(
        userID: String,
        nickname: String,
        friendRemark: String,
        nameCard: String
    ): GroupMember {
        return GroupMember(
            userID = userID,
            nickname = nickname,
            friendRemark = friendRemark,
            nameCard = nameCard,
            avatarURL = "",
            role = GroupMemberRole.MEMBER,
            muteUntil = 0L
        )
    }
}
