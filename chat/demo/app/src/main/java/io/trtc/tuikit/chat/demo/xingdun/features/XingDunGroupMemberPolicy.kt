package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember

internal object XingDunGroupMemberPolicy {

    fun canInvite(detail: XingDunGroupDetail): Boolean =
        detail.currentUserIsAssignedCs ||
            detail.inviteMode == MODE_EVERYONE ||
            detail.currentUserRole == ROLE_OWNER ||
            detail.currentUserRole == ROLE_ADMINISTRATOR

    fun canViewCard(
        detail: XingDunGroupDetail,
        member: XingDunGroupMember,
        currentUserID: String,
    ): Boolean = member.userId == currentUserID ||
        detail.currentUserIsAssignedCs ||
        detail.viewMemberCardMode == MODE_EVERYONE ||
        detail.currentUserRole == ROLE_OWNER ||
        detail.currentUserRole == ROLE_ADMINISTRATOR

    fun canRemove(
        detail: XingDunGroupDetail,
        member: XingDunGroupMember,
        currentUserID: String,
    ): Boolean {
        if (member.userId == currentUserID || member.role == ROLE_OWNER || member.userId == detail.ownerUserId) {
            return false
        }
        val hasHighestOperationalPermission = detail.currentUserIsAssignedCs &&
            (detail.currentUserRole == ROLE_OWNER || detail.currentUserRole == ROLE_ADMINISTRATOR)
        val hasOwnerLevelControl = detail.currentUserRole == ROLE_OWNER || hasHighestOperationalPermission
        return if (detail.groupType.equals(GROUP_TYPE_WORK, ignoreCase = true)) {
            hasOwnerLevelControl
        } else {
            hasOwnerLevelControl ||
                (detail.currentUserRole == ROLE_ADMINISTRATOR && member.role == ROLE_MEMBER)
        }
    }

    fun canTransferOwner(detail: XingDunGroupDetail): Boolean =
        detail.currentUserRole == ROLE_OWNER

    fun canMute(
        detail: XingDunGroupDetail,
        member: XingDunGroupMember,
        currentUserID: String,
    ): Boolean = member.userId != currentUserID &&
        member.role == ROLE_MEMBER &&
        (detail.currentUserRole == ROLE_OWNER || detail.currentUserRole == ROLE_ADMINISTRATOR)

    fun canChangeAdministrator(
        detail: XingDunGroupDetail,
        member: XingDunGroupMember,
        currentUserID: String,
    ): Boolean = member.userId != currentUserID &&
        member.role != ROLE_OWNER &&
        member.userId != detail.ownerUserId &&
        detail.currentUserRole == ROLE_OWNER &&
        !detail.groupType.equals(GROUP_TYPE_WORK, ignoreCase = true)

    const val ROLE_OWNER = "owner"
    const val ROLE_ADMINISTRATOR = "administrator"
    const val ROLE_MEMBER = "member"
    private const val MODE_EVERYONE = 1
    private const val GROUP_TYPE_WORK = "work"
}
