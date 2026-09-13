package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getSystemInfoDisplayString
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TipsMessagePayload
import java.util.Locale

class SystemMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = false, useDefaultBubble = false)

    override fun createView(context: Context, parent: ViewGroup): View {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            val pad = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val textView = view as TextView
        textView.text = RecalledMessageDisplayPolicy.format(textView.context, message) ?: run {
            val payload = message.messagePayload as? TipsMessagePayload
            getSystemInfoDisplayString(
                textView.context,
                payload?.groupTips
            )
        }
        textView.setTextColor(colors.textColorSecondary)
    }
}

internal data class RecalledMessageDisplaySpec(
    val textResId: Int,
    val formatArg: String? = null,
)

internal enum class ManagementRecallActor {
    CUSTOMER_SERVICE,
    GROUP_MANAGER,
    SYSTEM_ADMINISTRATOR,
}

internal object RecalledMessageDisplayPolicy {
    fun format(context: Context, message: MessageInfo): String? {
        val spec = createSpec(message) ?: return null
        return spec.formatArg?.let { context.getString(spec.textResId, it) }
            ?: context.getString(spec.textResId)
    }

    fun createSpec(message: MessageInfo): RecalledMessageDisplaySpec? {
        if (message.status != MessageStatus.REVOKED) {
            return null
        }
        when (managementRecallActor(message.revokeReason, message.revokerInfo?.userID)) {
            ManagementRecallActor.CUSTOMER_SERVICE -> return RecalledMessageDisplaySpec(
                R.string.message_list_message_tips_customer_service_recall_message,
            )
            ManagementRecallActor.GROUP_MANAGER -> return RecalledMessageDisplaySpec(
                R.string.message_list_message_tips_group_manager_recall_message,
            )
            ManagementRecallActor.SYSTEM_ADMINISTRATOR -> return RecalledMessageDisplaySpec(
                R.string.message_list_message_tips_administrator_recall_message,
            )
            null -> Unit
        }
        if (message.isSentBySelf) {
            return RecalledMessageDisplaySpec(R.string.message_list_message_tips_you_recall_message)
        }
        val revokerName = message.revokerInfo?.nickname.takeUnless { it.isNullOrBlank() }
            ?: message.revokerInfo?.userID.takeUnless { it.isNullOrBlank() }
        if (!revokerName.isNullOrBlank()) {
            return RecalledMessageDisplaySpec(
                textResId = R.string.message_list_message_tips_recall_message_format,
                formatArg = revokerName,
            )
        }
        val textResId = if (message.conversationType == ConversationType.C2C) {
            R.string.message_list_message_tips_others_recall_message
        } else {
            R.string.message_list_message_tips_normal_recall_message
        }
        return RecalledMessageDisplaySpec(textResId)
    }

    internal fun managementRecallActor(reason: String?, revokerUserID: String?): ManagementRecallActor? {
        val normalizedReason = reason?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedReason.contains("xingdun management recall")) {
            if (normalizedReason.contains("customer_service")) {
                return ManagementRecallActor.CUSTOMER_SERVICE
            }
            if (normalizedReason.contains("group_manager")) {
                return ManagementRecallActor.GROUP_MANAGER
            }
        }
        return if (revokerUserID?.trim().equals("administrator", ignoreCase = true)) {
            ManagementRecallActor.SYSTEM_ADMINISTRATOR
        } else {
            null
        }
    }
}
