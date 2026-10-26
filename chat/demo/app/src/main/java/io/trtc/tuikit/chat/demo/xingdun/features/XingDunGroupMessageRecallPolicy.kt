package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember

internal data class XingDunGroupMessageRecallAuthorization(
    val currentUserRole: String,
    val currentUserIsAssignedCustomerService: Boolean,
    val assignedCustomerServiceUserID: String?,
    val memberRolesByUserID: Map<String, String>,
    val deleteMessageMode: Int? = null,
) {
    fun canRecallOther(senderUserID: String): Boolean {
        if (deleteMessageMode !in listOf(1, 2)) return false
        val senderID = senderUserID.trim()
        if (senderID.isEmpty()) return false
        if (currentUserIsAssignedCustomerService) return true
        if (assignedCustomerServiceUserID?.trim() == senderID) return false
        val allowedRoles = if (deleteMessageMode == 1) setOf("owner", "administrator", "member")
            else setOf("owner", "administrator")
        return currentUserRole in allowedRoles &&
            memberRolesByUserID[senderID] == "member"
    }

    companion object {
        fun from(
            detail: XingDunGroupDetail,
            members: List<XingDunGroupMember>,
        ) = XingDunGroupMessageRecallAuthorization(
            currentUserRole = detail.currentUserRole,
            currentUserIsAssignedCustomerService = detail.currentUserIsAssignedCs,
            assignedCustomerServiceUserID = detail.assignedCustomerServiceUserId,
            memberRolesByUserID = members.associate { it.userId to it.role },
            deleteMessageMode = detail.deleteMessageMode,
        )
    }
}
