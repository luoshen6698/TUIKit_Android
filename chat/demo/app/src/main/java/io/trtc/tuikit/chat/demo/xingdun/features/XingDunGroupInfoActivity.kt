package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.AlertDialog
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.activity.result.contract.ActivityResultContracts
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
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Business-authoritative group information page aligned with the iOS group detail screen. */
open class XingDunGroupInfoActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null
    private val groupID: String by lazy { intent.getStringExtra(EXTRA_GROUP_ID).orEmpty() }

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var back: ImageView
    private lateinit var more: ImageView
    private lateinit var badge: FrameLayout
    private lateinit var divider: View
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout

    private var detail: XingDunGroupDetail? = null
    private val groupAvatarEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)) {
            loadDetail(force = true)
        }
    }

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
            themeStore.themeState.collectLatest {
                applyHeaderTheme(it.currentTheme.tokens.color)
                detail?.let(::renderDetail)
            }
        }
        if (intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)) {
            detail = XingDunGroupDetail(
                groupId = "@TGS#debug-private-id",
                displayGroupId = "100284",
                name = getString(R.string.xingdun_group_info_preview_name),
                avatar = null,
                intro = getString(R.string.xingdun_group_info_preview_intro),
                currentUserRole = "owner",
                updateTeamMode = 2,
            )
            renderDetail(requireNotNull(detail))
            refresh.isEnabled = false
        } else {
            showLoading()
            loadDetail()
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
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        titleView.setText(R.string.demo_chat_setting_group_info)
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener { finish() }
        more.visibility = View.GONE
        badge.visibility = View.GONE
        refresh.setColorSchemeColors(BRAND)
        refresh.setOnRefreshListener { loadDetail(force = true) }
        applyHeaderTheme(colors())
    }

    private fun loadDetail(force: Boolean = false) {
        val scope = activityScope ?: return
        if (!force && detail == null) showLoading()
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
                renderDetail(it)
            }.onFailure { error ->
                val cached = detail
                if (cached == null) showLoadError(error) else {
                    renderDetail(cached, error.localizedMessage)
                }
            }
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ProgressBar(this), LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { topMargin = 80.dp() })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_info_loading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors().textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 0)
        })
    }

    private fun showLoadError(error: Throwable) {
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_info_load_failed)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(current.textColorPrimary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 72.dp() })
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
            setOnClickListener { loadDetail() }
        })
    }

    private fun renderDetail(value: XingDunGroupDetail, warning: String? = null) {
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.NO_GRAVITY
        content.setBackgroundColor(current.bgColorTopBar)

        val identity = card(current)
        identity.addView(avatarRow(value, current))
        identity.addView(rowDivider(current))
        identity.addView(textRow(
            title = getString(R.string.xingdun_group_info_name),
            value = value.name.ifBlank { getString(R.string.xingdun_not_set) },
            colors = current,
        ))
        identity.addView(rowDivider(current))
        identity.addView(textRow(
            title = getString(R.string.xingdun_group_info_intro),
            value = value.intro?.takeIf(String::isNotBlank) ?: getString(R.string.xingdun_not_set),
            colors = current,
            showsDisclosure = value.canEditGroupInfo,
            multiline = true,
            onClick = if (value.canEditGroupInfo) ({ showIntroductionEditor(value) }) else null,
        ))
        content.addView(identity, matchWrap())

        val idCard = card(current)
        val displayID = value.publicGroupId
        idCard.addView(textRow(
            title = getString(R.string.xingdun_group_info_id),
            value = displayID ?: getString(R.string.xingdun_group_info_id_unavailable),
            colors = current,
            onClick = displayID?.let { id -> ({ copyGroupID(id) }) },
        ))
        content.addView(idCard, matchWrap().apply { topMargin = 12.dp() })

        if (!warning.isNullOrBlank()) {
            content.addView(TextView(this).apply {
                text = warning
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(WARNING)
                background = rounded(0xFFFFF4E5.toInt(), 12f)
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            }, matchWrap().apply { topMargin = 12.dp() })
        }
    }

    private fun avatarRow(value: XingDunGroupDetail, colors: ColorTokens): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 68.dp()
            setPadding(16.dp(), 8.dp(), 12.dp(), 8.dp())
            isClickable = value.canEditGroupInfo
            isFocusable = value.canEditGroupInfo
            if (value.canEditGroupInfo) setOnClickListener {
                groupAvatarEditor.launch(
                    XingDunGroupAvatarActivity.intent(
                        context = this@XingDunGroupInfoActivity,
                        groupID = value.groupId,
                        groupName = value.name,
                        avatarURL = value.avatar,
                        debugPreview = intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false),
                    ),
                )
            }
        }
        row.addView(TextView(this).apply {
            setText(R.string.xingdun_group_info_avatar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Avatar(this).apply {
            setSize(Avatar.AvatarSize.M)
            setContent(Avatar.AvatarContent.Image(value.avatar, value.name))
        })
        if (value.canEditGroupInfo) {
            row.addView(chevron(colors), LinearLayout.LayoutParams(18.dp(), 24.dp()).apply { marginStart = 8.dp() })
        }
        return row
    }

    private fun textRow(
        title: String,
        value: String,
        colors: ColorTokens,
        showsDisclosure: Boolean = false,
        multiline: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = if (multiline) 72.dp() else 58.dp()
        setPadding(16.dp(), 10.dp(), 12.dp(), 10.dp())
        isClickable = onClick != null
        isFocusable = onClick != null
        onClick?.let { action -> setOnClickListener { action() } }
        addView(TextView(this@XingDunGroupInfoActivity).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(TextView(this@XingDunGroupInfoActivity).apply {
            text = value
            maxLines = if (multiline) 3 else 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors.textColorSecondary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 18.dp() })
        if (showsDisclosure) {
            addView(chevron(colors), LinearLayout.LayoutParams(18.dp(), 24.dp()).apply { marginStart = 6.dp() })
        }
    }

    private fun showIntroductionEditor(value: XingDunGroupDetail) {
        val current = colors()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }
        val editor = EditText(this).apply {
            setText(value.intro.orEmpty())
            setSelection(text.length)
            minHeight = 130.dp()
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(current.textColorPrimary)
            setHintTextColor(current.textColorTertiary)
            setHint(R.string.xingdun_group_info_intro_hint)
            background = rounded(current.bgColorOperate, 12f, current.strokeColorPrimary)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }
        val counter = TextView(this).apply {
            gravity = Gravity.END
            setTextColor(current.textColorTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 6.dp(), 0, 0)
        }
        fun updateCounter() {
            counter.text = getString(
                R.string.xingdun_group_info_byte_count,
                editor.text.toString().toByteArray(Charsets.UTF_8).size,
                INTRO_MAX_BYTES,
            )
        }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateCounter()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateCounter()
        wrapper.addView(editor, matchWrap())
        wrapper.addView(counter, matchWrap())
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_group_info_intro)
            .setView(wrapper)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_group_info_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = editor.text.toString().trim()
                if (candidate.toByteArray(Charsets.UTF_8).size > INTRO_MAX_BYTES) {
                    Toast.makeText(this, R.string.xingdun_group_info_intro_too_long, Toast.LENGTH_LONG).show()
                } else {
                    saveIntroduction(candidate, dialog)
                }
            }
        }
        dialog.show()
    }

    private fun saveIntroduction(value: String, dialog: AlertDialog) {
        val scope = activityScope ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        scope.launch {
            runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().postEmpty(
                    session,
                    "team/update",
                    mapOf("team_id" to groupID, "intro" to value),
                )
            }.onSuccess {
                detail = detail?.copy(intro = value)
                detail?.let(::renderDetail)
                GroupStore.shared.loadJoinedGroups()
                dialog.dismiss()
                Toast.makeText(this@XingDunGroupInfoActivity, R.string.xingdun_group_info_intro_updated, Toast.LENGTH_SHORT).show()
                loadDetail(force = true)
            }.onFailure { error ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                Toast.makeText(this@XingDunGroupInfoActivity, error.localizedMessage ?: getString(R.string.xingdun_action_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyGroupID(id: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.xingdun_group_info_id), id),
        )
        Toast.makeText(this, R.string.xingdun_group_info_id_copied, Toast.LENGTH_SHORT).show()
    }

    private fun card(colors: ColorTokens) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(colors.bgColorOperate, 18f)
        clipToOutline = true
    }

    private fun rowDivider(colors: ColorTokens) = View(this).apply {
        setBackgroundColor(colors.strokeColorPrimary)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = 16.dp()
        }
    }

    private fun chevron(colors: ColorTokens) = ImageView(this).apply {
        setImageResource(R.drawable.demo_ic_arrow_right)
        imageTintList = ColorStateList.valueOf(colors.textColorTertiary)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private fun applyHeaderTheme(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        titleView.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
    }

    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
        strokeColor?.let { setStroke(1.dp(), it) }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_info_preview"
        private const val INTRO_MAX_BYTES = 240
        private const val BRAND = 0xFF23B39C.toInt()
        private const val WARNING = 0xFFB36A00.toInt()

        fun start(context: Context, groupID: String) {
            context.startActivity(
                Intent(context, XingDunGroupInfoActivity::class.java)
                    .putExtra(EXTRA_GROUP_ID, groupID),
            )
        }
    }
}
