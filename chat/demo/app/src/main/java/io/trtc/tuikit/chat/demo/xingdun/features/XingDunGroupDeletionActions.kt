package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Eligibility is an expiring server sender-capability, not owner/admin/assigned-CS inference. */
internal class XingDunGroupDeletionActions(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val conversationID: String,
) {
    private val allowedUntil = mutableMapOf<String, Long>()
    private val checkedUntil = mutableMapOf<String, Long>()
    private val generation = XingDunSessionManager.currentAuthenticationGeneration()
    private var confirming: String? = null
    private val active = mutableSetOf<String>()
    private fun isCurrent() = generation != null && generation == XingDunSessionManager.currentAuthenticationGeneration() && !activity.isDestroyed

    suspend fun refresh(messages: List<MessageInfo>, force: Boolean = false) {
        if (!isCurrent()) return
        val self = XingDunSessionManager.currentSession()?.timUserId
        val now = System.currentTimeMillis()
        val senders = messages.filter(::eligible).map { it.from.userID }
            .filter { it != self && XingDunGroupDeletionPolicy.validSender(it) }
            .filter { force || now >= (checkedUntil[it] ?: 0L) }.toSet()
        for (chunk in senders.chunked(100)) {
            val ids = chunk.toSet()
            try {
                val allowed = XingDunGroupDeletionRepository.allowed(activity, conversationID, ids)
                if (!isCurrent()) return
                ids.forEach { allowedUntil.remove(it) }
                ids.forEach { checkedUntil[it] = System.currentTimeMillis() + 15_000 }
                allowed.forEach { allowedUntil[it] = System.currentTimeMillis() + 15_000 }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { ids.forEach { allowedUntil.remove(it); checkedUntil.remove(it) } }
        }
    }

    private fun eligible(message: MessageInfo): Boolean = XingDunGroupDeletionPolicy.canOffer(
        conversationID, XingDunSessionManager.currentSession()?.timUserId.orEmpty(), message.msgID, message.from.userID,
        message.isSentBySelf, message.status == MessageStatus.SEND_SUCCESS,
        XingDunCustomMessageParser.parse(message)?.isControl == true, allowedByServer = true,
    ) && !XingDunGroupDeletionRepository.isHidden(conversationID, message.msgID)

    fun canOffer(message: MessageInfo): Boolean = isCurrent() && eligible(message) &&
        System.currentTimeMillis() < (allowedUntil[message.from.userID] ?: 0L)

    fun confirm(message: MessageInfo) {
        if (!canOffer(message) || confirming != null || message.msgID in active) return
        confirming = message.msgID
        AlertDialog.Builder(activity)
            .setTitle(R.string.xingdun_group_delete_confirm_title)
            .setMessage(R.string.xingdun_group_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_group_delete_action) { _, _ ->
                if (isCurrent() && eligible(message)) perform(message)
            }
            .setOnDismissListener { confirming = null }
            .show()
    }

    private fun perform(message: MessageInfo) {
        if (!active.add(message.msgID)) return
        scope.launch {
            try {
                check(isCurrent()) { "Authentication instance changed" }
                XingDunGroupDeletionRepository.delete(activity, conversationID, message.msgID, message.from.userID)
                if (isCurrent()) Toast.makeText(activity, R.string.xingdun_group_delete_success, Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                allowedUntil.remove(message.from.userID)
                if (isCurrent()) Toast.makeText(activity, R.string.xingdun_group_delete_failed, Toast.LENGTH_LONG).show()
            } finally { active.remove(message.msgID) }
        }
    }
}
