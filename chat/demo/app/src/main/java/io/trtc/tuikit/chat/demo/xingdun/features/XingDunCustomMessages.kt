package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunRuntimeFeaturePolicy
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContentRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageMatcher
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageSummaryProvider
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryRegistry
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class XingDunContactCardPayload(
    val userID: String,
    val customID: String?,
    val displayName: String,
    val avatarURL: String?,
    val department: String?,
)

internal data class XingDunCustomMessage(
    val type: String,
    val values: Map<String, String>,
    val isControl: Boolean,
    val isXingDunEnvelope: Boolean
) {
    fun contactCard(): XingDunContactCardPayload? {
        if (type !in CONTACT_CARD_TYPES) return null
        fun first(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            values[name]?.trim()?.takeIf(String::isNotEmpty)
        }
        val userID = first("accid", "user_id", "userId", "tim_user_id", "timUserId", "identifier") ?: return null
        return XingDunContactCardPayload(
            userID = userID,
            customID = first("custom_id", "customId", "account", "username"),
            displayName = first("display_name", "displayName", "nickname", "name") ?: userID,
            avatarURL = first("avatar_url", "avatarURL", "avatar"),
            department = first("department", "department_name", "departmentName"),
        )
    }

    fun summary(context: Context): String {
        fun first(vararg names: String): String? = names.firstNotNullOfOrNull { values[it]?.trim()?.takeIf(String::isNotEmpty) }
        return when (type) {
            "redpacket" -> context.getString(
                R.string.xingdun_custom_redpacket_summary,
                first("greeting", "fallback_text") ?: context.getString(R.string.xingdun_redpacket_default_greeting)
            )
            "contact_card", "xingdun_contact_card", "card" -> context.getString(
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
        val status = XingDunRedpacketAccessPolicy.statusText(context, values)
        val count = values["count"]?.toIntOrNull()?.takeIf { it > 1 }
        return buildString {
            append(greeting).append('\n').append(status).append('\n')
            append(context.getString(if (values["packet_type"] == "team_exclusive") R.string.xingdun_redpacket_exclusive else R.string.xingdun_redpacket_normal))
            if (count != null) append(" · ").append(
                context.resources.getQuantityString(R.plurals.xingdun_redpacket_total_count, count, count),
            )
        }
    }

    private fun formatDuration(seconds: Int): String = if (seconds >= 3_600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds % 3_600 / 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private companion object {
        val CONTACT_CARD_TYPES = setOf("contact_card", "xingdun_contact_card", "card")
    }
}

internal object XingDunRedpacketAccessPolicy {
    fun canOpen(featureEnabled: Boolean): Boolean = featureEnabled

    fun statusText(context: Context, values: Map<String, String>): String {
        if (values["has_claimed"].equals("true", true) || values["has_claimed"] == "1") {
            return context.getString(R.string.xingdun_redpacket_claimed)
        }
        return when (values["status"]?.toIntOrNull()) {
            1 -> context.getString(R.string.xingdun_redpacket_sending)
            2 -> context.getString(R.string.xingdun_redpacket_receive)
            3 -> context.getString(R.string.xingdun_redpacket_exhausted)
            4, 5 -> context.getString(R.string.xingdun_redpacket_expired)
            6 -> context.getString(R.string.xingdun_redpacket_cancelled)
            else -> values["status_name"]?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.xingdun_redpacket_status_loading)
        }
    }
}

internal object XingDunRedpacketClaimStatusPolicy {
    fun values(result: XingDunRedpacketClaimResultPayload): Map<String, String> = mapOf(
        "status" to (result.detail.status.takeIf { it > 0 } ?: result.status).toString(),
        "status_name" to result.detail.statusName.ifBlank { result.statusName },
        "has_claimed" to "true",
        "claimed_count" to result.detail.claimedCount.toString(),
        "count" to result.detail.count.toString(),
        "remain_count" to result.detail.remainCount.toString(),
    )
}

internal object XingDunRedpacketStatusLoader {
    private data class CachedStatus(val values: Map<String, String>, val loadedAtMillis: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, CachedStatus>()
    private val listeners = ConcurrentHashMap<String, MutableSet<(Map<String, String>) -> Unit>>()

    fun load(
        packetNo: String,
        forceRefresh: Boolean = false,
        completion: (Map<String, String>) -> Unit,
    ) {
        val session = XingDunSessionManager.currentSession() ?: return
        val tenantKey = XingDunTenantBoundary.identity(session)?.key ?: return
        val cacheKey = "$tenantKey:$packetNo"
        val cached = cache[cacheKey]
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.loadedAtMillis < CACHE_TTL_MILLIS) {
            completion(cached.values)
            return
        }
        scope.launch {
            val values = runCatching {
                val response = XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "redpacket/batchStatus",
                    mapOf("packet_nos" to packetNo),
                    JsonObject::class.java
                )
                val item = response.getAsJsonObject(packetNo) ?: return@runCatching emptyMap()
                buildMap {
                    item.entrySet().forEach { (key, value) ->
                        if (!value.isJsonNull && value.isJsonPrimitive) put(key, value.asString)
                    }
                }
            }.getOrNull() ?: return@launch
            val stillCurrentTenant = XingDunTenantBoundary.identity(XingDunSessionManager.currentSession())?.key == tenantKey
            if (values.isNotEmpty() && stillCurrentTenant) {
                cache[cacheKey] = CachedStatus(values, System.currentTimeMillis())
            }
            if (stillCurrentTenant) mainHandler.post { completion(values) }
        }
    }

    fun invalidate(packetNo: String) {
        val session = XingDunSessionManager.currentSession() ?: return
        val tenantKey = XingDunTenantBoundary.identity(session)?.key ?: return
        cache.remove("$tenantKey:$packetNo")
    }

    fun addListener(packetNo: String, listener: (Map<String, String>) -> Unit) {
        listeners.computeIfAbsent(packetNo) { ConcurrentHashMap.newKeySet() }.add(listener)
    }

    fun removeListener(packetNo: String, listener: (Map<String, String>) -> Unit) {
        listeners[packetNo]?.let { packetListeners ->
            packetListeners.remove(listener)
            if (packetListeners.isEmpty()) listeners.remove(packetNo, packetListeners)
        }
    }

    fun notifyChanged(packetNo: String) {
        invalidate(packetNo)
        notifyListeners(packetNo, emptyMap())
    }

    fun publish(packetNo: String, values: Map<String, String>) {
        if (values.isEmpty()) return notifyChanged(packetNo)
        val session = XingDunSessionManager.currentSession() ?: return
        val tenantKey = XingDunTenantBoundary.identity(session)?.key ?: return
        cache["$tenantKey:$packetNo"] = CachedStatus(values, System.currentTimeMillis())
        notifyListeners(packetNo, values)
    }

    fun clearTenantCache() {
        cache.clear()
    }

    private fun notifyListeners(packetNo: String, values: Map<String, String>) {
        val notify: () -> Unit = { listeners[packetNo]?.toList()?.forEach { it(values) } }
        if (Looper.myLooper() == Looper.getMainLooper()) notify() else mainHandler.post { notify() }
    }

    private const val CACHE_TTL_MILLIS = 30_000L
}

internal object XingDunCustomMessageParser {
    private val knownTypes = setOf(
        "redpacket", "contact_card", "xingdun_contact_card", "card", "call_record", "xingdun_call_record", "call", "audio_call",
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
    private val contactCardMatcher = MessageMatcher { message ->
        XingDunCustomMessageParser.parse(message)?.takeUnless(XingDunCustomMessage::isControl)?.contactCard() != null
    }
    private val redpacketMatcher = MessageMatcher { message ->
        XingDunCustomMessageParser.parse(message)?.let { it.type == "redpacket" && !it.isControl } == true
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
            matcher = contactCardMatcher,
            renderer = XingDunContactCardMessageRenderer,
            priority = 200,
            summaryProvider = summaryProvider
        )
        config.addCustomMessageRenderer(
            matcher = redpacketMatcher,
            renderer = XingDunRedpacketMessageRenderer,
            priority = 190,
            summaryProvider = summaryProvider
        )
        config.addCustomMessageRenderer(
            matcher = matcher,
            renderer = XingDunCustomMessageRenderer,
            priority = 100,
            summaryProvider = summaryProvider
        )
    }

    fun redpacketPreview(context: Context, packetNo: String, status: Int, hasClaimed: Boolean = false): View =
        XingDunRedpacketMessageView(context).apply {
            bind(
                XingDunCustomMessage(
                    type = "redpacket",
                    values = mapOf(
                        "packet_no" to packetNo,
                        "greeting" to context.getString(R.string.xingdun_redpacket_default_greeting),
                        "packet_type" to "team_random",
                        "count" to "3",
                        "status" to status.toString(),
                        "has_claimed" to hasClaimed.toString(),
                    ),
                    isControl = false,
                    isXingDunEnvelope = true,
                ),
            )
        }
}

private object XingDunRedpacketMessageRenderer : MessageContentRenderer {
    override val renderConfig = MessageRenderConfig(
        showMessageMeta = true,
        useDefaultBubble = false,
        bubbleStyle = BubbleStyle.CARD,
    )

    override fun createView(context: Context, parent: ViewGroup): View = XingDunRedpacketMessageView(context)

    override fun bindView(view: View, context: MessageRenderContext) {
        val message = XingDunCustomMessageParser.parse(context.message) ?: return
        (view as XingDunRedpacketMessageView).bind(message)
    }
}

private class XingDunRedpacketMessageView(context: Context) : LinearLayout(context) {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var boundMessage: XingDunCustomMessage? = null
    private var boundPacketNo: String? = null
    private var observedPacketNo: String? = null
    private var statusLoadGeneration = 0
    private var statusRefreshEnabled = false
    private val statusChangeListener: (Map<String, String>) -> Unit = { values ->
        if (values.isEmpty()) {
            loadStatus(forceRefresh = true)
        } else {
            statusLoadGeneration += 1
            applyStatusValues(boundPacketNo, values)
        }
    }
    private val refreshRunnable = Runnable {
        loadStatus(forceRefresh = true)
        scheduleStatusRefresh()
    }
    private val gift = ImageView(context).apply {
        setImageResource(R.drawable.xingdun_ic_gift_white)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val greeting = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        maxLines = 2
    }
    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(0xE0FFFFFF.toInt())
        maxLines = 1
    }
    private val footer = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 7.dp(), 14.dp(), 7.dp())
    }
    private val kind = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(0xE8FFFFFF.toInt())
    }
    private val count = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(0xE8FFFFFF.toInt())
        gravity = Gravity.END
    }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        layoutParams = LayoutParams(248.dp(), ViewGroup.LayoutParams.WRAP_CONTENT)

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 13.dp(), 14.dp(), 13.dp())
            addView(gift, LayoutParams(42.dp(), 42.dp()))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(greeting, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(status, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 3.dp()
                })
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12.dp() })
        })
        footer.addView(kind, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(count, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(footer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun bind(message: XingDunCustomMessage) {
        val packetNo = message.values["packet_no"] ?: message.values["packetNo"]
        stopObservingStatus()
        boundMessage = message
        boundPacketNo = packetNo
        tag = packetNo
        render(message)
        val enabled = XingDunRuntimeFeaturePolicy.redpacketEnabled(XingDunSessionManager.currentSession()?.features)
        statusRefreshEnabled = enabled && !packetNo.isNullOrBlank()
        setOnClickListener(if (XingDunRedpacketAccessPolicy.canOpen(enabled) && !packetNo.isNullOrBlank()) {
            OnClickListener {
                XingDunFeatureActivity.start(it.context, XingDunFeatureActivity.MODE_REDPACKET_DETAIL, packetNo)
            }
        } else null)
        observeStatus()
        loadStatus()
        scheduleStatusRefresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeStatus()
        loadStatus(forceRefresh = true)
        scheduleStatusRefresh()
    }

    override fun onDetachedFromWindow() {
        refreshHandler.removeCallbacks(refreshRunnable)
        stopObservingStatus()
        super.onDetachedFromWindow()
    }

    private fun loadStatus(forceRefresh: Boolean = false) {
        val packetNo = boundPacketNo ?: return
        if (boundMessage == null) return
        if (!statusRefreshEnabled) return
        val generation = ++statusLoadGeneration
        XingDunRedpacketStatusLoader.load(packetNo, forceRefresh) { statusValues ->
            if (tag == packetNo && generation == statusLoadGeneration && statusValues.isNotEmpty()) {
                applyStatusValues(packetNo, statusValues)
            }
        }
    }

    private fun applyStatusValues(packetNo: String?, statusValues: Map<String, String>) {
        if (packetNo.isNullOrBlank() || tag != packetNo || statusValues.isEmpty()) return
        val message = boundMessage ?: return
        val updated = message.copy(values = message.values + statusValues)
        boundMessage = updated
        render(updated)
        val statusValue = updated.values["status"]?.toIntOrNull() ?: 0
        val claimed = updated.values["has_claimed"].equals("true", true) || updated.values["has_claimed"] == "1"
        if (XingDunRedpacketPresentationPolicy.isTerminal(statusValue, claimed)) {
            statusRefreshEnabled = false
            refreshHandler.removeCallbacks(refreshRunnable)
            stopObservingStatus()
        }
    }

    private fun observeStatus() {
        val packetNo = boundPacketNo ?: return
        if (!isAttachedToWindow || !statusRefreshEnabled || observedPacketNo == packetNo) return
        observedPacketNo = packetNo
        XingDunRedpacketStatusLoader.addListener(packetNo, statusChangeListener)
    }

    private fun stopObservingStatus() {
        observedPacketNo?.let { XingDunRedpacketStatusLoader.removeListener(it, statusChangeListener) }
        observedPacketNo = null
    }

    private fun scheduleStatusRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (isAttachedToWindow && statusRefreshEnabled) {
            refreshHandler.postDelayed(refreshRunnable, STATUS_REFRESH_INTERVAL_MILLIS)
        }
    }

    private fun render(message: XingDunCustomMessage) {
        val greetingText = message.values["greeting"]?.trim()?.takeIf(String::isNotEmpty)
            ?: context.getString(R.string.xingdun_redpacket_default_greeting)
        val statusText = XingDunRedpacketAccessPolicy.statusText(context, message.values)
        val statusValue = message.values["status"]?.toIntOrNull() ?: 0
        val claimed = message.values["has_claimed"].equals("true", true) || message.values["has_claimed"] == "1"
        val terminal = XingDunRedpacketPresentationPolicy.isTerminal(statusValue, claimed)
        val packetCount = message.values["count"]?.toIntOrNull()?.takeIf { it > 1 }
        greeting.text = greetingText
        status.text = statusText
        kind.setText(if (message.values["packet_type"] == "team_exclusive") R.string.xingdun_redpacket_exclusive else R.string.xingdun_redpacket_normal)
        count.text = packetCount?.let {
            context.resources.getQuantityString(R.plurals.xingdun_redpacket_total_count, it, it)
        }.orEmpty()
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (terminal) intArrayOf(0xFF945C54.toInt(), 0xFF7A4945.toInt())
            else intArrayOf(0xFFE8453A.toInt(), 0xFFC51F24.toInt()),
        ).apply { cornerRadius = 12.dp().toFloat() }
        footer.setBackgroundColor(if (terminal) 0x24000000 else 0x14000000)
        contentDescription = context.getString(
            R.string.xingdun_redpacket_message_accessibility,
            if (message.values["packet_type"] == "team_exclusive") context.getString(R.string.xingdun_redpacket_exclusive) else context.getString(R.string.xingdun_redpacket_normal),
            greetingText,
            statusText,
        )
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATUS_REFRESH_INTERVAL_MILLIS = 1_000L
    }
}

private object XingDunContactCardMessageRenderer : MessageContentRenderer {
    override val renderConfig = MessageRenderConfig(
        showMessageMeta = true,
        useDefaultBubble = false,
        bubbleStyle = BubbleStyle.CARD
    )

    override fun createView(context: Context, parent: ViewGroup): View = XingDunContactCardView(context)

    override fun bindView(view: View, context: MessageRenderContext) {
        val payload = XingDunCustomMessageParser.parse(context.message)?.contactCard() ?: return
        (view as XingDunContactCardView).bind(payload, context)
    }
}

private class XingDunContactCardView(context: Context) : LinearLayout(context) {
    private val avatar = Avatar(context).apply {
        setSize(Avatar.AvatarSize.L)
        setShape(Avatar.AvatarShape.Round)
    }
    private val name = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
    }
    private val account = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        maxLines = 1
    }
    private val divider = View(context)
    private val footerIcon = ImageView(context).apply {
        setImageResource(R.drawable.xingdun_ic_contact_card)
    }
    private val footer = TextView(context).apply {
        setText(R.string.xingdun_custom_contact_card_footer)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(14.dp(), 13.dp(), 14.dp(), 10.dp())
        layoutParams = LayoutParams(270.dp(), ViewGroup.LayoutParams.WRAP_CONTENT)

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(avatar, LayoutParams(48.dp(), 48.dp()))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12.dp(), 0, 0, 0)
                addView(name, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(account, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 3.dp()
                })
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(divider, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
            topMargin = 12.dp()
            bottomMargin = 8.dp()
        })
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(footerIcon, LayoutParams(15.dp(), 15.dp()))
            addView(footer, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = 6.dp()
            })
        })
    }

    fun bind(payload: XingDunContactCardPayload, renderContext: MessageRenderContext) {
        val accountText = payload.userID
        avatar.setContent(Avatar.AvatarContent.Image(payload.avatarURL, payload.displayName))
        name.text = payload.displayName
        account.text = accountText
        name.setTextColor(renderContext.colors.textColorPrimary)
        account.setTextColor(renderContext.colors.textColorSecondary)
        footer.setTextColor(renderContext.colors.textColorSecondary)
        footerIcon.imageTintList = ColorStateList.valueOf(renderContext.colors.textColorSecondary)
        divider.setBackgroundColor(renderContext.colors.strokeColorPrimary)
        background = GradientDrawable().apply {
            setColor(renderContext.colors.bgColorOperate)
            setStroke(1.dp(), renderContext.colors.strokeColorPrimary)
            cornerRadius = 12.dp().toFloat()
        }
        contentDescription = context.getString(
            R.string.xingdun_custom_contact_card_accessibility,
            payload.displayName,
            accountText,
        )
        setOnClickListener {
            XingDunContactDetailActivity.start(
                context = it.context,
                userID = payload.userID,
                customID = payload.customID,
                nickname = payload.displayName,
                avatar = payload.avatarURL,
            )
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
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
        val packetNo = message.values["packet_no"] ?: message.values["packetNo"]
        textView.tag = packetNo
        textView.text = message.detail(textView.context)
        val backgroundColor = if (message.type == "redpacket") 0xFFD83B32.toInt() else context.colors.bgColorInput
        val textColor = if (message.type == "redpacket") 0xFFFFFFFF.toInt() else context.colors.textColorPrimary
        textView.setTextColor(textColor)
        textView.background = GradientDrawable().apply {
            setColor(backgroundColor)
            cornerRadius = 12f * textView.resources.displayMetrics.density
        }
        val redpacketEnabled = XingDunRuntimeFeaturePolicy.redpacketEnabled(
            XingDunSessionManager.currentSession()?.features,
        )
        textView.setOnClickListener(
            if (message.type == "redpacket" && XingDunRedpacketAccessPolicy.canOpen(redpacketEnabled)) View.OnClickListener {
                val destination = packetNo ?: return@OnClickListener
                XingDunFeatureActivity.start(it.context, XingDunFeatureActivity.MODE_REDPACKET_DETAIL, destination)
            } else null
        )
        if (message.type == "redpacket" && redpacketEnabled && !packetNo.isNullOrBlank()) {
            XingDunRedpacketStatusLoader.load(packetNo) { statusValues ->
                if (textView.tag == packetNo && statusValues.isNotEmpty()) {
                    textView.text = message.copy(values = message.values + statusValues).detail(textView.context)
                }
            }
        }
    }
}
