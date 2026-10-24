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

/** Shared production menu eligibility and confirmation/action flow. No state is hidden optimistically. */
internal class XingDunC2CDeletionActions(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val conversationID: String,
    private val userID: () -> String = { XingDunSessionManager.currentSession()?.timUserId.orEmpty() },
    private val account: () -> String? = XingDunC2CDeletionRepository::accountKey,
    private val delete: suspend (String, String) -> Unit = { conversation, message ->
        XingDunC2CDeletionRepository.delete(activity, conversation, message)
    },
    private val onResult: (Boolean) -> Unit = {},
    private val session: () -> Any? = XingDunSessionManager::currentAuthenticationGeneration,
) {
    private val activeIDs = mutableSetOf<String>()
    private var confirmingID: String? = null

    fun canOffer(message: MessageInfo): Boolean {
        val custom = XingDunCustomMessageParser.parse(message)
        return XingDunC2CDeletionPolicy.canDelete(
            conversationID, userID(), message.msgID,
            message.isSentBySelf && message.from.userID == userID(), message.status == MessageStatus.SEND_SUCCESS,
            custom?.isControl == true, custom?.type,
        )
    }

    fun confirm(message: MessageInfo) {
        if (!canOffer(message) || confirmingID != null || message.msgID in activeIDs) return
        val requestedAccount = account() ?: return
        val requestedSession = session() ?: return
        confirmingID = message.msgID
        AlertDialog.Builder(activity)
            .setTitle(R.string.xingdun_c2c_delete_confirm_title)
            .setMessage(R.string.xingdun_c2c_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_c2c_delete_action) { _, _ ->
                if (requestedAccount == account() && requestedSession == session() && canOffer(message)) {
                    perform(message.msgID, requestedAccount, requestedSession)
                }
            }
            .setOnDismissListener { confirmingID = null }
            .show()
    }

    private fun perform(messageID: String, requestedAccount: String, requestedSession: Any) {
        if (!activeIDs.add(messageID)) return
        scope.launch {
            try {
                check(requestedAccount == account() && requestedSession == session()) { "Authentication instance changed" }
                delete(conversationID, messageID)
                if (requestedAccount == account() && requestedSession == session() && !activity.isDestroyed) {
                    Toast.makeText(activity, R.string.xingdun_c2c_delete_success, Toast.LENGTH_SHORT).show()
                    onResult(true)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (requestedAccount == account() && requestedSession == session() && !activity.isDestroyed) {
                    Toast.makeText(activity, R.string.xingdun_c2c_delete_failed, Toast.LENGTH_LONG).show()
                    onResult(false)
                }
            } finally { activeIDs.remove(messageID) }
        }
    }
}
