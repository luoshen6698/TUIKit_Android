package io.trtc.tuikit.chat.uikit.components.config

sealed interface BusinessAction {
    data class ApplyFriend(
        val targetUserID: String,
        val message: String,
        val remark: String
    ) : BusinessAction

    data class HandleFriendApplication(
        val targetUserID: String,
        val approve: Boolean
    ) : BusinessAction

    data class DeleteFriend(val targetUserID: String) : BusinessAction
    data class SetFriendBlacklist(val targetUserID: String, val enabled: Boolean) : BusinessAction
    data class SetFriendRemark(val targetUserID: String, val remark: String) : BusinessAction

    data class CreateGroup(
        val groupName: String,
        val requestedGroupID: String?,
        val avatarURL: String?,
        val memberUserIDs: List<String>,
        val groupType: String
    ) : BusinessAction

    data class JoinGroup(val groupID: String, val message: String) : BusinessAction
    data class InviteGroupMembers(val groupID: String, val memberUserIDs: List<String>) : BusinessAction
    data class HandleGroupApplication(
        val applicationID: String,
        val groupID: String,
        val fromUserID: String,
        val approve: Boolean
    ) : BusinessAction

    data class UpdateGroup(
        val groupID: String,
        val fields: Map<String, String?>
    ) : BusinessAction

    data class SetGroupAdministrator(
        val groupID: String,
        val memberUserID: String,
        val enabled: Boolean
    ) : BusinessAction

    data class MuteGroupMember(
        val groupID: String,
        val memberUserID: String,
        val durationSeconds: Long
    ) : BusinessAction

    data class SetGroupMuteAll(val groupID: String, val enabled: Boolean) : BusinessAction
    data class TransferGroupOwner(val groupID: String, val newOwnerUserID: String) : BusinessAction
    data class RemoveGroupMembers(val groupID: String, val memberUserIDs: List<String>) : BusinessAction
    data class LeaveGroup(val groupID: String) : BusinessAction
    data class DismissGroup(val groupID: String) : BusinessAction
}

data class BusinessActionResult(val identifier: String? = null)

interface BusinessActionCompletion {
    fun onSuccess(result: BusinessActionResult = BusinessActionResult())
    fun onFailure(code: Int, description: String)
}

fun interface BusinessActionHandler {
    /** Return true when the handler owns this operation, or false to keep the stock SDK path. */
    fun handle(action: BusinessAction, completion: BusinessActionCompletion): Boolean
}

object BusinessActionRegistry {
    @Volatile
    var handler: BusinessActionHandler? = null

    fun dispatch(action: BusinessAction, completion: BusinessActionCompletion): Boolean {
        val currentHandler = handler ?: return false
        return try {
            currentHandler.handle(action, completion)
        } catch (error: Throwable) {
            completion.onFailure(-1, error.message.orEmpty())
            true
        }
    }
}
