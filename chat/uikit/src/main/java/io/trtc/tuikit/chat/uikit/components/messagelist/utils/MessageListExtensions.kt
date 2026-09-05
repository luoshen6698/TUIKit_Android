package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.jsonData2Dictionary
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.GroupTipsInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType

val MessageInfo.groupReadCount: Int
    get() = readReceiptInfo?.readCount ?: 0

enum class MessageReadReceiptDisplayState {
    UNREAD,
    READ,
    ALL_READ,
}

val MessageInfo.isShowReadReceipt: Boolean
    get() {
        return isSentBySelf &&
            needReadReceipt &&
            status == MessageStatus.SEND_SUCCESS &&
            messageType != MessageType.TIPS
    }

fun MessageInfo.shouldShowReadReceiptIndicator(
    isContainerAllowed: Boolean = true
): Boolean {
    return isContainerAllowed && isShowReadReceipt
}

val MessageInfo.readReceiptDisplayState: MessageReadReceiptDisplayState
    get() {
        val receipt = readReceiptInfo
        if (conversationType != ConversationType.GROUP) {
            return if (receipt?.isPeerRead == true) {
                MessageReadReceiptDisplayState.READ
            } else {
                MessageReadReceiptDisplayState.UNREAD
            }
        }

        val readCount = receipt?.readCount ?: 0
        val unreadCount = receipt?.unreadCount ?: 0
        return when {
            receipt != null && unreadCount == 0 -> MessageReadReceiptDisplayState.ALL_READ
            readCount > 0 -> MessageReadReceiptDisplayState.READ
            else -> MessageReadReceiptDisplayState.UNREAD
        }
    }

val MessageInfo.isAllRead: Boolean
    get() {
        val receipt = readReceiptInfo
        return when {
            receipt == null -> false
            conversationType != ConversationType.GROUP -> receipt.isPeerRead
            else -> receipt.unreadCount == 0
        }
    }

val MessageInfo.isUnread: Boolean
    get() {
        val receipt = readReceiptInfo
        return when {
            receipt == null -> true
            conversationType != ConversationType.GROUP -> !receipt.isPeerRead
            else -> receipt.unreadCount > 0
        }
    }

val MessageInfo.senderDisplayName: String
    get() = from.nameCard?.takeIf { it.isNotBlank() }
        ?: from.friendRemark?.takeIf { it.isNotBlank() }
        ?: from.nickname?.takeIf { it.isNotBlank() }
        ?: from.userID

fun getCreateGroupDisplayString(context: Context, message: MessageInfo): String {
    val customData = (message.messagePayload as? CustomMessagePayload)?.customData
    val customInfo = jsonData2Dictionary(customData)
    val groupType = customInfo?.get("groupType").orEmpty()
    return if (groupType.equals("Community", ignoreCase = true)) {
        context.getString(R.string.message_list_community_create_tips_message)
    } else {
        context.getString(R.string.message_list_group_create_tips_message)
    }
}

fun getSystemInfoDisplayString(context: Context, groupTips: List<GroupTipsInfo>?): String {
    val tips = groupTips?.firstOrNull() ?: return ""
    return when (tips) {
        is GroupTipsInfo.JoinGroup -> context.getString(R.string.message_list_group_event_member_joined)
        is GroupTipsInfo.InviteToGroup -> context.getString(R.string.message_list_group_event_members_invited)
        is GroupTipsInfo.QuitGroup -> context.getString(R.string.message_list_group_event_member_left)
        is GroupTipsInfo.KickedFromGroup -> context.getString(R.string.message_list_group_event_member_removed)
        is GroupTipsInfo.SetGroupAdmin -> context.getString(R.string.message_list_group_event_admin_set)
        is GroupTipsInfo.CancelGroupAdmin -> context.getString(R.string.message_list_group_event_admin_cancelled)
        is GroupTipsInfo.ChangeGroupName,
        is GroupTipsInfo.ChangeGroupAvatar,
        is GroupTipsInfo.ChangeGroupNotification,
        is GroupTipsInfo.ChangeGroupIntroduction,
        is GroupTipsInfo.ChangeGroupOwner,
        is GroupTipsInfo.ChangeGroupMuteAll,
        is GroupTipsInfo.ChangeJoinGroupApproval,
        is GroupTipsInfo.ChangeInviteToGroupApproval -> context.getString(R.string.message_list_group_event_profile_updated)
        is GroupTipsInfo.MuteGroupMember -> context.getString(R.string.message_list_group_event_member_profile_updated)
        is GroupTipsInfo.PinGroupMessage -> context.getString(R.string.message_list_group_event_message_pinned)
        is GroupTipsInfo.UnpinGroupMessage -> context.getString(R.string.message_list_group_event_message_unpinned)
        GroupTipsInfo.Unknown -> context.getString(R.string.message_list_group_event_notice)
    }
}
