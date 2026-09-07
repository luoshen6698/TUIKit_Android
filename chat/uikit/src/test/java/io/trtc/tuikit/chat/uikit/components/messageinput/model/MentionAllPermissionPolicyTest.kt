package io.trtc.tuikit.chat.uikit.components.messageinput.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionAllPermissionPolicyTest {
    @Test
    fun restrictedMemberDoesNotSeeMentionAllButCanStillMentionMembers() {
        assertFalse(MentionAllPermissionPolicy.shouldShow(canMentionAll = false))
        assertTrue(
            MentionAllPermissionPolicy.canSend(
                canMentionAll = false,
                mentions = listOf(MentionInfo(userID = "member-1", displayName = "Member")),
            )
        )
    }

    @Test
    fun staleMentionAllSelectionIsBlockedAfterPermissionBecomesRestricted() {
        val mentionAll = MentionInfo(
            userID = MentionInfo.AT_ALL_USER_ID,
            displayName = "All",
        )
        assertFalse(
            MentionAllPermissionPolicy.canSend(
                canMentionAll = false,
                mentions = listOf(mentionAll),
            )
        )
        assertTrue(
            MentionAllPermissionPolicy.canSend(
                canMentionAll = true,
                mentions = listOf(mentionAll),
            )
        )
    }
}
