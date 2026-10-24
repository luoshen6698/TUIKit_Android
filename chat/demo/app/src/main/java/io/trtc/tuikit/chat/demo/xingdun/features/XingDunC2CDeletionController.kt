package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Every loaded history page and foreground interval is checked, independently of auto-delete settings. */
internal class XingDunC2CDeletionController(
    context: Context,
    private val conversationID: String,
    private val scope: CoroutineScope,
    private val store: MessageListStore,
) {
    private val context = context.applicationContext
    private val account = XingDunC2CDeletionRepository.accountKey()
    private val mutex = Mutex()
    private var job: Job? = null
    private val checkedAt = mutableMapOf<String, Long>()
    private val listener: (String) -> Unit = { changed ->
        if (changed == conversationID && job?.isActive == true) scope.launch { reconcile() }
    }

    fun start() {
        if (job?.isActive == true || account == null) return
        checkedAt.clear()
        XingDunC2CDeletionRepository.addListener(listener)
        job = scope.launch {
            launch { store.state.messageList.collect { reconcile() } }
            while (isActive && account == XingDunC2CDeletionRepository.accountKey()) {
                reconcile()
                delay(15_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        XingDunC2CDeletionRepository.removeListener(listener)
    }

    private suspend fun reconcile() = mutex.withLock {
        if (account != XingDunC2CDeletionRepository.accountKey()) return@withLock
        try {
            val messages = store.state.messageList.value.toList()
            val now = System.currentTimeMillis()
            val known = XingDunC2CDeletionRepository.deletedIDs(context, conversationID)
            val candidates = SearchMessageVisibility.candidateIDs(messages) +
                listOfNotNull(XingDunPinnedMessageRepository.directPin(context, conversationID)?.messageId)
            val ids = candidates.filter(XingDunC2CDeletionPolicy::validID)
                .filter { it !in known && now - (checkedAt[it] ?: 0L) >= 15_000 }.toSet()
            try {
                for (chunk in ids.chunked(100)) {
                    XingDunC2CDeletionRepository.check(context, conversationID, chunk.toSet())
                    chunk.forEach { checkedAt[it] = now }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Preserve new rows; already persisted tombstones must still stay hidden offline. */ }
            if (account != XingDunC2CDeletionRepository.accountKey()) return@withLock
            val deleted = XingDunC2CDeletionRepository.deletedIDs(context, conversationID)
            val due = messages.filter { it.msgID in deleted }
            if (due.isNotEmpty()) suspendCancellableCoroutine<Unit> { continuation ->
                store.deleteMessages(due, object : CompletionHandler {
                    override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                    override fun onFailure(code: Int, desc: String) { if (continuation.isActive) continuation.resume(Unit) }
                })
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* No optimistic deletion; reconnect/history will retry the authoritative read. */ }
    }
}
