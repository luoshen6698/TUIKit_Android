package io.trtc.tuikit.chat.demo.main

import com.tencent.imsdk.v2.V2TIMConversation
import com.tencent.imsdk.v2.V2TIMConversationOperationResult
import com.tencent.imsdk.v2.V2TIMConversationResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun createReadAllCoordinator(store: ConversationListStore) = XingDunReadAllCoordinator(
    currentAccount = { V2TIMManager.getInstance().loginUser },
    findMarkedUnread = {
        val ids = mutableListOf<String>()
        var sequence = 0L
        do {
            val page = awaitReadAllSdk<V2TIMConversationResult> {
                V2TIMManager.getConversationManager().getConversationList(sequence, 100, it)
            }
            ids += page.conversationList
                .filter { it.markList.contains(V2TIMConversation.V2TIM_CONVERSATION_MARK_TYPE_UNREAD) }
                .map { it.conversationID }
            if (page.isFinished) break
            check(page.nextSeq != sequence) { "Unread conversation pagination did not advance" }
            sequence = page.nextSeq
        } while (true)
        ids
    },
    clearUnread = {
        suspendCancellableCoroutine<Unit> { continuation ->
            // Empty ID clears all C2C and group conversations, including unloaded/muted ones.
            store.clearConversationUnreadCount("", object : CompletionHandler {
                override fun onSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onFailure(code: Int, desc: String) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Clear unread failed ($code)"),
                    )
                }
            })
        }
    },
    clearMarks = { ids ->
        val results = awaitReadAllSdk<List<V2TIMConversationOperationResult>> {
            V2TIMManager.getConversationManager().markConversation(
                ids, V2TIMConversation.V2TIM_CONVERSATION_MARK_TYPE_UNREAD.toLong(), false, it,
            )
        }
        // SDK batch onSuccess does not imply every conversation succeeded.
        check(ids.all { id -> results.any { it.conversationID == id && it.resultCode == 0 } }) {
            "Some unread marks could not be cleared"
        }
    },
)

private suspend fun <T> awaitReadAllSdk(start: (V2TIMValueCallback<T>) -> Unit): T =
    suspendCancellableCoroutine { continuation ->
        start(object : V2TIMValueCallback<T> {
            override fun onSuccess(value: T) {
                if (continuation.isActive) continuation.resume(value)
            }
            override fun onError(code: Int, desc: String?) {
                if (continuation.isActive) continuation.resumeWithException(
                    IllegalStateException("Read-all SDK request failed ($code)"),
                )
            }
        })
    }
