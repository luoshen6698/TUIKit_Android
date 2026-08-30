package io.trtc.tuikit.chat.uikit.components.messagelist.config

/** Optional app-level presentation hook for locally marked messages. */
object MessageLocalMarkConfig {
    @Volatile
    var provider: ((conversationID: String, messageID: String) -> Boolean)? = null

    fun isMarked(conversationID: String, messageID: String): Boolean =
        provider?.invoke(conversationID, messageID) == true
}
