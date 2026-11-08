package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject

internal data class XingDunGroupInvitationUnread(val owner: String?, val count: Int) {
    fun forOwner(current: String?): Int = count.takeIf { current != null && current == owner } ?: 0
    companion object {
        fun parse(owner: String, data: JsonObject): XingDunGroupInvitationUnread {
            val value = data.get("unread_count")
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
            require(value.asString.matches(Regex("0|[1-9][0-9]*")))
            val count = requireNotNull(value.asString.toIntOrNull())
            require(count in 0..100)
            return XingDunGroupInvitationUnread(owner, count)
        }
    }
}
