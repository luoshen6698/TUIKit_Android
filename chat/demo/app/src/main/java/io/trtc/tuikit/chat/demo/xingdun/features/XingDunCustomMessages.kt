package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContentRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageMatcher
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageSummaryProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryRegistry
import java.util.Locale

internal data class XingDunCustomMessage(
    val type: String,
    val values: Map<String, String>,
    val isControl: Boolean,
    val isXingDunEnvelope: Boolean
) {
    fun summary(context: Context): String {
        fun first(vararg names: String): String? = names.firstNotNullOfOrNull { values[it]?.trim()?.takeIf(String::isNotEmpty) }
        return when (type) {
            "redpacket" -> context.getString(
                R.string.xingdun_custom_redpacket_summary,
                first("greeting", "fallback_text") ?: context.getString(R.string.xingdun_redpacket_default_greeting)
            )
            "contact_card", "card" -> context.getString(
                R.string.xingdun_custom_contact_summary,
                first("display_name", "nickname") ?: context.getString(R.string.xingdun_custom_contact)
            )
            "call_record", "xingdun_call_record", "call", "audio_call", "video_call" -> {
                val video = type.contains("video") || first("call_type", "call_kind", "kind").equals("video", true)
                val title = context.getString(if (video) R.string.xingdun_video_call else R.string.xingdun_audio_call)
                val duration = first("duration_seconds", "duration")?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                if (duration == null) context.getString(R.string.xingdun_custom_call_summary, title)
                else context.getString(R.string.xingdun_custom_call_duration, title, formatDuration(duration))
            }
            "report_notice" -> context.getString(
                R.string.xingdun_custom_report_notice,
                first("status_text", "result") ?: context.getString(R.string.xingdun_updated)
            )
            "workspace_application" -> context.getString(
                R.string.xingdun_custom_workspace_notice,
                first("fallback_text", "application_no", "action") ?: context.getString(R.string.xingdun_updated)
            )
            "cs_control" -> context.getString(
                R.string.xingdun_custom_cs_notice,
                first("fallback_text", "text", "message", "content", "title")
                    ?: context.getString(R.string.xingdun_updated)
            )
            "config_refresh" -> first("fallback_text", "text") ?: context.getString(R.string.xingdun_custom_group_notice)
            else -> first("fallback_text", "text", "title") ?: context.getString(R.string.xingdun_custom_unsupported)
        }
    }

    fun detail(context: Context): String {
        if (type != "redpacket") return summary(context)
        val greeting = values["greeting"]?.trim()?.takeIf(String::isNotEmpty)
            ?: context.getString(R.string.xingdun_redpacket_default_greeting)
        return "$greeting\n${context.getString(R.string.xingdun_redpacket_closed_detail)}"
    }

    private fun formatDuration(seconds: Int): String = if (seconds >= 3_600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds % 3_600 / 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
}

internal object XingDunCustomMessageParser {
    private val knownTypes = setOf(
        "redpacket", "contact_card", "card", "call_record", "xingdun_call_record", "call", "audio_call",
        "video_call", "config_refresh", "auto_delete_config", "remote_delete", "pin_message",
        "read_receipt_summary", "report_notice", "workspace_application", "cs_control"
    )
    private val hiddenTypes = setOf("auto_delete_config", "remote_delete", "pin_message", "read_receipt_summary")

    fun parse(message: MessageInfo): XingDunCustomMessage? {
        if (message.messageType != MessageType.CUSTOM) return null
        val payload = message.messagePayload as? CustomMessagePayload ?: return null
        return parse(payload.customData, payload.description)
    }

    internal fun parse(data: String?, description: String? = null): XingDunCustomMessage? {
        val root = runCatching { JsonParser.parseString(data.orEmpty()).asJsonObject }.getOrNull() ?: return null
        val values = linkedMapOf<String, String>()
        flatten(root, values)
        val explicitType = listOf("xd_type", "type", "message_type", "businessType", "name")
            .firstNotNullOfOrNull { values[it]?.trim()?.takeIf(String::isNotEmpty) }
        val descriptionType = description?.trim()?.removePrefix("XingDun:")?.takeIf(String::isNotEmpty)
        val type = (explicitType ?: descriptionType ?: "custom").lowercase(Locale.ROOT)
        val isXingDunEnvelope = explicitType != null || description?.startsWith("XingDun:") == true
        if (type !in knownTypes && !isXingDunEnvelope) return null
        val isControl = type in hiddenTypes ||
            (type == "config_refresh" && !values["scope"].equals("group", ignoreCase = true))
        return XingDunCustomMessage(type, values, isControl, isXingDunEnvelope)
    }

    private fun flatten(json: JsonObject, destination: MutableMap<String, String>) {
        json.entrySet().forEach { (key, value) ->
            if ((key == "payload" || key == "data") && value.isJsonObject) {
                flatten(value.asJsonObject, destination)
            } else if ((key == "payload" || key == "data") && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                runCatching { JsonParser.parseString(value.asString).asJsonObject }.getOrNull()?.let {
                    flatten(it, destination)
                }
            }
            if (key !in destination) stringify(value)?.let { destination[key] = it }
        }
    }

    private fun stringify(value: JsonElement): String? = when {
        value.isJsonNull -> null
        value.isJsonPrimitive -> value.asJsonPrimitive.asString
        else -> value.toString()
    }
}

internal object XingDunCustomMessagePresentation {
    private val matcher = MessageMatcher { message ->
        XingDunCustomMessageParser.parse(message)?.isControl == false
    }
    private val controlMatcher = MessageMatcher { message ->
        XingDunCustomMessageParser.parse(message)?.isControl == true
    }
    private val summaryProvider = MessageSummaryProvider { context ->
        XingDunCustomMessageParser.parse(context.message)?.takeUnless(XingDunCustomMessage::isControl)?.summary(context.context)
    }

    fun registerGlobalSummaries() {
        MessageListMessageSummaryRegistry.addCustomMessageSummary(matcher, summaryProvider, priority = 100)
    }

    fun configure(config: ChatMessageListConfig) {
        config.addMessageExclusion(controlMatcher)
        config.addCustomMessageRenderer(
            matcher = matcher,
            renderer = XingDunCustomMessageRenderer,
            priority = 100,
            summaryProvider = summaryProvider
        )
    }
}

private object XingDunCustomMessageRenderer : MessageContentRenderer {
    override val renderConfig = MessageRenderConfig(
        showMessageMeta = true,
        useDefaultBubble = false,
        bubbleStyle = BubbleStyle.CARD
    )

    override fun createView(context: Context, parent: ViewGroup): View = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.2f)
        val density = resources.displayMetrics.density
        setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
        layoutParams = LinearLayout.LayoutParams(
            (248 * density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun bindView(view: View, context: MessageRenderContext) {
        val textView = view as TextView
        val message = XingDunCustomMessageParser.parse(context.message) ?: return
        textView.text = message.detail(textView.context)
        val backgroundColor = if (message.type == "redpacket") 0xFFD83B32.toInt() else context.colors.bgColorInput
        val textColor = if (message.type == "redpacket") 0xFFFFFFFF.toInt() else context.colors.textColorPrimary
        textView.setTextColor(textColor)
        textView.background = GradientDrawable().apply {
            setColor(backgroundColor)
            cornerRadius = 12f * textView.resources.displayMetrics.density
        }
        textView.setOnClickListener(
            if (message.type == "redpacket") View.OnClickListener {
                Toast.makeText(it.context, R.string.xingdun_redpacket_closed_detail, Toast.LENGTH_SHORT).show()
            } else null
        )
    }
}
