package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.common.Event
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMemberPager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Tenant-authoritative group-management page aligned with the active iOS implementation. */
open class XingDunGroupManagementActivity : BaseActivity() {

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
    private var administratorCount = 0
    private var warningMessage: String? = null
    private var isOperating = false
    private var hasResumedOnce = false

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
            administratorCount = 2
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

    override fun onResume() {
        super.onResume()
        if (isDebugPreview) return
        if (!hasResumedOnce) {
            hasResumedOnce = true
            return
        }
        if (!isOperating && detail != null) {
            load()
        }
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
        titleView.setText(R.string.xingdun_group_management)
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
        val scope = activityScope ?: return
        scope.launch {
            val result = runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                val loadedDetail = XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session,
                    "team/detail",
                    mapOf("team_id" to groupID),
                    XingDunGroupDetail::class.java,
                )
                val members = XingDunGroupMemberPager.loadAll(
                    XingDunSessionManager.apiClient(),
                    session,
                    groupID,
                    getString(R.string.xingdun_group_members_pagination_failed),
                )
                loadedDetail to members.count { it.role == "administrator" }
            }
            refresh.isRefreshing = false
            result.onSuccess { (loadedDetail, loadedAdministratorCount) ->
                detail = loadedDetail
                administratorCount = loadedAdministratorCount
                warningMessage = null
                render(loadedDetail)
            }.onFailure(::showLoadError)
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ProgressBar(this), LinearLayout.LayoutParams(48.dp(), 48.dp()).apply {
            topMargin = 80.dp()
        })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_management_loading)
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
            setText(R.string.xingdun_group_management_load_failed)
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
        content.removeAllViews()
        content.gravity = Gravity.NO_GRAVITY
        content.setBackgroundColor(current.bgColorTopBar)

        if (value.canEditManagement) {
            addCardSection(null, card(current).apply {
                addView(navigateRow(
                    title = getString(R.string.xingdun_group_administrator_management),
                    value = administratorCount.toString(),
                    colors = current,
                    onClick = {
                        startActivity(
                            XingDunGroupAdministratorsActivity.intent(
                                this@XingDunGroupManagementActivity,
                                groupID,
                                isDebugPreview,
                            ),
                        )
                    },
                ), matchWrap())
            })
        }

        val permissions = card(current)
        permissions.addView(toggleRow(
            title = getString(R.string.xingdun_group_mute_all),
            checked = value.muteAll,
            enabled = value.canSetMuteAll && !isOperating,
            colors = current,
        ) { enabled -> setMuteAll(enabled) })
        PolicyField.entries.forEach { field ->
            permissions.addView(rowDivider(current))
            permissions.addView(navigateRow(
                title = getString(field.titleRes),
                value = getString(if (field.value(value) == MODE_ALL) {
                    R.string.xingdun_group_permission_everyone
                } else {
                    R.string.xingdun_group_permission_administrators
                }),
                colors = current,
                enabled = value.canManagePolicies && !isOperating,
                onClick = { showPermissionDialog(field, value) },
            ))
        }
        addCardSection(getString(R.string.xingdun_group_permission_settings), permissions)

        val join = card(current)
        join.addView(toggleRow(
            title = getString(R.string.xingdun_group_invitee_confirmation),
            checked = value.beInviteMode == BE_INVITE_CONFIRM,
            enabled = value.canManagePolicies && !isOperating,
            colors = current,
        ) { enabled -> updatePolicy("be_invite_mode", if (enabled) BE_INVITE_CONFIRM else BE_INVITE_NONE) })
        join.addView(rowDivider(current))
        join.addView(toggleRow(
            title = getString(R.string.xingdun_group_join_requires_approval),
            checked = value.joinMode == JOIN_MODE_APPROVAL,
            enabled = value.supportsJoinMode && value.canManagePolicies && !isOperating,
            colors = current,
        ) { enabled -> updatePolicy("join_mode", if (enabled) JOIN_MODE_APPROVAL else JOIN_MODE_FREE) })
        addCardSection(getString(R.string.xingdun_group_join_settings), join)

        if (value.canLeave || value.canDismiss) {
            val danger = card(current)
            if (value.canLeave) {
                danger.addView(dangerRow(getString(R.string.xingdun_group_leave)) {
                    confirmDanger(DangerAction.LEAVE)
                })
            }
            if (value.canLeave && value.canDismiss) danger.addView(rowDivider(current))
            if (value.canDismiss) {
                danger.addView(dangerRow(getString(R.string.xingdun_group_dismiss)) {
                    confirmDanger(DangerAction.DISMISS)
                })
            }
            addCardSection(null, danger)
        }

        warningMessage?.takeIf(String::isNotBlank)?.let { message ->
            content.addView(TextView(this).apply {
                text = message
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(WARNING)
                background = rounded(0xFFFFF4E5.toInt(), 12f)
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            }, matchWrap().apply { topMargin = 12.dp() })
        }

        if (isOperating) {
            content.addView(TextView(this).apply {
                setText(R.string.xingdun_group_management_processing)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(current.textColorSecondary)
                setPadding(0, 16.dp(), 0, 4.dp())
            }, matchWrap())
        }
    }

    private fun addCardSection(title: String?, view: View) {
        if (!title.isNullOrBlank()) {
            content.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(colors().textColorSecondary)
                setPadding(4.dp(), if (content.childCount == 0) 0 else 18.dp(), 4.dp(), 7.dp())
            }, matchWrap())
        }
        content.addView(view, matchWrap().apply {
            if (title.isNullOrBlank() && content.childCount > 1) topMargin = 14.dp()
        })
    }

    private fun navigateRow(
        title: String,
        value: String,
        colors: ColorTokens,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 58.dp()
        setPadding(16.dp(), 10.dp(), 12.dp(), 10.dp())
        isClickable = enabled
        isFocusable = enabled
        alpha = if (enabled) 1f else 0.48f
        if (enabled) setOnClickListener { onClick() }
        addView(TextView(this@XingDunGroupManagementActivity).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@XingDunGroupManagementActivity).apply {
            text = value
            gravity = Gravity.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(if (enabled) BRAND else colors.textColorSecondary)
        })
        addView(chevron(colors), LinearLayout.LayoutParams(18.dp(), 24.dp()).apply { marginStart = 6.dp() })
    }

    private fun toggleRow(
        title: String,
        checked: Boolean,
        enabled: Boolean,
        colors: ColorTokens,
        onChanged: (Boolean) -> Unit,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 58.dp()
        setPadding(16.dp(), 8.dp(), 12.dp(), 8.dp())
        alpha = if (enabled) 1f else 0.48f
        addView(TextView(this@XingDunGroupManagementActivity).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(SwitchCompat(this@XingDunGroupManagementActivity).apply {
            isChecked = checked
            isEnabled = enabled
            buttonTintList = null
            thumbTintList = switchThumbColors()
            trackTintList = switchTrackColors()
            setOnCheckedChangeListener { _, newValue -> onChanged(newValue) }
        })
    }

    private fun dangerRow(title: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            minimumHeight = 58.dp()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(DANGER)
            isEnabled = !isOperating
            isClickable = !isOperating
            if (!isOperating) setOnClickListener { onClick() }
        }

    private fun showPermissionDialog(field: PolicyField, current: XingDunGroupDetail) {
        val currentMode = field.value(current)
        var selectedMode = currentMode
        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        }
        lateinit var everyone: RadioButton
        lateinit var administrators: RadioButton
        fun option(mode: Int, titleRes: Int, detailRes: Int, expectationRes: Int): View =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
                background = rounded(colors().bgColorOperate, 12f)
                val header = LinearLayout(this@XingDunGroupManagementActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val radio = RadioButton(this@XingDunGroupManagementActivity).apply {
                    isChecked = currentMode == mode
                    isClickable = false
                }
                if (mode == MODE_ALL) everyone = radio else administrators = radio
                header.addView(radio)
                header.addView(TextView(this@XingDunGroupManagementActivity).apply {
                    setText(titleRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(colors().textColorPrimary)
                })
                addView(header, matchWrap())
                addView(TextView(this@XingDunGroupManagementActivity).apply {
                    setText(detailRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(colors().textColorSecondary)
                    setPadding(50.dp(), 2.dp(), 4.dp(), 0)
                }, matchWrap())
                addView(TextView(this@XingDunGroupManagementActivity).apply {
                    setText(expectationRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(BRAND)
                    setPadding(50.dp(), 6.dp(), 4.dp(), 0)
                }, matchWrap())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedMode = mode
                    everyone.isChecked = mode == MODE_ALL
                    administrators.isChecked = mode == MODE_ADMINISTRATORS
                }
            }
        options.addView(option(
            MODE_ALL,
            R.string.xingdun_group_permission_everyone,
            field.everyoneDetailRes,
            field.everyoneExpectationRes,
        ), matchWrap())
        options.addView(option(
            MODE_ADMINISTRATORS,
            R.string.xingdun_group_permission_administrators,
            field.administratorsDetailRes,
            field.administratorsExpectationRes,
        ), matchWrap().apply { topMargin = 10.dp() })
        val dialog = AlertDialog.Builder(this)
            .setTitle(field.titleRes)
            .setView(options)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_group_permission_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selectedMode == currentMode) {
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                    updatePolicy(field.serverField, selectedMode)
                }
            }
        }
        dialog.show()
    }

    private fun updatePolicy(field: String, value: Int) {
        val previous = detail ?: return
        val updated = previous.withPolicy(field, value)
        if (isDebugPreview) {
            detail = updated
            warningMessage = null
            render(updated)
            Toast.makeText(this, R.string.xingdun_group_management_updated, Toast.LENGTH_SHORT).show()
            return
        }
        operate(
            optimistic = updated,
            action = {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().postEmpty(
                    session,
                    "team/update",
                    mapOf("team_id" to groupID, field to value),
                )
            },
            successMessage = getString(R.string.xingdun_group_management_updated),
        )
    }

    private fun setMuteAll(enabled: Boolean) {
        val previous = detail ?: return
        val updated = previous.copy(
            muteAll = enabled,
            muteAllLevel = if (enabled) {
                if (previous.currentUserIsAssignedCs) MUTE_LEVEL_CUSTOMER_SERVICE else MUTE_LEVEL_MANAGER
            } else {
                MUTE_LEVEL_NONE
            },
        )
        if (isDebugPreview) {
            detail = updated
            render(updated)
            Toast.makeText(
                this,
                if (enabled) R.string.xingdun_group_mute_all_enabled else R.string.xingdun_group_mute_all_disabled,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        operate(
            optimistic = updated,
            action = {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().postEmpty(
                    session,
                    "team/setMuteAll",
                    mapOf("team_id" to groupID, "is_muted" to enabled),
                )
            },
            successMessage = getString(
                if (enabled) R.string.xingdun_group_mute_all_enabled else R.string.xingdun_group_mute_all_disabled,
            ),
        )
    }

    private fun operate(
        optimistic: XingDunGroupDetail,
        action: suspend () -> Unit,
        successMessage: String,
    ) {
        if (isOperating) return
        val previous = detail ?: return
        isOperating = true
        warningMessage = null
        detail = optimistic
        render(optimistic)
        activityScope?.launch {
            runCatching { action() }
                .onSuccess {
                    isOperating = false
                    Toast.makeText(this@XingDunGroupManagementActivity, successMessage, Toast.LENGTH_SHORT).show()
                    load()
                }
                .onFailure { error ->
                    isOperating = false
                    detail = previous
                    warningMessage = error.localizedMessage ?: getString(R.string.xingdun_group_management_action_failed)
                    render(previous)
                }
        }
    }

    private fun confirmDanger(action: DangerAction) {
        AlertDialog.Builder(this)
            .setTitle(action.titleRes)
            .setMessage(action.messageRes)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(action.actionRes) { _, _ -> performDanger(action) }
            .show()
    }

    private fun performDanger(action: DangerAction) {
        if (isDebugPreview) {
            Toast.makeText(this, action.successRes, Toast.LENGTH_SHORT).show()
            return
        }
        if (isOperating) return
        isOperating = true
        detail?.let(::render)
        val businessAction = when (action) {
            DangerAction.LEAVE -> BusinessAction.LeaveGroup(groupID)
            DangerAction.DISMISS -> BusinessAction.DismissGroup(groupID)
        }
        val handled = BusinessActionRegistry.dispatch(businessAction, object : BusinessActionCompletion {
            override fun onSuccess(result: BusinessActionResult) {
                isOperating = false
                EventBus.post(Event.GroupDeleted(groupID))
                Toast.makeText(this@XingDunGroupManagementActivity, action.successRes, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            override fun onFailure(code: Int, description: String) {
                isOperating = false
                warningMessage = description.ifBlank { getString(R.string.xingdun_group_management_action_failed) }
                detail?.let(::render)
            }
        })
        if (!handled) {
            isOperating = false
            warningMessage = getString(R.string.xingdun_group_management_handler_unavailable)
            detail?.let(::render)
        }
    }

    private fun XingDunGroupDetail.withPolicy(field: String, value: Int): XingDunGroupDetail = when (field) {
        "join_mode" -> copy(joinMode = value)
        "invite_mode" -> copy(inviteMode = value)
        "update_team_mode" -> copy(updateTeamMode = value)
        "at_all_mode" -> copy(atAllMode = value)
        "view_member_card_mode" -> copy(viewMemberCardMode = value)
        "pin_message_mode" -> copy(pinMessageMode = value)
        "be_invite_mode" -> copy(beInviteMode = value)
        else -> this
    }

    private fun previewDetail() = XingDunGroupDetail(
        groupId = groupID,
        displayGroupId = "100284",
        name = getString(R.string.xingdun_group_info_preview_name),
        memberCount = 18,
        currentUserRole = "owner",
        groupType = "Public",
        joinMode = JOIN_MODE_APPROVAL,
        inviteMode = MODE_ALL,
        updateTeamMode = MODE_ADMINISTRATORS,
        atAllMode = MODE_ADMINISTRATORS,
        beInviteMode = BE_INVITE_CONFIRM,
        viewMemberCardMode = MODE_ALL,
        pinMessageMode = MODE_ADMINISTRATORS,
        muteAll = false,
        muteAllLevel = MUTE_LEVEL_NONE,
    )

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

    private fun switchThumbColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFF5F5F5.toInt()),
    )

    private fun switchTrackColors() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(BRAND, 0xFFB8BEC6.toInt()),
    )

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

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private enum class PolicyField(
        val titleRes: Int,
        val serverField: String,
        val everyoneDetailRes: Int,
        val administratorsDetailRes: Int,
        val everyoneExpectationRes: Int,
        val administratorsExpectationRes: Int,
    ) {
        UPDATE_INFO(
            R.string.xingdun_group_permission_update_info,
            "update_team_mode",
            R.string.xingdun_group_permission_update_info_everyone_detail,
            R.string.xingdun_group_permission_update_info_administrators_detail,
            R.string.xingdun_group_permission_update_info_everyone_expectation,
            R.string.xingdun_group_permission_update_info_administrators_expectation,
        ),
        INVITE(
            R.string.xingdun_group_permission_invite,
            "invite_mode",
            R.string.xingdun_group_permission_invite_everyone_detail,
            R.string.xingdun_group_permission_invite_administrators_detail,
            R.string.xingdun_group_permission_invite_everyone_expectation,
            R.string.xingdun_group_permission_invite_administrators_expectation,
        ),
        MENTION_ALL(
            R.string.xingdun_group_permission_mention_all,
            "at_all_mode",
            R.string.xingdun_group_permission_mention_all_everyone_detail,
            R.string.xingdun_group_permission_mention_all_administrators_detail,
            R.string.xingdun_group_permission_mention_all_everyone_expectation,
            R.string.xingdun_group_permission_mention_all_administrators_expectation,
        ),
        VIEW_MEMBER_CARD(
            R.string.xingdun_group_permission_view_member_card,
            "view_member_card_mode",
            R.string.xingdun_group_permission_view_member_card_everyone_detail,
            R.string.xingdun_group_permission_view_member_card_administrators_detail,
            R.string.xingdun_group_permission_view_member_card_everyone_expectation,
            R.string.xingdun_group_permission_view_member_card_administrators_expectation,
        ),
        PIN_MESSAGE(
            R.string.xingdun_group_permission_pin_message,
            "pin_message_mode",
            R.string.xingdun_group_permission_pin_message_everyone_detail,
            R.string.xingdun_group_permission_pin_message_administrators_detail,
            R.string.xingdun_group_permission_pin_message_everyone_expectation,
            R.string.xingdun_group_permission_pin_message_administrators_expectation,
        );

        fun value(detail: XingDunGroupDetail): Int = when (this) {
            UPDATE_INFO -> detail.updateTeamMode
            INVITE -> detail.inviteMode
            MENTION_ALL -> detail.atAllMode
            VIEW_MEMBER_CARD -> detail.viewMemberCardMode
            PIN_MESSAGE -> detail.pinMessageMode
        }
    }

    private enum class DangerAction(
        val titleRes: Int,
        val messageRes: Int,
        val actionRes: Int,
        val successRes: Int,
    ) {
        LEAVE(
            R.string.xingdun_group_leave_confirm_title,
            R.string.xingdun_group_leave_confirm_message,
            R.string.xingdun_group_leave,
            R.string.xingdun_group_leave_success,
        ),
        DISMISS(
            R.string.xingdun_group_dismiss_confirm_title,
            R.string.xingdun_group_dismiss_confirm_message,
            R.string.xingdun_group_dismiss,
            R.string.xingdun_group_dismiss_success,
        ),
    }

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_management_preview"
        private const val MODE_ALL = 1
        private const val MODE_ADMINISTRATORS = 2
        private const val JOIN_MODE_FREE = 1
        private const val JOIN_MODE_APPROVAL = 2
        private const val BE_INVITE_CONFIRM = 1
        private const val BE_INVITE_NONE = 2
        private const val MUTE_LEVEL_NONE = 0
        private const val MUTE_LEVEL_MANAGER = 1
        private const val MUTE_LEVEL_CUSTOMER_SERVICE = 2
        private const val BRAND = 0xFF23B39C.toInt()
        private const val WARNING = 0xFFB36A00.toInt()
        private const val DANGER = 0xFFE34D59.toInt()

        fun start(context: Context, groupID: String) {
            context.startActivity(intent(context, groupID, false))
        }

        fun intent(context: Context, groupID: String, debugPreview: Boolean): Intent =
            Intent(context, XingDunGroupManagementActivity::class.java)
                .putExtra(EXTRA_GROUP_ID, groupID)
                .putExtra(EXTRA_DEBUG_PREVIEW, debugPreview)
    }
}
