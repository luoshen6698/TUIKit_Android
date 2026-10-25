package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMConversationResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Group recovery does not depend on auto-delete being enabled or a control envelope still being the last message. */
internal object XingDunGroupDeletionSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scans = XingDunForegroundDeletionScan(scope)
    private val hints = ConcurrentHashMap.newKeySet<String>()
    private var initialized = false
    private var foregroundJob: Job? = null

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        V2TIMManager.getInstance().addIMSDKListener(object : V2TIMSDKListener() {
            override fun onConnectSuccess() { refresh(app) }
        })
    }

    @Synchronized fun setForeground(context: Context, active: Boolean) {
        scans.setForeground(active)
        if (!active) { foregroundJob?.cancel(); foregroundJob = null; return }
        if (foregroundJob?.isActive == true) return
        foregroundJob = scope.launch {
            refresh(context.applicationContext)
            while (isActive) {
                // Open search and media snapshots must also recheck their independently loaded IDs.
                SearchMessageVisibility.invalidate()
                delay(15_000)
            }
        }
    }

    fun refresh(context: Context) {
        scans.request {
            val account = XingDunC2CDeletionRepository.accountKey() ?: return@request
            val generation = XingDunSessionManager.currentAuthenticationGeneration() ?: return@request
            try {
                var sequence = 0L
                do {
                    ensureCurrent(account, generation)
                    val page = conversationPage(sequence)
                    for (conversation in page.conversationList.orEmpty()) {
                        val group = XingDunGroupDeletionPolicy.group(conversation.conversationID) ?: continue
                        try {
                            ensureCurrent(account, generation)
                            val last = conversation.lastMessage
                            val raw = last?.customElem?.data?.toString(Charsets.UTF_8)
                            if (raw?.let { XingDunCustomMessageParser.parse(it)?.type } == "remote_delete") onHint(context, conversation.conversationID, raw, last)
                            val ids = listOfNotNull(last?.msgID).filter(XingDunGroupDeletionPolicy::validMessage).toSet() +
                                XingDunPinnedMessageRepository.cachedIDs(conversation.conversationID)
                            for (chunk in ids.chunked(100)) XingDunGroupDeletionRepository.check(context, conversation.conversationID, chunk.toSet())
                            val deleted = XingDunGroupDeletionRepository.deletedIDs(context, conversation.conversationID)
                            if (last != null && last.msgID in deleted) deleteLocally(last, account, generation)
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* A missed page is retried on reconnect, foreground or the next visible-page check. */ }
                    }
                    val next = page.nextSeq
                    if (page.isFinished || next == sequence) break
                    sequence = next
                } while (true)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* No optimistic deletion on a failed scan. */ }
        }
    }

    fun onHint(context: Context, conversationID: String, rawData: String?, control: V2TIMMessage? = null) {
        val group = XingDunGroupDeletionPolicy.group(conversationID) ?: return
        val account = XingDunC2CDeletionRepository.accountKey() ?: return
        val generation = XingDunSessionManager.currentAuthenticationGeneration() ?: return
        val ids = XingDunGroupDeletionPolicy.hintIDs(conversationID, rawData)
        val key = "$account:$generation:$conversationID:${ids.sorted()}"
        if (!hints.add(key)) return
        val deadline = System.currentTimeMillis() + 11_000
        if (ids.isNotEmpty()) XingDunGroupDeletionRepository.holdHint(conversationID, ids)
        scope.launch {
            try {
                if (ids.isEmpty()) refresh(context)
                else {
                    // The IM hint can arrive before the server transaction commits its tombstone.
                    for (wait in listOf(0L, 1_000L, 2_000L, 4_000L, 4_000L)) {
                        delay(wait)
                        ensureCurrent(account, generation)
                        try {
                            val deleted = XingDunGroupDeletionRepository.check(context, conversationID, ids)
                            deleteIDs(group, deleted, account, generation)
                            if (deleted.containsAll(ids)) break
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* Keep checking through the bounded pre-commit window. */ }
                    }
                }
                ensureCurrent(account, generation)
                if (control?.groupID == group) deleteLocally(control, account, generation)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Failed reads keep target holds; only a successful post-window authority read can release them. */ }
            finally {
                try { delay((deadline - System.currentTimeMillis()).coerceAtLeast(0)) }
                finally { hints.remove(key); SearchMessageVisibility.invalidate() }
            }
        }
    }

    private suspend fun deleteIDs(group: String, ids: Set<String>, account: String, generation: Long) {
        if (ids.isEmpty()) return
        ensureCurrent(account, generation)
        val messages = suspendCancellableCoroutine<List<V2TIMMessage>> { continuation ->
            V2TIMManager.getMessageManager().findMessages(ids.toList(), object : V2TIMValueCallback<List<V2TIMMessage>> {
                override fun onSuccess(value: List<V2TIMMessage>?) { if (continuation.isActive) continuation.resume(value.orEmpty()) }
                override fun onError(code: Int, desc: String?) { if (continuation.isActive) continuation.resume(emptyList()) }
            })
        }
        ensureCurrent(account, generation)
        messages.filter { it.groupID == group && it.msgID in ids }.forEach { deleteLocally(it, account, generation) }
    }

    private suspend fun deleteLocally(message: V2TIMMessage, account: String, generation: Long) {
        ensureCurrent(account, generation)
        suspendCancellableCoroutine<Unit> { continuation ->
            V2TIMManager.getMessageManager().deleteMessageFromLocalStorage(message, object : V2TIMCallback {
                override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onError(code: Int, desc: String?) { if (continuation.isActive) continuation.resume(Unit) }
            })
        }
    }

    private suspend fun conversationPage(sequence: Long): V2TIMConversationResult = suspendCancellableCoroutine { continuation ->
        V2TIMManager.getConversationManager().getConversationList(sequence, 100, object : V2TIMValueCallback<V2TIMConversationResult> {
            override fun onSuccess(value: V2TIMConversationResult?) {
                if (!continuation.isActive) return
                if (value == null) continuation.resumeWithException(IllegalStateException("Missing conversation page")) else continuation.resume(value)
            }
            override fun onError(code: Int, desc: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Conversation lookup unavailable")) }
        })
    }

    private fun ensureCurrent(account: String, generation: Long) {
        check(account == XingDunC2CDeletionRepository.accountKey() && generation == XingDunSessionManager.currentAuthenticationGeneration()) { "Authentication instance changed" }
    }
}
