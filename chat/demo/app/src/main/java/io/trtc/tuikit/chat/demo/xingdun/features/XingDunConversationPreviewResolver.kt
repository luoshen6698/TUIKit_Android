package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Handler
import android.os.Looper
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.chat.uikit.components.conversationlist.adapter.ConversationPreviewMessageProvider
import io.trtc.tuikit.chat.uikit.components.conversationlist.adapter.ConversationPreviewMessageRegistry
import java.util.concurrent.ConcurrentHashMap

internal object XingDunConversationPreviewResolver {
    private const val PAGE_COUNT = 100
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, MessageInfo>()
    private val emptyKeys = ConcurrentHashMap.newKeySet<String>()
    private val pending = mutableMapOf<String, MutableList<(MessageInfo?) -> Unit>>()
    private val lock = Any()

    fun register() {
        ConversationPreviewMessageRegistry.setProvider(
            object : ConversationPreviewMessageProvider {
                override fun shouldOverride(conversation: ConversationInfo): Boolean =
                    XingDunConversationPreviewPolicy.shouldSkipInConversationList(
                        conversation.lastMessage?.let { XingDunCustomMessageParser.parse(it) },
                    )

                override fun requestPreviewMessage(
                    conversation: ConversationInfo,
                    completion: (MessageInfo?) -> Unit,
                ) = this@XingDunConversationPreviewResolver.requestPreviewMessage(conversation, completion)
            },
        )
    }

    fun clear() {
        cache.clear()
        emptyKeys.clear()
        synchronized(lock) { pending.clear() }
    }

    private fun requestPreviewMessage(
        conversation: ConversationInfo,
        completion: (MessageInfo?) -> Unit,
    ) {
        val key = cacheKey(conversation)
        cache[key]?.let {
            completion(it)
            return
        }
        if (key in emptyKeys) {
            completion(null)
            return
        }

        val shouldLoad = synchronized(lock) {
            val callbacks = pending[key]
            if (callbacks != null) {
                callbacks += completion
                false
            } else {
                pending[key] = mutableListOf(completion)
                true
            }
        }
        if (!shouldLoad) return

        val store = MessageListStore.create(conversation.conversationID)
        store.loadMessages(
            MessageLoadOption(direction = MessageLoadDirection.OLDER, pageCount = PAGE_COUNT),
            object : CompletionHandler {
                override fun onSuccess() {
                    val preview = store.state.messageList.value
                        .asSequence()
                        .filterNot { it.msgID == conversation.lastMessage?.msgID }
                        .filterNot { message ->
                            XingDunCustomMessageParser.parse(message).let { custom ->
                                XingDunConversationPreviewPolicy.shouldSkipInConversationList(custom) ||
                                    XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(custom)
                            }
                        }
                        .maxByOrNull { it.timestamp ?: 0L }
                    complete(key, preview)
                }

                override fun onFailure(code: Int, desc: String) = complete(key, null)
            },
        )
    }

    private fun complete(key: String, preview: MessageInfo?) {
        if (preview == null) emptyKeys += key else cache[key] = preview
        val callbacks = synchronized(lock) { pending.remove(key).orEmpty() }
        mainHandler.post { callbacks.forEach { it(preview) } }
    }

    private fun cacheKey(conversation: ConversationInfo): String =
        "${conversation.conversationID}:${conversation.lastMessage?.msgID.orEmpty()}"
}
