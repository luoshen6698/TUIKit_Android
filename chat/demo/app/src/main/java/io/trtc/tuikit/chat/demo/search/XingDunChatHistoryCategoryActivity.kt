package io.trtc.tuikit.chat.demo.search

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.FileMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageListType
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewer
import io.trtc.tuikit.chat.uikit.components.search.utils.getMessageAbstract
import io.trtc.tuikit.chat.uikit.components.search.utils.messageSender
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** iOS-aligned category browser for chat history. */
open class XingDunChatHistoryCategoryActivity : BaseActivity() {
    override val requiresLogin: Boolean get() = !isDebugPreview

    private val conversationID by lazy { intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty() }
    private val mode by lazy { intent.getStringExtra(EXTRA_MODE).orEmpty() }
    private val memberID by lazy { intent.getStringExtra(EXTRA_MEMBER_ID) }
    private val memberName by lazy { intent.getStringExtra(EXTRA_MEMBER_NAME) }
    private val selectedDay by lazy { intent.getLongExtra(EXTRA_SELECTED_DAY, 0L) }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private val themeStore by lazy { ThemeStore.shared(this) }
    private val messageStore by lazy { MessageListStore.create(conversationID) }
    private var activityScope: CoroutineScope? = null
    private var messages: List<MessageInfo> = emptyList()

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: ImageView
    private lateinit var divider: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (conversationID.isBlank() || mode !in MODES) {
            finish()
            return
        }
        setContentView(R.layout.xingdun_activity_profile_editor)
        bindViews()
        configureHeader()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch { themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) } }
        if (mode == MODE_DATE) showDatePicker() else loadMessages()
    }

    override fun onDestroy() {
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.xingdun_profileEditorRoot)
        header = findViewById(R.id.demo_chatHeaderContainer)
        title = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        divider = findViewById(R.id.demo_headerDivider)
        scroll = findViewById(R.id.xingdun_profileEditorScroll)
        content = findViewById(R.id.xingdun_profileEditorContent)
        findViewById<ImageView>(R.id.demo_btnMore).visibility = View.GONE
        findViewById<FrameLayout>(R.id.demo_badgeContainer).visibility = View.GONE
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(20.dp(), 72.dp(), 20.dp(), 72.dp())
            visibility = View.GONE
            tag = TAG_SECONDARY
        }
    }

    private fun configureHeader() {
        title.text = when (mode) {
            MODE_MEMBER -> getString(R.string.xingdun_chat_history_by_member)
            MODE_MEMBER_RESULTS -> memberName.orEmpty().ifBlank { memberID.orEmpty() }
            MODE_IMAGE -> getString(R.string.xingdun_chat_history_image)
            MODE_VIDEO -> getString(R.string.xingdun_chat_history_video)
            MODE_FILE -> getString(R.string.xingdun_chat_history_file)
            MODE_DATE -> getString(R.string.xingdun_chat_history_by_date)
            MODE_DATE_RESULTS -> DateFormat.getMediumDateFormat(this).format(Date(selectedDay))
            else -> getString(R.string.xingdun_chat_history_title)
        }
    }

    private fun showDatePicker() {
        content.removeAllViews()
        val calendar = Calendar.getInstance()
        val picker = DatePicker(this).apply {
            maxDate = System.currentTimeMillis()
            calendarViewShown = true
        }
        content.addView(picker, matchWrap())
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_chat_history_date_hint)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(12.dp(), 14.dp(), 12.dp(), 18.dp())
            tag = TAG_SECONDARY
        }, matchWrap())
        content.addView(Button(this).apply {
            setText(R.string.xingdun_chat_history_view_messages)
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(BRAND, 14f)
            setOnClickListener {
                calendar.set(picker.year, picker.month, picker.dayOfMonth, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                start(
                    this@XingDunChatHistoryCategoryActivity,
                    conversationID,
                    MODE_DATE_RESULTS,
                    selectedDay = calendar.timeInMillis,
                    debugPreview = isDebugPreview,
                )
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            topMargin = 8.dp()
        })
        applyColors(colors())
    }

    private fun loadMessages() {
        if (isDebugPreview) {
            renderLoaded(emptyList())
            return
        }
        showLoading()
        val types = when (mode) {
            MODE_IMAGE -> listOf(MessageType.IMAGE)
            MODE_VIDEO -> listOf(MessageType.VIDEO)
            MODE_FILE -> listOf(MessageType.FILE)
            else -> emptyList()
        }
        messageStore.loadMessages(
            MessageLoadOption(
                messageListType = MessageListType.HISTORY,
                direction = MessageLoadDirection.OLDER,
                pageCount = PAGE_COUNT,
                messageTypeList = types,
            ),
            object : CompletionHandler {
                override fun onSuccess() = runOnUiThread { renderLoaded(messageStore.state.messageList.value) }
                override fun onFailure(code: Int, desc: String) = runOnUiThread { showLoadError() }
            },
        )
    }

    private fun showLoading() {
        content.removeAllViews()
        progress.visibility = View.VISIBLE
        content.addView(progress, LinearLayout.LayoutParams(40.dp(), 40.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 72.dp()
        })
    }

    private fun renderLoaded(values: List<MessageInfo>) {
        messages = values
            .filter(::matchesMode)
            .distinctBy { it.msgID }
            .sortedByDescending { it.timestamp ?: 0L }
        content.removeAllViews()
        progress.visibility = View.GONE
        if (mode == MODE_MEMBER) renderMembers(values) else renderMessages()
        applyColors(colors())
    }

    private fun renderMembers(values: List<MessageInfo>) {
        val members = values.mapNotNull { message ->
            val sender = message.from ?: return@mapNotNull null
            val id = sender.userID.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val name = listOf(sender.nameCard, sender.friendRemark, sender.nickname, id)
                .firstOrNull { !it.isNullOrBlank() }.orEmpty()
            id to name
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        if (members.isEmpty()) {
            showEmpty(R.string.xingdun_chat_history_no_members, R.string.xingdun_chat_history_no_members_hint)
            return
        }
        members.forEachIndexed { index, (id, name) ->
            content.addView(navigationRow(name, id) {
                start(
                    this,
                    conversationID,
                    MODE_MEMBER_RESULTS,
                    memberID = id,
                    memberName = name,
                    debugPreview = isDebugPreview,
                )
            }, matchWrap())
            if (index != members.lastIndex) content.addView(separator(), separatorParams())
        }
    }

    private fun renderMessages() {
        if (messages.isEmpty()) {
            val titleRes = when (mode) {
                MODE_IMAGE -> R.string.xingdun_chat_history_no_images
                MODE_VIDEO -> R.string.xingdun_chat_history_no_videos
                MODE_FILE -> R.string.xingdun_chat_history_no_files
                MODE_DATE_RESULTS -> R.string.xingdun_chat_history_no_date_messages
                else -> R.string.xingdun_chat_history_no_messages
            }
            showEmpty(titleRes, R.string.xingdun_chat_history_empty_hint)
            return
        }
        messages.forEachIndexed { index, message ->
            content.addView(messageRow(message), matchWrap())
            if (index != messages.lastIndex) content.addView(separator(), separatorParams())
        }
    }

    private fun matchesMode(message: MessageInfo): Boolean {
        if (mode == MODE_MEMBER) return true
        if (mode == MODE_MEMBER_RESULTS && message.from?.userID != memberID) return false
        if (mode == MODE_DATE_RESULTS && !isSameDay(message.timestamp, selectedDay)) return false
        return when (mode) {
            MODE_IMAGE -> message.messageType == MessageType.IMAGE
            MODE_VIDEO -> message.messageType == MessageType.VIDEO
            MODE_FILE -> message.messageType == MessageType.FILE
            else -> true
        }
    }

    private fun messageRow(message: MessageInfo): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 72.dp()
        setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
        background = rounded(colors().bgColorOperate, 0f)
        setOnClickListener {
            if (message.messageType == MessageType.IMAGE || message.messageType == MessageType.VIDEO) {
                showMedia(message)
            } else {
                ChatActivity.start(this@XingDunChatHistoryCategoryActivity, conversationID, message)
                finish()
            }
        }
        addView(ImageView(this@XingDunChatHistoryCategoryActivity).apply {
            setImageResource(iconFor(message.messageType))
            imageTintList = ColorStateList.valueOf(BRAND)
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
            background = rounded(BRAND_SOFT, 22f)
        }, LinearLayout.LayoutParams(44.dp(), 44.dp()))
        addView(LinearLayout(this@XingDunChatHistoryCategoryActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 0, 0, 0)
            addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                text = rowTitle(message)
                maxLines = 2
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                tag = TAG_PRIMARY
            }, matchWrap())
            addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                text = messageTime(message)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, 4.dp(), 0, 0)
                tag = TAG_SECONDARY
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun rowTitle(message: MessageInfo): String = when (val payload = message.messagePayload) {
        is FileMessagePayload -> payload.fileName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.xingdun_chat_history_file)
        else -> message.getMessageAbstract(this).takeIf { it.isNotBlank() }
            ?: message.messageSender.takeIf { it.isNotBlank() }
            ?: getString(R.string.xingdun_chat_history_message)
    }

    private fun showMedia(selected: MessageInfo) {
        val mediaMessages = messages.filter { it.messageType == MessageType.IMAGE || it.messageType == MessageType.VIDEO }
        val entries = mediaMessages.mapNotNull(::imageElement)
        val index = mediaMessages.indexOfFirst { it.msgID == selected.msgID }.coerceAtLeast(0)
        if (entries.isNotEmpty()) ImageViewer.view(entries, index.coerceAtMost(entries.lastIndex), null)
    }

    private fun imageElement(message: MessageInfo): ImageElement? {
        return when (val payload = message.messagePayload) {
            is ImageMessagePayload -> {
                val source = listOf(payload.originalImagePath, payload.originalImageURL, payload.largeImagePath, payload.largeImageURL)
                    .firstOrNull { !it.isNullOrBlank() } ?: return null
                ImageElement(source, 0, payload.originalImageWidth, payload.originalImageHeight, null, message.msgID)
            }
            is VideoMessagePayload -> {
                val cover = listOf(payload.videoSnapshotPath, payload.videoSnapshotURL).firstOrNull { !it.isNullOrBlank() }
                    ?: return null
                val video = listOf(payload.videoPath, payload.videoURL).firstOrNull { !it.isNullOrBlank() }
                ImageElement(cover, 1, payload.videoSnapshotWidth, payload.videoSnapshotHeight, video, message.msgID)
            }
            else -> null
        }
    }

    private fun navigationRow(titleValue: String, subtitle: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 64.dp()
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            background = rounded(colors().bgColorOperate, 0f)
            setOnClickListener { onClick() }
            addView(ImageView(this@XingDunChatHistoryCategoryActivity).apply {
                setImageResource(android.R.drawable.ic_menu_myplaces)
                imageTintList = ColorStateList.valueOf(BRAND)
            }, LinearLayout.LayoutParams(34.dp(), 34.dp()))
            addView(LinearLayout(this@XingDunChatHistoryCategoryActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 0, 0)
                addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                    text = titleValue
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    tag = TAG_PRIMARY
                }, matchWrap())
                addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                    text = subtitle
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    tag = TAG_SECONDARY
                }, matchWrap())
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                text = "›"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                tag = TAG_SECONDARY
            })
        }

    private fun showEmpty(titleRes: Int, messageRes: Int) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp(), 72.dp(), 24.dp(), 72.dp())
            addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                setText(titleRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                tag = TAG_PRIMARY
            }, matchWrap())
            addView(TextView(this@XingDunChatHistoryCategoryActivity).apply {
                setText(messageRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, 10.dp(), 0, 0)
                tag = TAG_SECONDARY
            }, matchWrap())
        }, matchWrap())
    }

    private fun showLoadError() {
        content.removeAllViews()
        status.setText(R.string.xingdun_chat_history_load_failed)
        status.visibility = View.VISIBLE
        content.addView(status, matchWrap())
        content.addView(Button(this).apply {
            setText(R.string.xingdun_retry)
            setOnClickListener { loadMessages() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        applyColors(colors())
    }

    private fun isSameDay(timestamp: Long?, dayStart: Long): Boolean {
        val raw = timestamp ?: return false
        val timeMillis = if (raw < 10_000_000_000L) raw * 1000L else raw
        val messageDay = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val selected = Calendar.getInstance().apply { timeInMillis = dayStart }
        return messageDay.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
            messageDay.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR)
    }

    private fun messageTime(message: MessageInfo): String {
        val raw = message.timestamp ?: return ""
        val millis = if (raw < 10_000_000_000L) raw * 1000L else raw
        return DateFormat.getMediumDateFormat(this).format(Date(millis))
    }

    private fun iconFor(type: MessageType): Int = when (type) {
        MessageType.IMAGE -> android.R.drawable.ic_menu_gallery
        MessageType.VIDEO -> android.R.drawable.ic_menu_slideshow
        MessageType.FILE -> android.R.drawable.ic_menu_save
        else -> android.R.drawable.ic_dialog_email
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        title.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        recolor(content, colors)
    }

    private fun recolor(view: View, colors: ColorTokens) {
        if (view.tag == TAG_PRIMARY && view is TextView) view.setTextColor(colors.textColorPrimary)
        if (view.tag == TAG_SECONDARY && view is TextView) view.setTextColor(colors.textColorSecondary)
        if (view is ViewGroup) for (index in 0 until view.childCount) recolor(view.getChildAt(index), colors)
    }

    private fun separator() = View(this).apply { setBackgroundColor(colors().strokeColorPrimary) }
    private fun separatorParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
        marginStart = 14.dp()
        marginEnd = 14.dp()
    }
    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }
    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val EXTRA_MODE = "history_mode"
        private const val EXTRA_MEMBER_ID = "member_id"
        private const val EXTRA_MEMBER_NAME = "member_name"
        private const val EXTRA_SELECTED_DAY = "selected_day"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_chat_history_preview"
        const val MODE_MEMBER = "member"
        const val MODE_MEMBER_RESULTS = "member_results"
        const val MODE_IMAGE = "image"
        const val MODE_VIDEO = "video"
        const val MODE_FILE = "file"
        const val MODE_DATE = "date"
        const val MODE_DATE_RESULTS = "date_results"
        private val MODES = setOf(MODE_MEMBER, MODE_MEMBER_RESULTS, MODE_IMAGE, MODE_VIDEO, MODE_FILE, MODE_DATE, MODE_DATE_RESULTS)
        private const val PAGE_COUNT = 100
        private const val TAG_PRIMARY = "chat_history_primary"
        private const val TAG_SECONDARY = "chat_history_secondary"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val BRAND_SOFT = 0x1F23B39C

        fun start(
            context: Context,
            conversationID: String,
            mode: String,
            memberID: String? = null,
            memberName: String? = null,
            selectedDay: Long = 0L,
            debugPreview: Boolean = false,
        ) {
            context.startActivity(Intent(context, XingDunChatHistoryCategoryActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_MEMBER_ID, memberID)
                putExtra(EXTRA_MEMBER_NAME, memberName)
                putExtra(EXTRA_SELECTED_DAY, selectedDay)
                putExtra(EXTRA_DEBUG_PREVIEW, debugPreview)
            })
        }
    }
}
