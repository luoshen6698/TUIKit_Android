package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Every page, quote target and cached pin is a candidate even if its remote-delete envelope was missed. */
internal class XingDunGroupDeletionController(
    context: Context,
    private val conversationID: String,
    private val scope: CoroutineScope,
    private val store: MessageListStore,
    private val actions: XingDunGroupDeletionActions,
    private val onVerificationFailure: (Boolean) -> Unit,
) {
    private val context = context.applicationContext
    private val generation = XingDunSessionManager.currentAuthenticationGeneration()
    private val mutex = Mutex()
    private var job: Job? = null
    private val checkedAt = mutableMapOf<String, Long>()
    private val listener: (String) -> Unit = { changed ->
        if (changed == conversationID && job?.isActive == true) scope.launch { reconcile() }
    }
    fun start() {
        if (job?.isActive == true) return
        checkedAt.clear()
        XingDunGroupDeletionRepository.addListener(listener)
        job = scope.launch {
            launch { store.state.messageList.collect { reconcile() } }
            while (isActive && generation == XingDunSessionManager.currentAuthenticationGeneration()) {
                reconcile(); delay(15_000)
            }
        }
    }
    fun retry() { checkedAt.clear(); scope.launch { reconcile() } }
    fun stop() { job?.cancel(); job = null; XingDunGroupDeletionRepository.removeListener(listener) }

    private suspend fun reconcile() = mutex.withLock {
        if (generation != XingDunSessionManager.currentAuthenticationGeneration()) return@withLock
        val messages = store.state.messageList.value.toList()
        messages.forEach { message ->
            val custom = XingDunCustomMessageParser.parse(message)
            if (custom?.type == "remote_delete") XingDunGroupDeletionSync.onHint(context, conversationID,
                (message.messagePayload as? io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload)?.customData)
        }
        val controls = messages.filter { XingDunCustomMessageParser.parse(it)?.type == "remote_delete" }
        if (controls.isNotEmpty()) store.deleteMessages(controls, object : CompletionHandler {
            override fun onSuccess() = Unit
            override fun onFailure(code: Int, desc: String) = Unit
        })
        val candidates = SearchMessageVisibility.candidateIDs(messages) + XingDunPinnedMessageRepository.cachedIDs(conversationID)
        val now = System.currentTimeMillis()
        val ids = candidates.filter(XingDunGroupDeletionPolicy::validMessage)
            .filter { now - (checkedAt[it] ?: 0L) >= 15_000 }.toSet()
        var failed = false
        try {
            for (chunk in ids.chunked(100)) {
                XingDunGroupDeletionRepository.check(context, conversationID, chunk.toSet())
                chunk.forEach { checkedAt[it] = now }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        if (generation != XingDunSessionManager.currentAuthenticationGeneration()) return@withLock
        onVerificationFailure(failed)
        actions.refresh(messages)
    }
}
