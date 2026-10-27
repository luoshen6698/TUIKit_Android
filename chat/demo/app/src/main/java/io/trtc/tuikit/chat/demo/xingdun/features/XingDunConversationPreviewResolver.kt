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
import kotlinx.coroutines.withContext
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility

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
        val requestedStamp = XingDunSessionManager.currentMessageReadStamp() ?: return completion(null)
        val requestedRevision = publicationRevision.get()
        scope.launch(Dispatchers.IO) {
            val source = runCatching { XingDunGroupDeletionRepository.currentReadScope() }.getOrNull()
                ?: return@launch
            fun current() = source.stamp == requestedStamp && requestedStamp == XingDunSessionManager.currentMessageReadStamp() &&
                requestedRevision == publicationRevision.get()
            if (!current()) return@launch
            val key = cacheKey(conversation, source, requestedRevision)
            cache[key]?.let { cached ->
                val displayRevision = SearchMessageVisibility.revision.value
                val checked = XingDunGroupDeletionRepository.mapReadScope(storeContext(), source, conversation.conversationID, listOf(cached)).firstOrNull()
                mainHandler.post { if (current() && displayRevision == SearchMessageVisibility.revision.value) completion(checked) }
                return@launch
            }
            if (key in emptyKeys) {
                mainHandler.post { if (current()) completion(null) }
                return@launch
            }
            val shouldLoad = synchronized(lock) {
                val callbacks = pending[key]
                if (callbacks != null) { callbacks += completion; false }
                else { pending[key] = mutableListOf(completion); true }
            }
            if (!shouldLoad) return@launch
            // Preserve SDK admission on Main; only the bound read and filtering move to IO.
            withContext(Dispatchers.Main.immediate) {
                if (!current()) { synchronized(lock) { pending.remove(key) }; return@withContext }
                val store = MessageListStore.create(conversation.conversationID)
                store.loadMessages(
                    MessageLoadOption(direction = MessageLoadDirection.OLDER, pageCount = PAGE_COUNT),
                    object : CompletionHandler {
                        override fun onSuccess() {
                            if (!current()) { synchronized(lock) { pending.remove(key) }; return }
                            val snapshot = store.state.messageList.value.map { it.copy(quoteInfo = it.quoteInfo?.copy()) }
                            scope.launch(Dispatchers.IO) {
                                val checked = XingDunMessageDeletionVisibility.filterMessages(storeContext(), conversation.conversationID, snapshot)
                                if (!current()) { synchronized(lock) { pending.remove(key) }; return@launch }
                                // One scoped durable set replaces the former per-message isDeleted/decrypt loop.
                                val displayRevision = SearchMessageVisibility.revision.value
                                val visible = XingDunGroupDeletionRepository.mapReadScope(storeContext(), source, conversation.conversationID, checked)
                                if (!current()) { synchronized(lock) { pending.remove(key) }; return@launch }
                                val preview = visible.asSequence().filterNot { message ->
                                    XingDunCustomMessageParser.parse(message).let { custom ->
                                        XingDunConversationPreviewPolicy.shouldSkipInConversationList(custom) ||
                                            XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(custom)
                                    }
                                }.maxByOrNull { it.timestamp ?: 0L }
                                complete(key, preview, ::current, displayRevision)
                            }
                        }
                        override fun onFailure(code: Int, desc: String) = complete(key, null, ::current)
                    },
                )
            }
        }
    }

    private lateinit var applicationContext: android.content.Context
    fun initialize(context: android.content.Context) { applicationContext = context.applicationContext }
    private fun storeContext() = applicationContext

    private fun complete(key: String, preview: MessageInfo?, isCurrent: () -> Boolean,
                         displayRevision: Long = SearchMessageVisibility.revision.value) {
        if (!isCurrent() || displayRevision != SearchMessageVisibility.revision.value) {
            synchronized(lock) { pending.remove(key) }; return
        }
        if (preview == null) emptyKeys += key else cache[key] = preview
        val callbacks = synchronized(lock) { pending.remove(key).orEmpty() }
        mainHandler.post {
            if (isCurrent() && displayRevision == SearchMessageVisibility.revision.value) callbacks.forEach { it(preview) }
        }
    }

    private fun cacheKey(conversation: ConversationInfo, source: XingDunGroupDeletionReadScope, revision: Long): String =
        "${source.display.account}:${source.stamp}:$revision:${conversation.conversationID}:${conversation.lastMessage?.msgID.orEmpty()}"
}
