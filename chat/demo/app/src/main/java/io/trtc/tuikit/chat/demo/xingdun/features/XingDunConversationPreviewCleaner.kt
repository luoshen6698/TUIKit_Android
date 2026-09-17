package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.util.Log
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMConversation
import com.tencent.imsdk.v2.V2TIMConversationResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object XingDunConversationPreviewPolicy {
    fun shouldRemoveFromLocalHistory(message: XingDunCustomMessage?): Boolean =
        message?.type == "remote_delete" && message.isControl
}

/**
 * Removes the invisible remote-delete envelope after applying it locally.
 * Tencent IM then promotes the latest remaining chat message as the conversation preview.
 */
internal object XingDunConversationPreviewCleaner {
    private const val PAGE_SIZE = 100
    private const val TAG = "XDConversationPreview"
    private val cleanupInFlight = ConcurrentHashMap.newKeySet<String>()
    private val scanInFlight = AtomicBoolean(false)

    @Volatile
    private var scannedAccount: String? = null

    fun onRemoteDeleteReceived(
        context: Context,
        conversationID: String,
        values: Map<String, String>,
        controlMessage: V2TIMMessage,
    ) {
        XingDunAutoDeleteRepository.applyRemoteDeletion(context, conversationID, values)
        removeRemoteDeleteEnvelope(context, conversationID, values, controlMessage)
    }

    fun cleanupExistingConversationPreviews(context: Context) {
        val account = accountKey() ?: return
        if (scannedAccount == account || !scanInFlight.compareAndSet(false, true)) return
        loadConversationPage(context.applicationContext, account, 0L)
    }

    fun resetAccountState() {
        scannedAccount = null
        scanInFlight.set(false)
        cleanupInFlight.clear()
    }

    private fun loadConversationPage(context: Context, account: String, nextSequence: Long) {
        V2TIMManager.getConversationManager().getConversationList(
            nextSequence,
            PAGE_SIZE,
            object : V2TIMValueCallback<V2TIMConversationResult> {
                override fun onSuccess(result: V2TIMConversationResult?) {
                    if (accountKey() != account) {
                        scanInFlight.set(false)
                        return
                    }
                    result?.conversationList.orEmpty().forEach { conversation ->
                        cleanupConversation(context, conversation)
                    }
                    if (result != null && !result.isFinished) {
                        loadConversationPage(context, account, result.nextSeq)
                    } else {
                        scannedAccount = account
                        scanInFlight.set(false)
                    }
                }

                override fun onError(code: Int, desc: String?) {
                    scanInFlight.set(false)
                    Log.w(TAG, "Failed to scan conversation previews, code=$code")
                }
            },
        )
    }

    private fun cleanupConversation(context: Context, conversation: V2TIMConversation) {
        val controlMessage = conversation.lastMessage ?: return
        val custom = parse(controlMessage) ?: return
        if (!XingDunConversationPreviewPolicy.shouldRemoveFromLocalHistory(custom)) return
        XingDunAutoDeleteRepository.applyRemoteDeletion(context, conversation.conversationID, custom.values)
        removeRemoteDeleteEnvelope(context, conversation.conversationID, custom.values, controlMessage)
    }

    private fun removeRemoteDeleteEnvelope(
        context: Context,
        conversationID: String,
        values: Map<String, String>,
        controlMessage: V2TIMMessage,
    ) {
        val cleanupKey = "$conversationID:${controlMessage.msgID}"
        if (!cleanupInFlight.add(cleanupKey)) return
        val targetIDs = XingDunAutoDeletePolicy.remoteDeletedIDs(values["message_ids"])
        if (targetIDs.isEmpty()) {
            deleteLocally(context, conversationID, cleanupKey, listOf(controlMessage))
            return
        }
        V2TIMManager.getMessageManager().findMessages(
            targetIDs.toList(),
            object : V2TIMValueCallback<List<V2TIMMessage>> {
                override fun onSuccess(messages: List<V2TIMMessage>?) {
                    deleteLocally(
                        context,
                        conversationID,
                        cleanupKey,
                        messages.orEmpty().filter { it.msgID in targetIDs } + controlMessage,
                    )
                }

                override fun onError(code: Int, desc: String?) {
                    Log.w(TAG, "Failed to resolve auto-deleted messages, code=$code")
                    deleteLocally(context, conversationID, cleanupKey, listOf(controlMessage))
                }
            },
        )
    }

    private fun deleteLocally(
        context: Context,
        conversationID: String,
        cleanupKey: String,
        messages: List<V2TIMMessage>,
    ) {
        val queue = messages.distinctBy(V2TIMMessage::getMsgID).toMutableList()
        fun deleteNext() {
            val message = queue.removeFirstOrNull()
            if (message == null) {
                cleanupInFlight.remove(cleanupKey)
                refreshConversation(context, conversationID)
                return
            }
            V2TIMManager.getMessageManager().deleteMessageFromLocalStorage(
                message,
                object : V2TIMCallback {
                    override fun onSuccess() = deleteNext()

                    override fun onError(code: Int, desc: String?) {
                        Log.w(TAG, "Failed to remove local control message, code=$code")
                        deleteNext()
                    }
                },
            )
        }
        deleteNext()
    }

    private fun refreshConversation(context: Context, conversationID: String) {
        V2TIMManager.getConversationManager().getConversation(
            conversationID,
            object : V2TIMValueCallback<V2TIMConversation> {
                override fun onSuccess(conversation: V2TIMConversation?) {
                    conversation?.let { cleanupConversation(context, it) }
                }

                override fun onError(code: Int, desc: String?) = Unit
            },
        )
    }

    private fun parse(message: V2TIMMessage): XingDunCustomMessage? {
        if (message.elemType != V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM) return null
        val element = message.customElem ?: return null
        return XingDunCustomMessageParser.parse(
            element.data?.toString(Charsets.UTF_8),
            element.description,
        )
    }

    private fun accountKey(): String? = XingDunSessionManager.currentSession()?.let {
        "${it.sdkAppId}:${it.companyId}:${it.timUserId}"
    }
}
