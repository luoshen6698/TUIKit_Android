package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicxcore.api.group.GroupType

internal object MessageInputReadReceiptPolicy {

    fun needReadReceipt(
        isReadReceiptEnabled: Boolean,
        isGroupConversation: Boolean,
        groupType: GroupType?
    ): Boolean {
        return isReadReceiptEnabled && isGroupConversation && groupType != GroupType.COMMUNITY
    }
}
