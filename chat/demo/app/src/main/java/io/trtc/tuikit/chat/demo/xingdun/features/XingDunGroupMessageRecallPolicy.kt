package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember

internal data class XingDunGroupMessageRecallAuthorization(
    val currentUserRole: String,
    val currentUserIsAssignedCustomerService: Boolean,
    val assignedCustomerServiceUserID: String?,
    val memberRolesByUserID: Map<String, String>,
) {
    fun canRecallOther(senderUserID: String): Boolean {
        val senderID = senderUserID.trim()
        if (senderID.isEmpty()) return false
        if (currentUserIsAssignedCustomerService) return true
        if (assignedCustomerServiceUserID?.trim() == senderID) return false
        return currentUserRole in setOf("owner", "administrator") &&
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
        )
    }
}
