package io.trtc.tuikit.chat.uikit.components.messageinput.model

internal object MentionAllPermissionPolicy {
    fun shouldShow(canMentionAll: Boolean): Boolean = canMentionAll

    fun canSend(canMentionAll: Boolean, mentions: List<MentionInfo>): Boolean =
        canMentionAll || mentions.none(MentionInfo::isAtAll)
}
