package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunGroupMemberPolicyTest {

    @Test
    fun `member-card policy follows iOS role and tenant detail rules`() {
        val target = member("target")
        assertFalse(XingDunGroupMemberPolicy.canViewCard(detail(), target, "self"))
        assertTrue(XingDunGroupMemberPolicy.canViewCard(detail(viewMode = 1), target, "self"))
        assertTrue(XingDunGroupMemberPolicy.canViewCard(detail(role = "administrator"), target, "self"))
        assertTrue(XingDunGroupMemberPolicy.canViewCard(detail(assignedCs = true), target, "self"))
        assertTrue(XingDunGroupMemberPolicy.canViewCard(detail(), member("self"), "self"))
    }

    @Test
    fun `owner and administrator removal rules follow iOS group-type behavior`() {
        val normal = member("normal")
        val admin = member("admin", "administrator")
        val owner = member("owner", "owner")

        assertTrue(XingDunGroupMemberPolicy.canRemove(detail(role = "owner"), normal, "self"))
        assertTrue(XingDunGroupMemberPolicy.canRemove(detail(role = "owner"), admin, "self"))
        assertFalse(XingDunGroupMemberPolicy.canRemove(detail(role = "administrator"), admin, "self"))
        assertTrue(XingDunGroupMemberPolicy.canRemove(detail(role = "administrator"), normal, "self"))
        assertFalse(XingDunGroupMemberPolicy.canRemove(detail(role = "administrator", type = "Work"), normal, "self"))
        assertTrue(XingDunGroupMemberPolicy.canRemove(detail(role = "administrator", type = "Work", assignedCs = true), normal, "self"))
        assertFalse(XingDunGroupMemberPolicy.canRemove(detail(role = "owner"), owner, "self"))
        assertFalse(XingDunGroupMemberPolicy.canRemove(detail(role = "owner"), member("self"), "self"))
    }

    @Test
    fun `only current owner can enter transfer flow`() {
        assertTrue(XingDunGroupMemberPolicy.canTransferOwner(detail(role = "owner")))
        assertFalse(XingDunGroupMemberPolicy.canTransferOwner(detail(role = "administrator", assignedCs = true)))
        assertFalse(XingDunGroupMemberPolicy.canTransferOwner(detail(role = "member")))
    }

    @Test
    fun `invite policy follows tenant mode and privileged roles`() {
        assertFalse(XingDunGroupMemberPolicy.canInvite(detail(role = "member", inviteMode = 2)))
        assertTrue(XingDunGroupMemberPolicy.canInvite(detail(role = "member", inviteMode = 1)))
        assertTrue(XingDunGroupMemberPolicy.canInvite(detail(role = "administrator", inviteMode = 2)))
        assertTrue(XingDunGroupMemberPolicy.canInvite(detail(role = "owner", inviteMode = 2)))
        assertTrue(XingDunGroupMemberPolicy.canInvite(detail(role = "member", inviteMode = 2, assignedCs = true)))
    }

    @Test
    fun `mute policy only allows operators to mute ordinary members`() {
        val normal = member("normal")
        assertTrue(XingDunGroupMemberPolicy.canMute(detail(role = "owner"), normal, "self"))
        assertTrue(XingDunGroupMemberPolicy.canMute(detail(role = "administrator"), normal, "self"))
        assertFalse(XingDunGroupMemberPolicy.canMute(detail(role = "member"), normal, "self"))
        assertFalse(XingDunGroupMemberPolicy.canMute(detail(role = "owner"), member("admin", "administrator"), "self"))
        assertFalse(XingDunGroupMemberPolicy.canMute(detail(role = "owner"), member("self"), "self"))
    }

    @Test
    fun `administrator changes remain owner-only and exclude work groups`() {
        val normal = member("normal")
        val admin = member("admin", "administrator")
        assertTrue(XingDunGroupMemberPolicy.canChangeAdministrator(detail(role = "owner"), normal, "self"))
        assertTrue(XingDunGroupMemberPolicy.canChangeAdministrator(detail(role = "owner"), admin, "self"))
        assertFalse(XingDunGroupMemberPolicy.canChangeAdministrator(detail(role = "administrator"), normal, "self"))
        assertFalse(XingDunGroupMemberPolicy.canChangeAdministrator(detail(role = "owner", type = "Work"), normal, "self"))
        assertFalse(XingDunGroupMemberPolicy.canChangeAdministrator(detail(role = "owner"), member("owner", "owner"), "self"))
        assertFalse(XingDunGroupMemberPolicy.canChangeAdministrator(detail(role = "owner"), member("self"), "self"))
    }

    private fun detail(
        role: String = "member",
        type: String = "Public",
        viewMode: Int = 2,
        inviteMode: Int = 2,
        assignedCs: Boolean = false,
    ) = XingDunGroupDetail(
        groupId = "group-1",
        ownerUserId = "owner",
        currentUserRole = role,
        groupType = type,
        viewMemberCardMode = viewMode,
        inviteMode = inviteMode,
        currentUserIsAssignedCs = assignedCs,
    )

    private fun member(userID: String, role: String = "member") = XingDunGroupMember(
        userId = userID,
        nickname = userID,
        role = role,
    )
}
