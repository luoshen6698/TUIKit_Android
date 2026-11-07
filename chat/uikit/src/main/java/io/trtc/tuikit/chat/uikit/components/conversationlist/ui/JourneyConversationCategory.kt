package io.trtc.tuikit.chat.uikit.components.conversationlist.ui

/** Reference Android categories; filtering does not mutate conversation or unread state. */
enum class JourneyConversationCategory {
    ALL, PINNED, TEAM;
    fun accepts(pinned: Boolean, group: Boolean): Boolean = when (this) {
        ALL -> true
        PINNED -> pinned
        TEAM -> group
    }
}
