package io.trtc.tuikit.chat.uikit.components.conversationlist.adapter

import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

interface ConversationPreviewMessageProvider {
    fun shouldOverride(conversation: ConversationInfo): Boolean

    fun requestPreviewMessage(conversation: ConversationInfo, completion: (MessageInfo?) -> Unit)
}

object ConversationPreviewMessageRegistry {
    @Volatile
    private var provider: ConversationPreviewMessageProvider? = null

    @JvmStatic
    fun setProvider(provider: ConversationPreviewMessageProvider?) {
        this.provider = provider
    }

    internal fun shouldOverride(conversation: ConversationInfo): Boolean =
        provider?.shouldOverride(conversation) == true

    internal fun requestPreviewMessage(
        conversation: ConversationInfo,
        completion: (MessageInfo?) -> Unit,
    ) {
        provider?.requestPreviewMessage(conversation, completion)
    }
}
