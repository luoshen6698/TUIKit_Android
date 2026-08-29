package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
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
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMemberPager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Full-screen owner-transfer flow matching the active iOS group-settings page. */
open class XingDunGroupTransferOwnerActivity : BaseActivity() {

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
    private var candidates: List<XingDunGroupMember> = emptyList()
    private var listContainer: LinearLayout? = null
    private var searchQuery = ""
    private var isOperating = false
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
                if (detail != null) render()
            }
        }
        if (isDebugPreview) {
            refresh.isEnabled = false
            detail = previewDetail()
            candidates = previewCandidates()
            render()
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
        titleView.setText(R.string.xingdun_group_transfer_owner_title)
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener {
            if (!isOperating) finish()
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
                check(XingDunGroupMemberPolicy.canTransferOwner(loadedDetail)) {
                    getString(R.string.xingdun_group_transfer_owner_only_owner)
                }
                val loadedMembers = XingDunGroupMemberPager.loadAll(
                    client = XingDunSessionManager.apiClient(),
                    session = session,
                    groupID = groupID,
                    invalidPaginationMessage = getString(R.string.xingdun_group_members_pagination_failed),
                )
                loadedDetail to loadedMembers
                    .filterNot { it.role == XingDunGroupMemberPolicy.ROLE_OWNER || it.userId == loadedDetail.ownerUserId }
                    .sortedWith(memberComparator)
            }
            refresh.isRefreshing = false
            result.onSuccess { (loadedDetail, loadedCandidates) ->
                detail = loadedDetail
                candidates = loadedCandidates
                warningMessage = null
                render()
            }.onFailure(::showLoadError)
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ProgressBar(this), LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { topMargin = 80.dp() })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_transfer_owner_loading)
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
            setText(R.string.xingdun_group_transfer_owner_load_failed)
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

    private fun render() {
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.NO_GRAVITY
        content.setBackgroundColor(current.bgColorTopBar)

        content.addView(searchField(current), matchWrap())
        warningMessage?.takeIf(String::isNotBlank)?.let { message ->
            content.addView(infoView(message), matchWrap().apply { topMargin = 10.dp() })
        }
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer, matchWrap().apply { topMargin = 12.dp() })
        renderCandidates()

        if (isOperating) {
            content.addView(TextView(this).apply {
                setText(R.string.xingdun_group_transfer_owner_processing)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(current.textColorSecondary)
                setPadding(0, 16.dp(), 0, 8.dp())
            }, matchWrap())
        }
    }

    private fun searchField(colors: ColorTokens) = EditText(this).apply {
        setSingleLine(true)
        setText(searchQuery)
        setSelection(text.length)
        setHint(R.string.xingdun_group_members_search)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(colors.textColorPrimary)
        setHintTextColor(colors.textColorTertiary)
        background = rounded(colors.bgColorInput, 22f)
        setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
        isEnabled = !isOperating
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                renderCandidates()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderCandidates() {
        val container = listContainer ?: return
        val current = colors()
        val query = searchQuery.trim()
        val visible = candidates.filter {
            query.isBlank() || it.displayName().contains(query, ignoreCase = true) ||
                it.userId.contains(query, ignoreCase = true)
        }
        container.removeAllViews()
        if (visible.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(if (query.isBlank()) {
                    R.string.xingdun_group_transfer_owner_empty
                } else {
                    R.string.xingdun_group_transfer_owner_no_match
                })
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(current.textColorTertiary)
                setPadding(16.dp(), 72.dp(), 16.dp(), 72.dp())
            }, matchWrap())
            return
        }
        val card = card(current)
        visible.forEachIndexed { index, member ->
            if (index > 0) card.addView(rowDivider(current))
            card.addView(candidateRow(member, current))
        }
        container.addView(card, matchWrap())
    }

    private fun candidateRow(member: XingDunGroupMember, colors: ColorTokens) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 70.dp()
            setPadding(14.dp(), 9.dp(), 12.dp(), 9.dp())
            isClickable = !isOperating
            isFocusable = !isOperating
            alpha = if (isOperating) 0.5f else 1f
            if (!isOperating) setOnClickListener { confirmTransfer(member) }
            addView(Avatar(this@XingDunGroupTransferOwnerActivity).apply {
                setSize(Avatar.AvatarSize.M)
                setContent(Avatar.AvatarContent.Image(member.avatar, member.displayName()))
            })
            addView(LinearLayout(this@XingDunGroupTransferOwnerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 8.dp(), 0)
                addView(TextView(this@XingDunGroupTransferOwnerActivity).apply {
                    text = member.displayName()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(colors.textColorPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(this@XingDunGroupTransferOwnerActivity).apply {
                    text = member.userId
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(colors.textColorSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(this@XingDunGroupTransferOwnerActivity).apply {
                setImageResource(R.drawable.demo_ic_arrow_right)
                imageTintList = ColorStateList.valueOf(colors.textColorTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(18.dp(), 24.dp()))
        }

    private fun confirmTransfer(member: XingDunGroupMember) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_group_transfer_owner_confirm_title)
            .setMessage(getString(R.string.xingdun_group_transfer_owner_confirm_message, member.displayName()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_group_transfer_owner_confirm, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(DANGER)
                setOnClickListener {
                    dialog.dismiss()
                    transfer(member)
                }
            }
        }
        dialog.show()
    }

    private fun transfer(member: XingDunGroupMember) {
        if (isOperating) return
        if (isDebugPreview) {
            Toast.makeText(this, R.string.xingdun_group_transfer_owner_success, Toast.LENGTH_SHORT).show()
            return
        }
        isOperating = true
        warningMessage = null
        if (detail != null) render()
        val dispatched = BusinessActionRegistry.dispatch(
            BusinessAction.TransferGroupOwner(groupID, member.userId),
            object : BusinessActionCompletion {
                override fun onSuccess(result: BusinessActionResult) {
                    runOnUiThread {
                        isOperating = false
                        GroupStore.shared.loadJoinedGroups()
                        Toast.makeText(
                            this@XingDunGroupTransferOwnerActivity,
                            R.string.xingdun_group_transfer_owner_success,
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }

                override fun onFailure(code: Int, description: String) {
                    runOnUiThread {
                        isOperating = false
                        warningMessage = description.ifBlank { getString(R.string.xingdun_group_management_action_failed) }
                        if (detail != null) render()
                    }
                }
            },
        )
        if (!dispatched) {
            isOperating = false
            warningMessage = getString(R.string.xingdun_group_management_action_failed)
            if (detail != null) render()
        }
    }

    private fun XingDunGroupMember.displayName(): String = nickname.ifBlank { userId }

    private fun previewDetail() = XingDunGroupDetail(
        groupId = groupID,
        name = getString(R.string.xingdun_group_info_preview_name),
        ownerUserId = PREVIEW_OWNER_ID,
        currentUserRole = XingDunGroupMemberPolicy.ROLE_OWNER,
        groupType = "Public",
    )

    private fun previewCandidates() = listOf(
        XingDunGroupMember("xd_admin_01", getString(R.string.xingdun_group_preview_administrator_one), role = XingDunGroupMemberPolicy.ROLE_ADMINISTRATOR),
        XingDunGroupMember("xd_member_01", getString(R.string.xingdun_group_preview_member_one)),
        XingDunGroupMember("xd_member_02", getString(R.string.xingdun_group_preview_member_two)),
        XingDunGroupMember("xd_member_03", getString(R.string.xingdun_group_preview_member_three)),
    ).sortedWith(memberComparator)

    private fun infoView(message: String) = TextView(this).apply {
        text = message
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(WARNING)
        background = rounded(0xFFFFF4E5.toInt(), 12f)
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
    }

    private fun card(colors: ColorTokens) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(colors.bgColorOperate, 18f)
        clipToOutline = true
    }

    private fun rowDivider(colors: ColorTokens) = View(this).apply {
        setBackgroundColor(colors.strokeColorPrimary)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 66.dp() }
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
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_transfer_owner_preview"
        private const val PREVIEW_OWNER_ID = "xd_owner"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val WARNING = 0xFFB36A00.toInt()
        private const val DANGER = 0xFFE34D59.toInt()

        private val memberComparator = compareBy<XingDunGroupMember> {
            if (it.role == XingDunGroupMemberPolicy.ROLE_ADMINISTRATOR) 0 else 1
        }.thenBy { it.nickname.ifBlank { it.userId }.lowercase() }
            .thenBy { it.userId.lowercase() }

        fun intent(context: Context, groupID: String, debugPreview: Boolean = false): Intent =
            Intent(context, XingDunGroupTransferOwnerActivity::class.java)
                .putExtra(EXTRA_GROUP_ID, groupID)
                .putExtra(EXTRA_DEBUG_PREVIEW, debugPreview)

        fun start(context: Context, groupID: String) {
            context.startActivity(intent(context, groupID))
        }
    }
}
