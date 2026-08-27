package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.AlertDialog
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
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMemberPage
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Administrator list and role editor backed by the tenant business API. */
open class XingDunGroupAdministratorsActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !isDebugPreview

    private val themeStore by lazy { ThemeStore.shared(this) }
    private val groupID by lazy { intent.getStringExtra(EXTRA_GROUP_ID).orEmpty() }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private var activityScope: CoroutineScope? = null

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
    private var members: List<XingDunGroupMember> = emptyList()
    private var operatingUserID: String? = null
    private var warningMessage: String? = null

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
                detail?.let(::render)
            }
        }
        if (isDebugPreview) {
            refresh.isEnabled = false
            detail = previewDetail()
            members = previewMembers()
            render(requireNotNull(detail))
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
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        titleView.setText(R.string.xingdun_group_administrators)
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener {
            if (operatingUserID == null) finish()
        }
        more.visibility = View.GONE
        badge.visibility = View.GONE
        refresh.setColorSchemeColors(BRAND)
        refresh.setOnRefreshListener { load() }
        applyHeaderTheme(colors())
    }

    private fun load() {
        activityScope?.launch {
            val result = runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                val loadedDetail = XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session,
                    "team/detail",
                    mapOf("team_id" to groupID),
                    XingDunGroupDetail::class.java,
                )
                val page = XingDunSessionManager.apiClient().get<XingDunGroupMemberPage>(
                    session,
                    "team/members",
                    mapOf("team_id" to groupID, "page" to "1", "pageSize" to "200"),
                    XingDunGroupMemberPage::class.java,
                )
                loadedDetail to page.list.sortedWith(memberComparator)
            }
            refresh.isRefreshing = false
            result.onSuccess { (loadedDetail, loadedMembers) ->
                detail = loadedDetail
                members = loadedMembers
                warningMessage = null
                render(loadedDetail)
            }.onFailure(::showLoadError)
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ProgressBar(this), LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { topMargin = 80.dp() })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_administrators_loading)
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
            setText(R.string.xingdun_group_administrators_load_failed)
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

    private fun render(value: XingDunGroupDetail) {
        val current = colors()
        val administrators = members.filter { it.role == ROLE_ADMINISTRATOR }
        content.removeAllViews()
        content.gravity = Gravity.NO_GRAVITY
        content.setBackgroundColor(current.bgColorTopBar)

        if (canManageAdministrators(value)) {
            val addEnabled = operatingUserID == null && administrators.size < ADMINISTRATOR_LIMIT &&
                members.any { it.role == ROLE_MEMBER }
            content.addView(card(current).apply {
                addView(addAdministratorRow(administrators.size, addEnabled, current))
            }, matchWrap())
        } else if (!supportsAdministratorRoles(value.groupType)) {
            content.addView(infoView(getString(R.string.xingdun_group_administrators_unsupported), current), matchWrap())
        }

        if (warningMessage != null) {
            content.addView(infoView(requireNotNull(warningMessage), current, warning = true), matchWrap().apply { topMargin = 12.dp() })
        }

        if (administrators.isEmpty()) {
            content.addView(TextView(this).apply {
                setText(R.string.xingdun_group_administrators_empty)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(current.textColorTertiary)
                setPadding(16.dp(), 48.dp(), 16.dp(), 48.dp())
            }, matchWrap())
            return
        }

        val listCard = card(current)
        administrators.forEachIndexed { index, member ->
            if (index > 0) listCard.addView(rowDivider(current))
            listCard.addView(administratorRow(member, value, current))
        }
        content.addView(listCard, matchWrap().apply { topMargin = 12.dp() })
    }

    private fun addAdministratorRow(count: Int, enabled: Boolean, colors: ColorTokens): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 66.dp()
            setPadding(16.dp(), 9.dp(), 12.dp(), 9.dp())
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.48f
            if (enabled) setOnClickListener { showCandidatePicker() }
            addView(TextView(this@XingDunGroupAdministratorsActivity).apply {
                text = "+"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(BRAND)
                background = rounded(0x1423B39C, 18f)
            }, LinearLayout.LayoutParams(38.dp(), 38.dp()))
            addView(LinearLayout(this@XingDunGroupAdministratorsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 0, 0)
                addView(TextView(this@XingDunGroupAdministratorsActivity).apply {
                    setText(R.string.xingdun_group_administrator_add)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(BRAND)
                })
                addView(TextView(this@XingDunGroupAdministratorsActivity).apply {
                    text = getString(R.string.xingdun_group_administrator_limit, count, ADMINISTRATOR_LIMIT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(colors.textColorTertiary)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron(colors), LinearLayout.LayoutParams(18.dp(), 24.dp()))
        }

    private fun administratorRow(member: XingDunGroupMember, value: XingDunGroupDetail, colors: ColorTokens): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 68.dp()
            setPadding(14.dp(), 9.dp(), 12.dp(), 9.dp())
            val isOperating = operatingUserID == member.userId
            alpha = if (isOperating) 0.5f else 1f
            isClickable = !isOperating
            if (!isOperating) setOnClickListener { openMemberDetail(member.userId) }
            addView(Avatar(this@XingDunGroupAdministratorsActivity).apply {
                setSize(Avatar.AvatarSize.M)
                setContent(Avatar.AvatarContent.Image(member.avatar, member.displayName()))
            })
            addView(LinearLayout(this@XingDunGroupAdministratorsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 8.dp(), 0)
                addView(TextView(this@XingDunGroupAdministratorsActivity).apply {
                    text = member.displayName()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(colors.textColorPrimary)
                    maxLines = 1
                })
                addView(TextView(this@XingDunGroupAdministratorsActivity).apply {
                    text = member.userId
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(colors.textColorTertiary)
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (canRemoveAdministrator(value, member)) {
                addView(TextView(this@XingDunGroupAdministratorsActivity).apply {
                    setText(R.string.xingdun_group_administrator_remove)
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(DANGER)
                    background = rounded(0x0FE34D59, 16f)
                    setPadding(12.dp(), 7.dp(), 12.dp(), 7.dp())
                    isClickable = !isOperating
                    if (!isOperating) setOnClickListener {
                        confirmRemoveAdministrator(member)
                    }
                })
            }
        }

    private fun showCandidatePicker() {
        val candidates = members.filter { it.role == ROLE_MEMBER }
        if (candidates.isEmpty()) return
        val current = colors()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 4.dp(), 20.dp(), 8.dp())
        }
        val search = EditText(this).apply {
            setSingleLine(true)
            setHint(R.string.xingdun_group_administrator_search)
            setTextColor(current.textColorPrimary)
            setHintTextColor(current.textColorTertiary)
            background = rounded(current.bgColorInput, 16f)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = ScrollView(this).apply {
            addView(list)
        }
        wrapper.addView(search, matchWrap())
        wrapper.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 360.dp()).apply { topMargin = 10.dp() })

        lateinit var dialog: AlertDialog
        fun renderCandidates(query: String) {
            list.removeAllViews()
            val visible = candidates.filter {
                query.isBlank() || it.displayName().contains(query, ignoreCase = true) ||
                    it.userId.contains(query, ignoreCase = true)
            }
            if (visible.isEmpty()) {
                list.addView(TextView(this).apply {
                    setText(R.string.xingdun_group_administrator_no_candidates)
                    gravity = Gravity.CENTER
                    setTextColor(current.textColorTertiary)
                    setPadding(12.dp(), 36.dp(), 12.dp(), 36.dp())
                })
            } else {
                visible.forEachIndexed { index, member ->
                    if (index > 0) list.addView(rowDivider(current))
                    list.addView(TextView(this).apply {
                        text = "${member.displayName()}\n${member.userId}"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTextColor(current.textColorPrimary)
                        setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
                        isClickable = true
                        setOnClickListener {
                            dialog.dismiss()
                            setAdministrator(member, true)
                        }
                    })
                }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderCandidates(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_group_administrator_add)
            .setView(wrapper)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        renderCandidates("")
        dialog.show()
    }

    private fun confirmRemoveAdministrator(member: XingDunGroupMember) {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_group_administrator_remove_title)
            .setMessage(getString(R.string.xingdun_group_administrator_remove_message, member.displayName()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_group_administrator_remove) { _, _ ->
                setAdministrator(member, false)
            }
            .show()
    }

    private fun setAdministrator(member: XingDunGroupMember, enabled: Boolean) {
        if (operatingUserID != null) return
        if (isDebugPreview) {
            members = members.map {
                if (it.userId == member.userId) it.copy(role = if (enabled) ROLE_ADMINISTRATOR else ROLE_MEMBER) else it
            }.sortedWith(memberComparator)
            detail?.let(::render)
            Toast.makeText(
                this,
                if (enabled) R.string.xingdun_group_administrator_added else R.string.xingdun_group_administrator_removed,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        operatingUserID = member.userId
        warningMessage = null
        detail?.let(::render)
        activityScope?.launch {
            runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().postEmpty(
                    session,
                    "team/setAdmin",
                    mapOf("team_id" to groupID, "member_user_id" to member.userId, "is_admin" to enabled),
                )
            }.onSuccess {
                operatingUserID = null
                Toast.makeText(
                    this@XingDunGroupAdministratorsActivity,
                    if (enabled) R.string.xingdun_group_administrator_added else R.string.xingdun_group_administrator_removed,
                    Toast.LENGTH_SHORT,
                ).show()
                load()
            }.onFailure { error ->
                operatingUserID = null
                warningMessage = error.localizedMessage ?: getString(R.string.xingdun_group_management_action_failed)
                detail?.let(::render)
            }
        }
    }

    private fun openMemberDetail(userID: String) {
        members.firstOrNull { it.userId == userID }?.let { member ->
            XingDunContactDetailActivity.start(this, member.userId, member.nickname, member.avatar)
        }
    }

    private fun canManageAdministrators(value: XingDunGroupDetail): Boolean =
        supportsAdministratorRoles(value.groupType) &&
            (value.currentUserRole == ROLE_OWNER ||
                (value.currentUserIsAssignedCs && value.currentUserRole == ROLE_ADMINISTRATOR))

    private fun canRemoveAdministrator(value: XingDunGroupDetail, member: XingDunGroupMember): Boolean =
        canManageAdministrators(value) &&
            member.userId != XingDunSessionManager.currentSession()?.timUserId

    private fun supportsAdministratorRoles(groupType: String): Boolean =
        groupType.equals("Public", true) || groupType.equals("Meeting", true) || groupType.equals("Community", true)

    private fun XingDunGroupMember.displayName(): String = nickname.ifBlank { userId }

    private fun infoView(message: String, colors: ColorTokens, warning: Boolean = false) = TextView(this).apply {
        text = message
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(if (warning) WARNING else colors.textColorSecondary)
        background = rounded(if (warning) 0xFFFFF4E5.toInt() else colors.bgColorOperate, 12f)
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
    }

    private fun previewDetail() = XingDunGroupDetail(
        groupId = groupID,
        name = getString(R.string.xingdun_group_info_preview_name),
        currentUserRole = ROLE_OWNER,
        groupType = "Public",
    )

    private fun previewMembers() = listOf(
        XingDunGroupMember("xd_owner", getString(R.string.xingdun_group_preview_owner), role = ROLE_OWNER),
        XingDunGroupMember("xd_admin_01", getString(R.string.xingdun_group_preview_administrator_one), role = ROLE_ADMINISTRATOR),
        XingDunGroupMember("xd_admin_02", getString(R.string.xingdun_group_preview_administrator_two), role = ROLE_ADMINISTRATOR),
        XingDunGroupMember("xd_member_01", getString(R.string.xingdun_group_preview_member_one), role = ROLE_MEMBER),
        XingDunGroupMember("xd_member_02", getString(R.string.xingdun_group_preview_member_two), role = ROLE_MEMBER),
    ).sortedWith(memberComparator)

    private fun card(colors: ColorTokens) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(colors.bgColorOperate, 18f)
        clipToOutline = true
    }

    private fun rowDivider(colors: ColorTokens) = View(this).apply {
        setBackgroundColor(colors.strokeColorPrimary)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 16.dp() }
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

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_administrators_preview"
        private const val ADMINISTRATOR_LIMIT = 10
        private const val ROLE_OWNER = "owner"
        private const val ROLE_ADMINISTRATOR = "administrator"
        private const val ROLE_MEMBER = "member"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val WARNING = 0xFFB36A00.toInt()
        private const val DANGER = 0xFFE34D59.toInt()

        private val memberComparator = compareBy<XingDunGroupMember> {
            when (it.role) {
                ROLE_OWNER -> 0
                ROLE_ADMINISTRATOR -> 1
                else -> 2
            }
        }.thenBy { it.nickname.ifBlank { it.userId }.lowercase() }

        fun intent(context: Context, groupID: String, debugPreview: Boolean = false): Intent =
            Intent(context, XingDunGroupAdministratorsActivity::class.java)
                .putExtra(EXTRA_GROUP_ID, groupID)
                .putExtra(EXTRA_DEBUG_PREVIEW, debugPreview)
    }
}
