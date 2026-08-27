package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Business-authoritative announcement page aligned with the iOS group settings child page. */
open class XingDunGroupAnnouncementActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private val themeStore by lazy { ThemeStore.shared(this) }
    private val groupID by lazy { intent.getStringExtra(EXTRA_GROUP_ID).orEmpty() }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private var activityScope: CoroutineScope? = null

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var back: ImageView
    private lateinit var more: ImageView
    private lateinit var saveAction: TextView
    private lateinit var badge: FrameLayout
    private lateinit var divider: View
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var detail: XingDunGroupDetail? = null
    private var editor: EditText? = null
    private var counter: TextView? = null
    private var operationError: String? = null
    private var isOperating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (groupID.isBlank()) {
            Toast.makeText(this, R.string.xingdun_invalid_group, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.xingdun_activity_group_info)
        bindViews()
        configureHeader()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyHeaderTheme(state.currentTheme.tokens.color)
                detail?.let { render(it, editor?.text?.toString()) }
            }
        }
        if (isDebugPreview) {
            refresh.isEnabled = false
            val fixture = XingDunGroupDetail(
                groupId = groupID,
                displayGroupId = "100284",
                name = getString(R.string.xingdun_group_info_preview_name),
                announcement = getString(R.string.xingdun_group_announcement_preview),
                currentUserRole = "owner",
            )
            detail = fixture
            render(fixture)
        } else {
            showLoading()
            load()
        }
    }

    override fun onDestroy() {
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.xingdun_groupInfoRoot)
        header = findViewById(R.id.demo_chatHeaderContainer)
        titleView = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        more = findViewById(R.id.demo_btnMore)
        badge = findViewById(R.id.demo_badgeContainer)
        divider = findViewById(R.id.demo_headerDivider)
        refresh = findViewById(R.id.xingdun_groupInfoRefresh)
        scroll = findViewById(R.id.xingdun_groupInfoScroll)
        content = findViewById(R.id.xingdun_groupInfoContent)
        saveAction = TextView(this).apply {
            setText(R.string.xingdun_group_announcement_save)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(12.dp(), 0, 0, 0)
            visibility = View.GONE
            isEnabled = false
            setOnClickListener { save() }
        }
        (more.parent as FrameLayout).addView(
            saveAction,
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
        titleView.setText(R.string.xingdun_group_announcement)
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener { if (!isOperating) finish() }
        more.visibility = View.GONE
        badge.visibility = View.GONE
        refresh.setColorSchemeColors(BRAND)
        refresh.setOnRefreshListener { load() }
        applyHeaderTheme(colors())
    }

    private fun load() {
        val scope = activityScope ?: return
        scope.launch {
            val result = runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session,
                    "team/detail",
                    mapOf("team_id" to groupID),
                    XingDunGroupDetail::class.java,
                )
            }
            refresh.isRefreshing = false
            result.onSuccess {
                detail = it
                operationError = null
                render(it)
            }.onFailure(::showLoadError)
        }
    }

    private fun showLoading() {
        saveAction.visibility = View.GONE
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ProgressBar(this), LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { topMargin = 80.dp() })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_announcement_loading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors().textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 0)
        })
    }

    private fun showLoadError(error: Throwable) {
        saveAction.visibility = View.GONE
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_announcement_load_failed)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(current.textColorPrimary)
            gravity = Gravity.CENTER
        }, matchWrap().apply { topMargin = 72.dp() })
        content.addView(TextView(this).apply {
            text = error.localizedMessage?.takeIf(String::isNotBlank)
                ?: getString(R.string.xingdun_group_info_load_failed_message)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(current.textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(12.dp(), 12.dp(), 12.dp(), 18.dp())
        })
        content.addView(Button(this).apply {
            setText(R.string.xingdun_group_info_retry)
            setOnClickListener {
                showLoading()
                load()
            }
        })
    }

    private fun render(value: XingDunGroupDetail, draft: String? = null) {
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.NO_GRAVITY
        content.setBackgroundColor(current.bgColorTopBar)
        saveAction.visibility = if (value.canEditAnnouncement) View.VISIBLE else View.GONE

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 14.dp(), 14.dp(), 10.dp())
            background = rounded(current.bgColorOperate, 18f)
        }
        val initial = draft ?: value.announcement.orEmpty()
        val announcementEditor = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            minHeight = 160.dp()
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(current.textColorPrimary)
            setHintTextColor(current.textColorSecondary)
            setHint(R.string.xingdun_group_announcement_empty)
            background = null
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            isEnabled = value.canEditAnnouncement && !isOperating
        }
        val byteCounter = TextView(this).apply {
            gravity = Gravity.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(current.textColorSecondary)
            setPadding(0, 8.dp(), 2.dp(), 0)
        }
        announcementEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateEditorState(announcementEditor, byteCounter)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        editor = announcementEditor
        counter = byteCounter
        card.addView(announcementEditor, matchWrap())
        card.addView(byteCounter, matchWrap())
        content.addView(card, matchWrap())
        updateEditorState(announcementEditor, byteCounter)

        if (!operationError.isNullOrBlank()) {
            content.addView(TextView(this).apply {
                text = operationError
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(WARNING)
                background = rounded(0xFFFFF4E5.toInt(), 12f)
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            }, matchWrap().apply { topMargin = 12.dp() })
        }
    }

    private fun updateEditorState(input: EditText, byteCounter: TextView) {
        val bytes = input.text.toString().trim().toByteArray(Charsets.UTF_8).size
        byteCounter.text = getString(R.string.xingdun_group_announcement_count, bytes, MAX_BYTES)
        byteCounter.setTextColor(if (bytes <= MAX_BYTES) colors().textColorSecondary else colors().textColorError)
        saveAction.isEnabled = detail?.canEditAnnouncement == true && !isOperating && bytes <= MAX_BYTES
        saveAction.setTextColor(if (saveAction.isEnabled) BRAND else colors().textColorTertiary)
    }

    private fun save() {
        val value = editor?.text?.toString()?.trim() ?: return
        val bytes = value.toByteArray(Charsets.UTF_8).size
        if (bytes > MAX_BYTES || isOperating) {
            if (bytes > MAX_BYTES) Toast.makeText(this, R.string.xingdun_group_announcement_too_long, Toast.LENGTH_LONG).show()
            return
        }
        if (isDebugPreview) {
            detail = detail?.copy(announcement = value)
            Toast.makeText(this, R.string.xingdun_group_announcement_saved, Toast.LENGTH_SHORT).show()
            detail?.let { render(it, value) }
            return
        }
        val scope = activityScope ?: return
        isOperating = true
        operationError = null
        editor?.isEnabled = false
        saveAction.isEnabled = false
        scope.launch {
            runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().postEmpty(
                    session,
                    "team/update",
                    mapOf("team_id" to groupID, "announcement" to value),
                )
            }.onSuccess {
                detail = detail?.copy(announcement = value)
                GroupStore.shared.loadJoinedGroups()
                Toast.makeText(this@XingDunGroupAnnouncementActivity, R.string.xingdun_group_announcement_saved, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                operationError = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
            }
            isOperating = false
            detail?.let { render(it, value) }
        }
    }

    private fun applyHeaderTheme(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        titleView.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        val input = editor
        val byteCounter = counter
        if (input != null && byteCounter != null) updateEditorState(input, byteCounter)
    }

    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_announcement_preview"
        private const val MAX_BYTES = 300
        private const val BRAND = 0xFF23B39C.toInt()
        private const val WARNING = 0xFFB36A00.toInt()

        fun start(context: Context, groupID: String) {
            context.startActivity(
                Intent(context, XingDunGroupAnnouncementActivity::class.java)
                    .putExtra(EXTRA_GROUP_ID, groupID),
            )
        }
    }
}
