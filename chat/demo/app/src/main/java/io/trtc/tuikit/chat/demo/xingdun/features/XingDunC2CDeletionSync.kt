package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.util.Log
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMConversationResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reconnect/foreground repair checks actual history IDs, not just the last control envelope. */
internal object XingDunC2CDeletionSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanning = AtomicBoolean(false)
    private var initialized = false
    @Volatile private var lastScanAccount: String? = null
    @Volatile private var lastScanMillis = 0L

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        V2TIMManager.getInstance().addIMSDKListener(object : V2TIMSDKListener() {
            override fun onConnectSuccess() { refresh(appContext, force = true) }
        })
    }

    fun refresh(context: Context, force: Boolean = false) {
        val account = XingDunC2CDeletionRepository.accountKey() ?: return
        val now = System.currentTimeMillis()
        if (!force && account == lastScanAccount && now - lastScanMillis in 0..15_000) return
        if (!scanning.compareAndSet(false, true)) return
        lastScanAccount = account
        lastScanMillis = now
        val appContext = context.applicationContext
        scope.launch {
            try {
                var sequence = 0L
                do {
                    ensureAccount(account)
                    val page = conversationPage(sequence)
                    for (conversation in page.conversationList.orEmpty()) {
                        val self = XingDunSessionManager.currentSession()?.timUserId ?: break
                        if (XingDunC2CDeletionPolicy.peer(conversation.conversationID, self) == null) continue
                        ensureAccount(account)
                        try {
                            val messages = history(conversation.userID)
                            ensureAccount(account)
                            reconcile(appContext, conversation.conversationID, messages, account)
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { Log.w("XDC2CDeletion", "History reconciliation deferred") }
                    }
                    val next = page.nextSeq
                    if (page.isFinished || next == sequence) break
                    sequence = next
                } while (true)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Log.w("XDC2CDeletion", "Deletion reconciliation deferred") }
            finally { scanning.set(false) }
        }
    }

    fun onHint(context: Context, conversationID: String, values: Map<String, String>, control: V2TIMMessage) {
        val account = XingDunC2CDeletionRepository.accountKey() ?: return
        val ids = XingDunAutoDeletePolicy.remoteDeletedIDs(values["message_ids"])
            .filter(XingDunC2CDeletionPolicy::validID).toSet()
        if (ids.isEmpty()) return
        scope.launch {
            try {
                ids.chunked(100).forEach { chunk ->
                    ensureAccount(account)
                    val confirmed = XingDunC2CDeletionRepository.check(context, conversationID, chunk.toSet())
                    deleteIDs(confirmed, account)
                }
                // Only the invisible envelope is discarded when no target tombstone is confirmed.
                ensureAccount(account)
                deleteLocally(control, account)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Log.w("XDC2CDeletion", "Deletion hint awaits authoritative confirmation") }
        }
    }

    private suspend fun reconcile(context: Context, conversationID: String, messages: List<V2TIMMessage>, account: String) {
        val candidates = messages.flatMap { message ->
            val custom = message.customElem?.data?.toString(Charsets.UTF_8)?.let { XingDunCustomMessageParser.parse(it) }
            if (custom?.type == "remote_delete") XingDunAutoDeletePolicy.remoteDeletedIDs(custom.values["message_ids"])
            else setOf(message.msgID.orEmpty())
        }.filter(XingDunC2CDeletionPolicy::validID).toSet()
        if (candidates.isEmpty()) return
        candidates.chunked(100).forEach { chunk ->
            ensureAccount(account)
            XingDunC2CDeletionRepository.check(context, conversationID, chunk.toSet())
        }
        val confirmed = XingDunC2CDeletionRepository.deletedIDs(context, conversationID)
        for (message in messages) {
            val custom = message.customElem?.data?.toString(Charsets.UTF_8)?.let { XingDunCustomMessageParser.parse(it) }
            if (message.msgID in confirmed || custom?.type == "remote_delete") deleteLocally(message, account)
        }
    }

    private suspend fun deleteIDs(ids: Set<String>, account: String) {
        if (ids.isEmpty()) return
        ensureAccount(account)
        val messages = suspendCancellableCoroutine<List<V2TIMMessage>> { continuation ->
            V2TIMManager.getMessageManager().findMessages(ids.toList(), object : V2TIMValueCallback<List<V2TIMMessage>> {
                override fun onSuccess(value: List<V2TIMMessage>?) {
                    if (continuation.isActive) continuation.resume(value.orEmpty())
                }
                override fun onError(code: Int, desc: String?) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            })
        }
        messages.filter { it.msgID in ids }.forEach { deleteLocally(it, account) }
    }

    private suspend fun deleteLocally(message: V2TIMMessage, account: String) {
        ensureAccount(account)
        suspendCancellableCoroutine<Unit> { continuation ->
            V2TIMManager.getMessageManager().deleteMessageFromLocalStorage(message, object : V2TIMCallback {
                override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onError(code: Int, desc: String?) {
                    if (continuation.isActive) continuation.resume(Unit) // Persistent tombstones remain for retry.
                }
            })
        }
    }

    private suspend fun conversationPage(sequence: Long): V2TIMConversationResult = suspendCancellableCoroutine { continuation ->
        V2TIMManager.getConversationManager().getConversationList(sequence, 100, object : V2TIMValueCallback<V2TIMConversationResult> {
            override fun onSuccess(value: V2TIMConversationResult?) {
                if (!continuation.isActive) return
                if (value == null) continuation.resumeWithException(IllegalStateException("Missing conversation page"))
                else continuation.resume(value)
            }
            override fun onError(code: Int, desc: String?) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Conversation scan failed"))
            }
        })
    }

    private suspend fun history(peer: String): List<V2TIMMessage> = suspendCancellableCoroutine { continuation ->
        V2TIMManager.getMessageManager().getC2CHistoryMessageList(peer, 100, null, object : V2TIMValueCallback<List<V2TIMMessage>> {
            override fun onSuccess(value: List<V2TIMMessage>?) { if (continuation.isActive) continuation.resume(value.orEmpty()) }
            override fun onError(code: Int, desc: String?) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("History scan failed"))
            }
        })
    }

    private fun ensureAccount(account: String) { check(XingDunC2CDeletionRepository.accountKey() == account) { "Session changed" } }
}
