package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.AlertDialog
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
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupNameChangedException
import io.trtc.tuikit.chat.demo.xingdun.network.groupNameUpdatePayload
import io.trtc.tuikit.chat.demo.xingdun.network.isGroupNameChangeConflict
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    private var profileOwner: XingDunGroupProfileOperationOwner? = null
    private var authorityAvailable = false
    @Volatile private var resumed = false
    private var detailRequest = 0L
    private var activeEditor: ProfileEditor? = null
    private val isDebugPreview: Boolean
        get() = intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private class ProfileEditor(
        val isName: Boolean,
        val openedName: String?,
        val dialog: AlertDialog,
        val input: EditText,
        val feedback: TextView,
        var submitting: Boolean = false,
        var revoked: Boolean = false,
        var resultPending: Boolean = false,
    )

    // Returning from either Save or Cancel always reauthorizes in onResume.
    private val groupAvatarEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

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
        profileOwner = XingDunGroupProfileOperationOwner(getString(R.string.xingdun_session_expired))
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
            authorityAvailable = true
            renderDetail(requireNotNull(detail))
            refresh.isEnabled = false
        } else {
            showLoading()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::content.isInitialized) {
            if (isDebugPreview) detail?.let(::renderDetail) else loadDetail(force = true)
        }
    }

    override fun onPause() {
        resumed = false
        if (!isDebugPreview && ::content.isInitialized) {
            detailRequest++
            authorityAvailable = false
            detail?.let(::renderDetail)
            updateEditorControls()
        }
        super.onPause()
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

    private fun ownerIsCurrent(): Boolean =
        resumed && !isFinishing && !isDestroyed && (isDebugPreview || profileOwner?.isCurrent() == true)

    private fun canEdit(value: XingDunGroupDetail): Boolean =
        authorityAvailable && ownerIsCurrent() && value.canEditGroupInfo

    private fun loadDetail(force: Boolean = false) {
        val scope = activityScope ?: return
        val owner = profileOwner ?: return
        if (activeEditor?.submitting == true) return
        val ticket = ++detailRequest
        authorityAvailable = false
        if (!force && detail == null) showLoading() else detail?.let(::renderDetail)
        updateEditorControls()
        scope.launch {
            try {
                val loaded = owner.detail(groupID)
                if (ticket != detailRequest || !ownerIsCurrent()) return@launch
                detail = loaded
                authorityAvailable = true
                if (!loaded.canEditGroupInfo) activeEditor?.revoked = true
                renderDetail(loaded)
                if (!loaded.canEditGroupInfo) editorFeedback(permissionMessage(loaded))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ticket != detailRequest || isFinishing || isDestroyed) return@launch
                authorityAvailable = false
                val message = profileErrorMessage(error)
                val cached = detail
                if (cached == null) showLoadError(error) else renderDetail(cached, message)
                editorFeedback(message)
            } finally {
                if (ticket == detailRequest) {
                    refresh.isRefreshing = false
                    updateEditorControls()
                }
            }
        }
    }

    private fun profileErrorMessage(error: Exception, nameEdit: Boolean = false): String =
        if (nameEdit && isGroupNameChangeConflict(error)) getString(R.string.xingdun_group_info_name_changed)
        else if (error is XingDunApiException) error.localizedMessage?.takeIf(String::isNotBlank)
            ?: getString(R.string.xingdun_action_failed)
        else getString(R.string.xingdun_group_info_load_failed_message)

    private fun permissionMessage(value: XingDunGroupDetail): String =
        getString(if (value.currentUserRole == "member" && value.updateTeamMode != 1)
            R.string.xingdun_group_permission_update_info_administrators_detail
        else R.string.xingdun_action_failed)

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
            showsDisclosure = canEdit(value),
            onClick = if (canEdit(value)) ({ showNameEditor(value) }) else null,
        ))
        identity.addView(rowDivider(current))
        identity.addView(textRow(
            title = getString(R.string.xingdun_group_info_intro),
            value = value.intro?.takeIf(String::isNotBlank) ?: getString(R.string.xingdun_not_set),
            colors = current,
            showsDisclosure = canEdit(value),
            multiline = true,
            onClick = if (canEdit(value)) ({ showIntroductionEditor(value) }) else null,
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
            isClickable = canEdit(value)
            isFocusable = canEdit(value)
            if (canEdit(value)) setOnClickListener {
                if (!canEdit(value)) return@setOnClickListener
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
        if (canEdit(value)) {
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

    private fun showIntroductionEditor(value: XingDunGroupDetail) = showProfileEditor(value, isName = false)

    private fun showNameEditor(value: XingDunGroupDetail) = showProfileEditor(value, isName = true)

    private fun showProfileEditor(value: XingDunGroupDetail, isName: Boolean) {
        if (!canEdit(value) || activeEditor != null) return
        val current = colors()
        val limit = if (isName) NAME_MAX_BYTES else INTRO_MAX_BYTES
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }
        val editor = EditText(this).apply {
            setText(if (isName) value.name else value.intro.orEmpty())
            setSelection(text.length)
            if (isName) setSingleLine() else {
                minHeight = 130.dp()
                gravity = Gravity.TOP or Gravity.START
            }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or if (isName)
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(current.textColorPrimary)
            setHintTextColor(current.textColorTertiary)
            setHint(if (isName) R.string.xingdun_group_info_name_hint else R.string.xingdun_group_info_intro_hint)
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
            counter.text = getString(R.string.xingdun_group_info_byte_count,
                editor.text.toString().toByteArray(Charsets.UTF_8).size, limit)
        }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateCounter()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateCounter()
        val feedback = TextView(this).apply {
            visibility = View.GONE
            setTextColor(WARNING)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 8.dp(), 0, 0)
        }
        wrapper.addView(editor, matchWrap())
        wrapper.addView(counter, matchWrap())
        wrapper.addView(feedback, matchWrap())
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isName) R.string.xingdun_group_info_name else R.string.xingdun_group_info_intro)
            .setView(wrapper)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_group_info_save, null)
            .create()
        val state = ProfileEditor(
            isName = isName,
            openedName = if (isName) value.name else null,
            dialog = dialog,
            input = editor,
            feedback = feedback,
        )
        activeEditor = state
        dialog.setOnDismissListener { if (activeEditor === state) activeEditor = null }
        dialog.setOnShowListener {
            editor.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = editor.text.toString().trim()
                when {
                    isName && candidate.isEmpty() -> Toast.makeText(this,
                        R.string.xingdun_group_info_name_required, Toast.LENGTH_LONG).show()
                    candidate.toByteArray(Charsets.UTF_8).size > limit -> Toast.makeText(this,
                        if (isName) R.string.xingdun_group_info_name_too_long else R.string.xingdun_group_info_intro_too_long,
                        Toast.LENGTH_LONG).show()
                    else -> saveProfile(candidate, state)
                }
            }
            updateEditorControls()
        }
        dialog.show()
    }

    private fun editorFeedback(message: String) {
        activeEditor?.feedback?.apply { text = message; visibility = View.VISIBLE }
    }

    private fun updateEditorControls() {
        val state = activeEditor ?: return
        val editable = detail?.let(::canEdit) == true && !state.submitting && !state.revoked && !state.resultPending
        state.input.isEnabled = editable
        state.dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = editable
        state.dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !state.submitting
        state.dialog.setCancelable(!state.submitting)
        state.dialog.setCanceledOnTouchOutside(!state.submitting)
    }

    private fun saveProfile(value: String, state: ProfileEditor) {
        val scope = activityScope ?: return
        val owner = profileOwner ?: return
        if (activeEditor !== state || state.submitting || state.revoked || state.resultPending ||
            detail?.let(::canEdit) != true) return
        state.submitting = true
        authorityAvailable = false
        detailRequest++ // A pre-submit read must never be replaced by an older in-flight refresh.
        updateEditorControls()
        scope.launch {
            var writeAttempted = false
            try {
                val latest = owner.detail(groupID)
                if (activeEditor !== state || !ownerIsCurrent()) return@launch
                detail = latest
                authorityAvailable = true
                renderDetail(latest)
                if (!latest.canEditGroupInfo) {
                    state.revoked = true
                    editorFeedback(permissionMessage(latest))
                    return@launch
                }
                val body = if (state.isName) {
                    groupNameUpdatePayload(groupID, checkNotNull(state.openedName), latest.name, value)
                } else if (latest.intro.orEmpty() == value) null
                else mapOf("team_id" to groupID, "intro" to value)
                if (body == null) {
                    profileSaved(latest, state)
                    return@launch
                }
                writeAttempted = true
                owner.request { session ->
                    if (!resumed) throw CancellationException()
                    XingDunSessionManager.apiClient().postEmpty(session, "team/update", body)
                }
                if (activeEditor !== state || !ownerIsCurrent()) return@launch
                profileSaved(if (state.isName) latest.copy(name = value) else latest.copy(intro = value), state)
            } catch (cancelled: CancellationException) {
                if (writeAttempted) {
                    state.resultPending = true
                    authorityAvailable = false
                    if (activeEditor === state) editorFeedback(getString(R.string.xingdun_pending))
                }
                throw cancelled
            } catch (error: Exception) {
                state.resultPending = writeAttempted && !isDefiniteGroupProfileRejection(error)
                if (activeEditor !== state || isFinishing || isDestroyed) return@launch
                val actual = when {
                    writeAttempted -> try { owner.detail(groupID) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    error is XingDunGroupNameChangedException -> detail // The just-confirmed pre-submit detail.
                    else -> null
                }
                if (activeEditor !== state || !ownerIsCurrent()) {
                    authorityAvailable = false
                    return@launch
                }
                if (actual != null) detail = actual
                authorityAvailable = actual != null
                if (actual != null && !actual.canEditGroupInfo) state.revoked = true
                if (writeAttempted && !isDefiniteGroupProfileRejection(error) && actual != null &&
                    (if (state.isName) actual.name else actual.intro.orEmpty()) == value) {
                    profileSaved(actual, state)
                    return@launch
                }
                // A failed/old read does not prove an uncertain write was not committed.
                state.resultPending = writeAttempted && !isDefiniteGroupProfileRejection(error)
                detail?.let { renderDetail(it, profileErrorMessage(error, nameEdit = state.isName)) }
                editorFeedback(if (state.resultPending) getString(R.string.xingdun_pending)
                    else profileErrorMessage(error, nameEdit = state.isName))
            } finally {
                state.submitting = false
                if (activeEditor === state) updateEditorControls()
            }
        }
    }

    private fun profileSaved(value: XingDunGroupDetail, state: ProfileEditor) {
        detail = value
        authorityAvailable = false
        if (activeEditor === state) activeEditor = null
        state.dialog.dismiss()
        renderDetail(value)
        GroupStore.shared.loadJoinedGroups()
        Toast.makeText(this,
            if (state.isName) R.string.xingdun_group_info_name_updated else R.string.xingdun_group_info_intro_updated,
            Toast.LENGTH_SHORT).show()
        loadDetail(force = true)
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
        private const val NAME_MAX_BYTES = 100
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


/** Profile editing borrows the existing authentication generation; credentials live only in IO calls. */
internal class XingDunGroupProfileOperationOwner(private val unavailableMessage: String) {
    private val generation = XingDunSessionManager.currentMessageReadStamp()?.authenticationGeneration

    fun isCurrent(): Boolean = generation != null &&
        XingDunSessionManager.currentMessageReadStamp()?.authenticationGeneration == generation

    suspend fun <T> request(block: suspend (XingDunStoredSession) -> T): T = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        check(isCurrent()) { unavailableMessage }
        val snapshot = XingDunSessionManager.readMessageSessionSnapshot()
        check(snapshot != null && snapshot.first.authenticationGeneration == generation && isCurrent()) { unavailableMessage }
        val result = block(snapshot.second)
        currentCoroutineContext().ensureActive()
        check(isCurrent()) { unavailableMessage }
        result
    }

    suspend fun detail(groupID: String): XingDunGroupDetail = request { session ->
        XingDunSessionManager.apiClient().get<XingDunGroupDetail>(session, "team/detail",
            mapOf("team_id" to groupID), XingDunGroupDetail::class.java)
    }.also { check(it.groupId == groupID) { unavailableMessage } }
}

/** A confirmed rejection can keep a permitted draft editable; transport uncertainty must read back. */
internal fun isDefiniteGroupProfileRejection(error: Exception): Boolean = error is XingDunApiException &&
    (error.httpStatus?.let { it in 400..499 } == true || error.businessCode?.let { it in 400..499 } == true)
