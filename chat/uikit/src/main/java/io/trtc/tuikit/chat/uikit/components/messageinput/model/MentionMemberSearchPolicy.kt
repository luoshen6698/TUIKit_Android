package io.trtc.tuikit.chat.uikit.components.messageinput.model

import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicxcore.api.group.GroupMember

internal object MentionMemberSearchPolicy {
    fun filter(members: List<GroupMember>, query: String): List<GroupMember> {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            return members
        }
        return members.filter { member ->
            listOf(
                member.displayName,
                member.userID,
                member.nameCard,
                member.friendRemark,
                member.nickname
            )
                .filterNotNull()
                .distinct()
                .any { value -> value.contains(keyword, ignoreCase = true) }
        }
    }

    fun mergeSelectedIDs(
        currentSelectedIDs: Collection<String>,
        visibleMemberIDs: Collection<String>,
        visibleSelectedIDs: Collection<String>
    ): LinkedHashSet<String> {
        val visibleIDs = visibleMemberIDs.toSet()
        return currentSelectedIDs
            .filterTo(linkedSetOf()) { it !in visibleIDs }
            .apply { addAll(visibleSelectedIDs) }
    }
}
