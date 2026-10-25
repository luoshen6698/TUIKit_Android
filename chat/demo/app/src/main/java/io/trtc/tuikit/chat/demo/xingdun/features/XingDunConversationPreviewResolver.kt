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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager

internal object XingDunConversationPreviewResolver {
    private const val PAGE_COUNT = 100
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, MessageInfo>()
    private val emptyKeys = ConcurrentHashMap.newKeySet<String>()
    private val pending = mutableMapOf<String, MutableList<(MessageInfo?) -> Unit>>()
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val publicationRevision = AtomicLong(0)

    fun register() {
        ConversationPreviewMessageRegistry.setProvider(
            object : ConversationPreviewMessageProvider {
                override fun shouldOverride(conversation: ConversationInfo): Boolean =
                    XingDunMessageDeletionVisibility.isDeleted(conversation.conversationID, conversation.lastMessage?.msgID.orEmpty()) ||
                    (conversation.conversationID.startsWith("group_") && conversation.lastMessage?.let {
                        !XingDunGroupDeletionRepository.canDisplay(conversation.conversationID, it)
                    } == true) ||
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
        publicationRevision.incrementAndGet()
        cache.clear()
        emptyKeys.clear()
        synchronized(lock) { pending.clear() }
    }
    fun invalidateResolved() { cache.clear(); emptyKeys.clear() }

    private fun requestPreviewMessage(
        conversation: ConversationInfo,
        completion: (MessageInfo?) -> Unit,
    ) {
        val guard = XingDunDeletionPublicationGuard(XingDunC2CDeletionRepository::accountKey,
            XingDunSessionManager::currentAuthenticationGeneration, publicationRevision::get)
        if (!guard.isCurrent()) return completion(null)
        val key = cacheKey(conversation)
        cache[key]?.let {
            if (guard.isCurrent()) completion(it.takeUnless { message -> XingDunMessageDeletionVisibility.isDeleted(conversation.conversationID, message.msgID) })
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
                    if (!guard.isCurrent()) { synchronized(lock) { pending.remove(key) }; return }
                    scope.launch {
                    val checked = XingDunMessageDeletionVisibility.filterMessages(
                        storeContext(), conversation.conversationID, store.state.messageList.value,
                    )
                    if (!guard.isCurrent()) { synchronized(lock) { pending.remove(key) }; return@launch }
                    val preview = checked
                        .asSequence()
                        .filterNot { XingDunMessageDeletionVisibility.isDeleted(conversation.conversationID, it.msgID) }
                        .filterNot { message ->
                            XingDunCustomMessageParser.parse(message).let { custom ->
                                XingDunConversationPreviewPolicy.shouldSkipInConversationList(custom) ||
                                    XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(custom)
                            }
                        }
                        .maxByOrNull { it.timestamp ?: 0L }
                    complete(key, preview, guard::isCurrent)
                    }
                }

                override fun onFailure(code: Int, desc: String) = complete(key, null, guard::isCurrent)
            },
        )
    }

    private lateinit var applicationContext: android.content.Context
    fun initialize(context: android.content.Context) { applicationContext = context.applicationContext }
    private fun storeContext() = applicationContext

    private fun complete(key: String, preview: MessageInfo?, isCurrent: () -> Boolean) {
        if (!isCurrent()) { synchronized(lock) { pending.remove(key) }; return }
        if (preview == null) emptyKeys += key else cache[key] = preview
        val callbacks = synchronized(lock) { pending.remove(key).orEmpty() }
        mainHandler.post { if (isCurrent()) callbacks.forEach { it(preview) } }
    }

    private fun cacheKey(conversation: ConversationInfo): String =
        "${XingDunC2CDeletionRepository.accountKey()}:${XingDunSessionManager.currentAuthenticationGeneration()}:${publicationRevision.get()}:${conversation.conversationID}:${conversation.lastMessage?.msgID.orEmpty()}"
}
