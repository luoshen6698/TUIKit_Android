package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting

import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermissionManager
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatSettingUiStateTest {

    @Test
    fun `public group owner can see transfer ownership entry`() {
        assertTrue(state(GroupType.PUBLIC_GROUP, GroupMemberRole.OWNER).permissions.canTransferOwner)
    }

    @Test
    fun `public group admin and member cannot see transfer ownership entry`() {
        assertFalse(state(GroupType.PUBLIC_GROUP, GroupMemberRole.ADMIN).permissions.canTransferOwner)
        assertFalse(state(GroupType.PUBLIC_GROUP, GroupMemberRole.MEMBER).permissions.canTransferOwner)
    }

    @Test
    fun `unsupported group type hides transfer ownership entry from owner`() {
        assertFalse(state(GroupType.AV_CHAT_ROOM, GroupMemberRole.OWNER).permissions.canTransferOwner)
    }

    private fun state(groupType: GroupType, role: GroupMemberRole): GroupChatSettingUiState {
        return createGroupChatSettingUiState(
            groupID = "group-a",
            groupName = "Group A",
            avatarURL = "",
            notification = "",
            groupType = groupType,
            selfRole = role,
            joinOption = GroupJoinOption.AUTH,
            inviteOption = GroupInviteOption.ANY,
            nameCard = null,
            memberCount = 0,
            groupMembers = emptyList(),
            isNotDisturb = false,
            isPinned = false,
            chatBackgroundImageUri = null,
            canPerformAction = GroupPermissionManager::canPerformAction,
        )
    }
}
