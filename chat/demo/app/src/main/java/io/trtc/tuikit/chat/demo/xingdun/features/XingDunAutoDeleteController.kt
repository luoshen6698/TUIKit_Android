package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.util.Log
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Runs only while this conversation is visible; SDK deletion persists the local removal. */
internal class XingDunAutoDeleteController(
    context: Context,
    private val conversationID: String,
    private val scope: CoroutineScope,
    private val store: MessageListStore,
) {
    private val context = context.applicationContext
    private val account = XingDunSessionManager.currentSession()?.let { "${it.sdkAppId}:${it.companyId}:${it.timUserId}" }
    private var job: Job? = null
    @Volatile private var deleting = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var nextRefresh = 0L
            while (isActive && sameAccount()) {
                val now = System.currentTimeMillis()
                if (now >= nextRefresh) {
                    try {
                        XingDunAutoDeleteRepository.load(conversationID, force = true)
                        nextRefresh = now + 60_000
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        nextRefresh = now + 5_000
                    }
                }
                removeDueMessages()
                delay(1_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun sameAccount(): Boolean = account != null && account == XingDunSessionManager.currentSession()?.let {
        "${it.sdkAppId}:${it.companyId}:${it.timUserId}"
    }

    private fun removeDueMessages() {
        if (deleting || !sameAccount()) return
        val messages = store.state.messageList.value
        // Control messages may be delivered before this page opens, or arrive through history loading.
        messages.forEach { message ->
            val custom = XingDunCustomMessageParser.parse(message) ?: return@forEach
            when (custom.type) {
                "auto_delete_config" -> XingDunAutoDeleteRepository.applyRemote(conversationID, custom.values)
                "remote_delete" -> XingDunAutoDeleteRepository.applyRemoteDeletion(context, conversationID, custom.values)
            }
        }
        val configuration = XingDunAutoDeleteRepository.cached(conversationID)
        val deletedIDs = XingDunAutoDeleteRepository.deletedIDs(context, conversationID)
        val due = messages.filter { message ->
            val custom = XingDunCustomMessageParser.parse(message)
            val protectedControl = custom?.type in setOf("auto_delete_config", "remote_delete", "config_refresh")
            !protectedControl && (message.msgID in deletedIDs ||
                (configuration != null && message.status.name == "SEND_SUCCESS" && XingDunAutoDeletePolicy.isExpired(
                    message.timestamp, configuration.ttlSeconds, configuration.enabled,
                    configuration.updatedAt, System.currentTimeMillis(),
                )))
        }
        if (due.isEmpty()) return
        XingDunAutoDeleteRepository.rememberDeleted(context, conversationID, due.map { it.msgID }.toSet())
        deleting = true
        store.deleteMessages(due, object : CompletionHandler {
            override fun onSuccess() { deleting = false }
            override fun onFailure(code: Int, desc: String) {
                deleting = false
                Log.w("XingDunAutoDelete", "Local deletion failed; will retry, code=$code")
            }
        })
    }
}
