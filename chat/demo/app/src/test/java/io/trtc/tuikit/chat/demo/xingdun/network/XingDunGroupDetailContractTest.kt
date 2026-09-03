package io.trtc.tuikit.chat.demo.xingdun.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunGroupDetailContractTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun businessDisplayIdWinsAndTencentInternalIdIsNeverExposed() {
        val detail = gson.fromJson(
            """{
                "group_id":"@TGS#tenant-private",
                "display_group_id":"100284",
                "name":"Project Group"
            }""".trimIndent(),
            XingDunGroupDetail::class.java,
        )
        assertEquals("100284", detail.publicGroupId)

        assertNull(detail.copy(displayGroupId = null).publicGroupId)
        assertNull(detail.copy(displayGroupId = "public-code").publicGroupId)
        assertEquals("100285", detail.copy(displayGroupId = null, groupId = "100285").publicGroupId)
    }

    @Test
    fun businessPolicyAllowsAllMembersManagersAndAssignedCustomerService() {
        val member = XingDunGroupDetail(currentUserRole = "member", updateTeamMode = 2)
        assertFalse(member.canEditGroupInfo)
        assertTrue(member.copy(updateTeamMode = 1).canEditGroupInfo)
        assertTrue(member.copy(currentUserRole = "administrator").canEditGroupInfo)
        assertTrue(member.copy(currentUserRole = "owner").canEditGroupInfo)
        assertTrue(member.copy(currentUserIsAssignedCs = true).canEditGroupInfo)
    }

    @Test
    fun announcementEditPermissionMatchesIOSOwnerAndAdministratorRule() {
        val member = XingDunGroupDetail(currentUserRole = "member", updateTeamMode = 1)
        assertFalse(member.canEditAnnouncement)
        assertFalse(member.copy(currentUserIsAssignedCs = true).canEditAnnouncement)
        assertTrue(member.copy(currentUserRole = "administrator").canEditAnnouncement)
        assertTrue(member.copy(currentUserRole = "owner").canEditAnnouncement)
    }

    @Test
    fun managementPolicyAndDangerPermissionsMatchIOSRules() {
        val member = XingDunGroupDetail(
            currentUserRole = "member",
            groupType = "Public",
            muteAllLevel = 2,
        )
        assertFalse(member.canEditManagement)
        assertFalse(member.canManagePolicies)
        assertFalse(member.canSetMuteAll)
        assertTrue(member.supportsJoinMode)
        assertTrue(member.canLeave)
        assertFalse(member.canDismiss)

        val administrator = member.copy(currentUserRole = "administrator", muteAllLevel = 1)
        assertTrue(administrator.canEditManagement)
        assertFalse(administrator.canManagePolicies)
        assertTrue(administrator.canSetMuteAll)

        val assignedCustomerService = administrator.copy(currentUserIsAssignedCs = true, muteAllLevel = 2)
        assertTrue(assignedCustomerService.canManagePolicies)
        assertTrue(assignedCustomerService.canSetMuteAll)
        assertFalse(assignedCustomerService.canLeave)

        val owner = member.copy(currentUserRole = "owner", muteAllLevel = 0)
        assertTrue(owner.canManagePolicies)
        assertFalse(owner.canLeave)
        assertTrue(owner.canDismiss)
        assertFalse(owner.copy(isOfficial = true).canDismiss)
        assertFalse(owner.copy(isCustomerService = true).canDismiss)
    }

    @Test
    fun managementFieldsDecodeFromTenantContract() {
        val detail = gson.fromJson(
            """{
                "group_id":"@TGS#group",
                "group_type":"Public",
                "join_mode":1,
                "invite_mode":2,
                "update_team_mode":1,
                "at_all_mode":2,
                "be_invite_mode":2,
                "view_member_card_mode":1,
                "pin_message_mode":2,
                "mute_all":true,
                "mute_all_level":2,
                "is_official":false,
                "is_customer_service":true,
                "current_user_is_assigned_cs":true
            }""".trimIndent(),
            XingDunGroupDetail::class.java,
        )
        assertEquals(1, detail.joinMode)
        assertEquals(2, detail.inviteMode)
        assertEquals(2, detail.beInviteMode)
        assertTrue(detail.muteAll)
        assertEquals(2, detail.muteAllLevel)
        assertTrue(detail.isCustomerService)
        assertTrue(detail.currentUserIsAssignedCs)
    }

    @Test
    fun composerPermissionMatchesIOSMuteLevels() {
        val member = XingDunGroupDetail(currentUserRole = "member")
        assertTrue(member.canSendMessages)
        assertFalse(member.copy(muteAll = true, muteAllLevel = 1).canSendMessages)

        val owner = member.copy(currentUserRole = "owner", muteAll = true, muteAllLevel = 1)
        val administrator = member.copy(currentUserRole = "administrator", muteAll = true, muteAllLevel = 1)
        assertTrue(owner.canSendMessages)
        assertTrue(administrator.canSendMessages)

        assertFalse(owner.copy(muteAllLevel = 2).canSendMessages)
        assertFalse(administrator.copy(muteAllLevel = 2).canSendMessages)
        assertTrue(member.copy(muteAll = true, muteAllLevel = 2, currentUserIsAssignedCs = true).canSendMessages)
    }
}
