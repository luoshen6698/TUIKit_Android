package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessage
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessagePage
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessageSnapshot
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Pinned-message list aligned with the active iOS sheet. */
open class XingDunPinnedMessagesActivity : BaseActivity() {
    override val requiresLogin: Boolean get() = !isDebugPreview

    private val conversationID by lazy { intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty() }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null
    private var page = XingDunPinnedMessagePage()
    private var canManage = false
    private var isLoading = false
    private var isUpdating = false

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: ImageView
    private lateinit var done: TextView
    private lateinit var divider: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var retry: Button

    private val repositoryListener: (String) -> Unit = { changedID ->
        if (!isDebugPreview && changedID == conversationID) runOnUiThread { load(force = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (conversationID.isBlank()) {
            Toast.makeText(this, R.string.xingdun_pinned_invalid_conversation, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.xingdun_activity_profile_editor)
        bindViews()
        configureHeader()
        buildStateViews()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch { themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) } }
        XingDunPinnedMessageRepository.addListener(repositoryListener)
        if (isDebugPreview) {
            canManage = true
            page = previewPage()
            render()
        } else {
            load()
            loadPermission()
        }
    }

    override fun onDestroy() {
        XingDunPinnedMessageRepository.removeListener(repositoryListener)
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
        findViewById<View>(R.id.xingdun_profileEditorBottomNavigation).visibility = View.GONE
        val more = findViewById<ImageView>(R.id.demo_btnMore)
        more.visibility = View.GONE
        findViewById<FrameLayout>(R.id.demo_badgeContainer).visibility = View.GONE
        findViewById<LinearLayout>(R.id.demo_leftContainer).visibility = View.INVISIBLE
        done = TextView(this).apply {
            setText(R.string.xingdun_complete)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(12.dp(), 0, 0, 0)
            setOnClickListener { if (!isUpdating) finish() }
        }
        (more.parent as FrameLayout).addView(
            done,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END),
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        title.setText(R.string.xingdun_pinned_messages_title)
        val language = resources.configuration.locales[0]?.language
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (language == "en") 15f else 18f)
    }

    private fun buildStateViews() {
        progress = ProgressBar(this).apply { visibility = View.GONE }
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(20.dp(), 24.dp(), 20.dp(), 16.dp())
            visibility = View.GONE
        }
        retry = Button(this).apply {
            setText(R.string.xingdun_pinned_retry)
            visibility = View.GONE
            setOnClickListener { load(force = true) }
        }
    }

    private fun load(force: Boolean = false) {
        if (isLoading || isUpdating) return
        XingDunPinnedMessageRepository.cached(conversationID)?.let {
            page = it
            render()
        }
        isLoading = page.items.isEmpty()
        renderState()
        activityScope?.launch {
            runCatching { XingDunPinnedMessageRepository.load(this@XingDunPinnedMessagesActivity, conversationID, force) }
                .onSuccess {
                    page = it
                    isLoading = false
                    render()
                }
                .onFailure {
                    isLoading = false
                    showError(R.string.xingdun_pinned_load_failed)
                }
        }
    }

    private fun loadPermission() {
        if (conversationID.startsWith("c2c_")) {
            canManage = true
            render()
            return
        }
        val groupID = conversationID.removePrefix("group_")
        activityScope?.launch {
            val result = runCatching {
                val session = XingDunSessionManager.currentSession() ?: error("Missing session")
                XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session,
                    "team/detail",
                    mapOf("team_id" to groupID),
                    XingDunGroupDetail::class.java,
                )
            }
            result.onSuccess {
                canManage = XingDunPinnedMessagePolicy.canManage(
                    it.currentUserRole,
                    it.currentUserIsAssignedCs,
                    it.pinMessageMode,
                )
                render()
            }
        }
    }

    private fun render() {
        content.removeAllViews()
        val pins = if (isDebugPreview) {
            XingDunPinnedMessagePolicy.visiblePins(page.items)
        } else {
            XingDunPinnedMessageRepository.unreadPins(this, conversationID, page.items)
        }
        if (pins.isEmpty() && !isLoading) {
            content.addView(emptyState(), matchWrap())
        } else {
            pins.forEachIndexed { index, pin ->
                content.addView(pinRow(pin), matchWrap().apply {
                    if (index > 0) topMargin = 10.dp()
                })
            }
        }
        content.addView(progress, LinearLayout.LayoutParams(36.dp(), 36.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 24.dp()
        })
        content.addView(status, matchWrap())
        content.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        applyColors(colors())
        renderState()
    }

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(24.dp(), 72.dp(), 24.dp(), 72.dp())
        addView(TextView(this@XingDunPinnedMessagesActivity).apply {
            setText(R.string.xingdun_pinned_empty_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            tag = TAG_PRIMARY
        }, matchWrap())
        addView(TextView(this@XingDunPinnedMessagesActivity).apply {
            setText(R.string.xingdun_pinned_empty_message)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, 10.dp(), 0, 0)
            tag = TAG_SECONDARY
        }, matchWrap())
    }

    private fun pinRow(pin: XingDunPinnedMessage): View = FrameLayout(this).apply {
        val actionWidth = 104.dp()
        val action = if (canManage) {
            TextView(this@XingDunPinnedMessagesActivity).apply {
                setText(R.string.xingdun_pinned_unpin)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(DESTRUCTIVE)
                gravity = Gravity.CENTER
                visibility = View.INVISIBLE
                setOnClickListener { unpin(pin) }
            }.also {
                addView(it, FrameLayout.LayoutParams(actionWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
            }
        } else null
        val card = LinearLayout(this@XingDunPinnedMessagesActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 15.dp(), 16.dp(), 13.dp())
            background = rounded(colors().bgColorOperate, 18f)
            isClickable = true
            isFocusable = true
            contentDescription = summary(pin)
            setOnClickListener {
                if (translationX < 0f) {
                    animate().translationX(0f).setDuration(SWIPE_ANIMATION_MS)
                        .withEndAction { action?.visibility = View.INVISIBLE }
                        .start()
                }
                else locate(pin)
            }
            addView(TextView(this@XingDunPinnedMessagesActivity).apply {
                text = summary(pin)
                maxLines = 3
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                tag = TAG_PRIMARY
            }, matchWrap())
            addView(TextView(this@XingDunPinnedMessagesActivity).apply {
                text = actionDescription(pin)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 7.dp(), 0, 0)
                tag = TAG_SECONDARY
            }, matchWrap())
        }
        addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (action != null) attachSwipe(card, action, actionWidth)
    }

    private fun attachSwipe(card: View, action: View, actionWidth: Int) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var startTranslation = 0f
        var moved = false
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    startTranslation = view.translationX
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawX - downX
                    if (kotlin.math.abs(delta) > touchSlop) {
                        moved = true
                        if (delta < 0f || startTranslation < 0f) action.visibility = View.VISIBLE
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    view.translationX = (startTranslation + delta).coerceIn(-actionWidth.toFloat(), 0f)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (!moved) {
                        view.performClick()
                    } else {
                        val target = if (view.translationX <= -actionWidth / 2f) -actionWidth.toFloat() else 0f
                        val animation = view.animate().translationX(target).setDuration(SWIPE_ANIMATION_MS)
                        if (target == 0f) animation.withEndAction { action.visibility = View.INVISIBLE }
                        animation.start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    view.animate().translationX(0f).setDuration(SWIPE_ANIMATION_MS)
                        .withEndAction { action.visibility = View.INVISIBLE }
                        .start()
                    true
                }
                else -> false
            }
        }
    }

    private fun locate(pin: XingDunPinnedMessage) {
        if (isDebugPreview) {
            Toast.makeText(this, R.string.xingdun_pinned_locate_preview, Toast.LENGTH_SHORT).show()
            return
        }
        ChatActivity.startForMessageID(
            this,
            pin.conversationId.ifBlank { conversationID },
            pin.messageId,
            pin.version,
        )
        finish()
    }

    private fun unpin(pin: XingDunPinnedMessage) {
        if (isDebugPreview) {
            page = page.copy(items = page.items.filterNot { it.messageId == pin.messageId }, total = (page.total - 1).coerceAtLeast(0))
            render()
            return
        }
        isUpdating = true
        renderState()
        activityScope?.launch {
            val result = runCatching {
                if (conversationID.startsWith("c2c_")) {
                    val snapshot = pin.message
                    XingDunPinnedMessageRepository.toggleDirect(
                        this@XingDunPinnedMessagesActivity,
                        conversationID,
                        pin.messageId,
                        pin.messageSequence,
                        snapshot?.sender,
                        snapshot?.senderNickname,
                        snapshot?.messageType.orEmpty(),
                        snapshot?.text.orEmpty(),
                    )
                } else {
                    XingDunPinnedMessageRepository.unpinGroup(conversationID, pin.messageId)
                }
            }
            isUpdating = false
            result.onSuccess {
                page = page.copy(items = page.items.filterNot { item -> item.messageId == pin.messageId })
                Toast.makeText(this@XingDunPinnedMessagesActivity, R.string.xingdun_pinned_unpinned, Toast.LENGTH_SHORT).show()
                render()
            }.onFailure { showError(R.string.xingdun_pinned_unpin_failed) }
        }
    }

    private fun summary(pin: XingDunPinnedMessage): String {
        val text = pin.message?.text?.trim().orEmpty()
        if (text.isNotEmpty()) return text
        val res = when (XingDunPinnedMessagePolicy.summaryType(pin.message?.messageType)) {
            XingDunPinnedMessagePolicy.SummaryType.IMAGE -> R.string.xingdun_pinned_summary_image
            XingDunPinnedMessagePolicy.SummaryType.AUDIO -> R.string.xingdun_pinned_summary_audio
            XingDunPinnedMessagePolicy.SummaryType.VIDEO -> R.string.xingdun_pinned_summary_video
            XingDunPinnedMessagePolicy.SummaryType.FILE -> R.string.xingdun_pinned_summary_file
            XingDunPinnedMessagePolicy.SummaryType.CUSTOM -> R.string.xingdun_pinned_summary_custom
            XingDunPinnedMessagePolicy.SummaryType.MESSAGE -> R.string.xingdun_pinned_summary_message
        }
        return getString(res)
    }

    private fun actionDescription(pin: XingDunPinnedMessage): String {
        val fallback = getString(R.string.xingdun_pinned_group_member)
        val operator = pin.operatorNickname?.trim().takeUnless(String?::isNullOrEmpty) ?: fallback
        val sender = pin.message?.senderNickname?.trim().takeUnless(String?::isNullOrEmpty) ?: fallback
        return getString(R.string.xingdun_pinned_action_description, operator, sender)
    }

    private fun showError(message: Int) {
        status.setText(message)
        status.visibility = View.VISIBLE
        retry.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
        renderState()
    }

    private fun renderState() {
        progress.visibility = if (isLoading || isUpdating) View.VISIBLE else View.GONE
        back.alpha = if (isUpdating) 0.35f else 1f
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        title.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        done.setTextColor(BRAND)
        status.setTextColor(WARNING)
        recolor(content, colors)
    }

    private fun recolor(view: View, colors: ColorTokens) {
        if (view.tag == TAG_PRIMARY && view is TextView) view.setTextColor(colors.textColorPrimary)
        if (view.tag == TAG_SECONDARY && view is TextView) view.setTextColor(colors.textColorSecondary)
        if (view is ViewGroup) for (index in 0 until view.childCount) recolor(view.getChildAt(index), colors)
    }

    private fun previewPage() = XingDunPinnedMessagePage(
        items = listOf(
            previewPin("pin-1", "项目进度已更新，请大家今天下班前确认。", "张经理", "项目负责人", 4),
            previewPin("pin-2", "", "客服小星", "设计组", 3, "PICTURE"),
            previewPin("pin-3", "会议纪要.pdf", "群管理员", "运营同事", 2, "FILE"),
        ),
        total = 3,
    )

    private fun previewPin(
        id: String,
        text: String,
        operator: String,
        sender: String,
        version: Int,
        type: String = "TEXT",
    ) = XingDunPinnedMessage(
        messageId = id,
        conversationId = conversationID,
        isPinned = true,
        version = version,
        operatorNickname = operator,
        message = XingDunPinnedMessageSnapshot(senderNickname = sender, messageType = type, text = text),
    )

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }
    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_pinned_messages_preview"
        private const val TAG_PRIMARY = "pinned_primary"
        private const val TAG_SECONDARY = "pinned_secondary"
        private const val SWIPE_ANIMATION_MS = 160L
        private const val BRAND = 0xFF23B39C.toInt()
        private const val DESTRUCTIVE = 0xFFD83B32.toInt()
        private const val WARNING = 0xFFB36A00.toInt()

        fun start(context: Context, conversationID: String) {
            context.startActivity(Intent(context, XingDunPinnedMessagesActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
            })
        }
    }
}
