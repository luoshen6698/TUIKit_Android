package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionStyle
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingCustomAction
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingScene
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.C2CChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.C2CChatSettingViewModelFactory
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class C2CChatSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class LayoutStyle {
        DEFAULT,
        XINGDUN_INSET_GROUPED,
    }

    private var onSendMessageClick: (() -> Unit)? = null
    private var onVoiceCallClick: (() -> Unit)? = null
    private var onVideoCallClick: (() -> Unit)? = null
    private var onAutoDeleteClick: (() -> Unit)? = null
    private var autoDeleteTitle: String? = null
    private var onSearchMessagesClick: (() -> Unit)? = null
    private var searchMessagesTitle: String? = null
    private var onContactDeleted: (() -> Unit)? = null
    private var showChatBackground = true
    private var onCreateGroupClick: (() -> Unit)? = null
    private var layoutStyle: LayoutStyle = LayoutStyle.DEFAULT

    private var viewModel: C2CChatSettingViewModel? = null
    private var viewScope: CoroutineScope? = null

    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout

    private lateinit var userInfoLayout: LinearLayout
    private lateinit var avatarView: Avatar
    private lateinit var nicknameTextView: TextView
    private lateinit var idTextView: TextView
    private lateinit var signatureTextView: TextView

    private var remarkRow: SettingRowNavigate? = null

    private lateinit var doNotDisturbRow: SettingRowToggle
    private lateinit var pinRow: SettingRowToggle
    private lateinit var blacklistRow: SettingRowToggle
    private lateinit var chatBackgroundRow: SettingRowNavigate

    private lateinit var sendMessageButton: SettingRowButton
    private lateinit var voiceCallButton: SettingRowButton
    private lateinit var videoCallButton: SettingRowButton
    private lateinit var clearHistoryButton: SettingRowButton
    private lateinit var deleteFriendButton: SettingRowButton

    private val spacers = mutableListOf<View>()
    private val dividers = mutableListOf<View>()
    private val sectionCards = mutableListOf<LinearLayout>()
    private val sectionTitles = mutableListOf<TextView>()

    private var currentUserID: String? = null
    private var isUiBuilt = false

    fun setup(
        userID: String,
        onSendMessageClick: (() -> Unit)? = null,
        onVoiceCallClick: (() -> Unit)? = null,
        onVideoCallClick: (() -> Unit)? = null,
        autoDeleteTitle: String? = null,
        onAutoDeleteClick: (() -> Unit)? = null,
        searchMessagesTitle: String? = null,
        onSearchMessagesClick: (() -> Unit)? = null,
        onContactDeleted: (() -> Unit)? = null,
        showChatBackground: Boolean = true,
        onCreateGroupClick: (() -> Unit)? = null,
        layoutStyle: LayoutStyle = LayoutStyle.DEFAULT,
    ) {
        this.onSendMessageClick = onSendMessageClick
        this.onVoiceCallClick = onVoiceCallClick
        this.onVideoCallClick = onVideoCallClick
        this.autoDeleteTitle = autoDeleteTitle
        this.onAutoDeleteClick = onAutoDeleteClick
        this.searchMessagesTitle = searchMessagesTitle
        this.onSearchMessagesClick = onSearchMessagesClick
        this.onContactDeleted = onContactDeleted
        this.showChatBackground = showChatBackground
        this.onCreateGroupClick = onCreateGroupClick
        this.layoutStyle = layoutStyle

        val owner = context.findViewModelStoreOwner() ?: return

        cleanupBinding()
        currentUserID = userID
        val viewModelKey = "${C2CChatSettingViewModel::class.java.name}:$userID"
        viewModel = ViewModelProvider(owner, C2CChatSettingViewModelFactory(userID, context))
            .get(viewModelKey, C2CChatSettingViewModel::class.java)

        if (!isUiBuilt) {
            buildUI()
            isUiBuilt = true
        }

        if (isAttachedToWindow) {
            bindViewModel()
        }
    }

    private fun buildUI() {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        removeAllViews()
        spacers.clear()
        dividers.clear()
        sectionCards.clear()
        sectionTitles.clear()
        val dm = resources.displayMetrics
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        scrollView = ScrollView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(colors.bgColorTopBar)
        }

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(colors.bgColorTopBar)
        }

        if (layoutStyle == LayoutStyle.XINGDUN_INSET_GROUPED) {
            buildXingDunInsetGroupedUI(colors)
            scrollView.addView(contentLayout)
            addView(scrollView)
            return
        }

        userInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val horizontalPadding = dp2px(16f, dm).toInt()
            val verticalPadding = dp2px(12f, dm).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        avatarView = Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
        }
        userInfoLayout.addView(avatarView)

        val textInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val leftMargin = dp2px(16f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = leftMargin }
        }

        nicknameTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 1
        }
        textInfoLayout.addView(nicknameTextView)

        idTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val topMargin = dp2px(4f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
        }
        textInfoLayout.addView(idTextView)

        signatureTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            maxLines = 1
            val topMargin = dp2px(2f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
        }
        textInfoLayout.addView(signatureTextView)

        userInfoLayout.addView(textInfoLayout)
        contentLayout.addView(userInfoLayout)

        addSpacer(contentLayout)

        remarkRow = SettingRowNavigate(context).apply {
            setShowArrow(true)
        }.also { row ->
            row.setOnClickListener {
            val vm = viewModel ?: return@setOnClickListener
            TextInputDialog(
                context = context,
                title = context.getString(R.string.chat_setting_modify_contact_remark),
                initialText = vm.friendRemark.value,
                onConfirm = { vm.setFriendRemark(it) }
            ).show()
            }
        }
        remarkRow?.let(contentLayout::addView)

        addSpacer(contentLayout)

        doNotDisturbRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_do_not_disturb))
            onToggleChanged = { checked -> viewModel?.setDoNotDisturb(checked) }
        }
        contentLayout.addView(doNotDisturbRow)

        addDivider(contentLayout)

        pinRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_pin))
            onToggleChanged = { checked -> viewModel?.setPinChat(checked) }
        }
        contentLayout.addView(pinRow)

        if (showChatBackground) {
            addSpacer(contentLayout)
            chatBackgroundRow = SettingRowNavigate(context).apply {
                setTitle(context.getString(R.string.chat_setting_chat_background))
                setShowArrow(true)
                setOnClickListener {
                    viewModel?.let { vm -> showChatBackgroundPicker(vm) }
                }
            }
            contentLayout.addView(chatBackgroundRow)
        }

        if (onSearchMessagesClick != null && !searchMessagesTitle.isNullOrBlank()) {
            addSpacer(contentLayout)
            contentLayout.addView(SettingRowNavigate(context).apply {
                setTitle(searchMessagesTitle.orEmpty())
                setShowArrow(true)
                setOnClickListener { onSearchMessagesClick?.invoke() }
            })
        }

        if (onAutoDeleteClick != null && !autoDeleteTitle.isNullOrBlank()) {
            addSpacer(contentLayout)
            contentLayout.addView(SettingRowNavigate(context).apply {
                setTitle(autoDeleteTitle.orEmpty())
                setShowArrow(true)
                setOnClickListener { onAutoDeleteClick?.invoke() }
            })
        }

        addSpacer(contentLayout)

        blacklistRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_add_blacklist))
            onToggleChanged = { viewModel?.toggleBlacklist() }
        }
        contentLayout.addView(blacklistRow)

        addSpacer(contentLayout)

        val actionRows = C2CChatSettingActionPolicy.actions().map { action ->
            when (action) {
                C2CChatSettingAction.SEND_MESSAGE -> createSendMessageButton().also { sendMessageButton = it }
                C2CChatSettingAction.VOICE_CALL -> createVoiceCallButton().also { voiceCallButton = it }
                C2CChatSettingAction.VIDEO_CALL -> createVideoCallButton().also { videoCallButton = it }
                C2CChatSettingAction.CLEAR_HISTORY -> createClearHistoryButton().also { clearHistoryButton = it }
                C2CChatSettingAction.DELETE_FRIEND -> createDeleteFriendButton().also { deleteFriendButton = it }
            }
        }
        actionRows.forEachIndexed { index, row ->
            contentLayout.addView(row)
            if (index != actionRows.lastIndex) {
                addDivider(contentLayout)
            }
        }

        appendCustomActions(contentLayout, actionRows.isNotEmpty())

        scrollView.addView(contentLayout)
        addView(scrollView)
    }

    private fun buildXingDunInsetGroupedUI(colors: ColorTokens) {
        val dm = resources.displayMetrics
        val horizontalInset = dp2px(16f, dm).toInt()
        contentLayout.setPadding(horizontalInset, dp2px(8f, dm).toInt(), horizontalInset, dp2px(28f, dm).toInt())
        contentLayout.setBackgroundColor(colors.bgColorDefault)
        scrollView.setBackgroundColor(colors.bgColorDefault)

        val memberCard = createInsetCard(colors)
        userInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(14f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(12f, dm).toInt(),
            )
        }

        val memberColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp2px(72f, dm).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        avatarView = Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
            setShape(Avatar.AvatarShape.RoundRectangle)
        }
        memberColumn.addView(
            avatarView,
            LinearLayout.LayoutParams(
                dp2px(48f, context.resources.displayMetrics).toInt(),
                dp2px(48f, context.resources.displayMetrics).toInt(),
            ),
        )
        nicknameTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp2px(6f, dm).toInt() }
        }
        memberColumn.addView(nicknameTextView)
        userInfoLayout.addView(memberColumn)

        onCreateGroupClick?.let { createGroupAction ->
            val createGroupColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(R.string.chat_setting_create_group)
                layoutParams = LinearLayout.LayoutParams(dp2px(72f, dm).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp2px(4f, dm).toInt()
                }
                setOnClickListener { createGroupAction() }
            }
            val addIcon = ImageView(context).apply {
                setImageResource(R.drawable.uikit_ic_user_add)
                setColorFilter(colors.textColorSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                background = roundedBackground(colors.bgColorDefault, 9f)
                setPadding(
                    dp2px(11f, dm).toInt(),
                    dp2px(11f, dm).toInt(),
                    dp2px(11f, dm).toInt(),
                    dp2px(11f, dm).toInt(),
                )
            }
            createGroupColumn.addView(
                addIcon,
                LinearLayout.LayoutParams(dp2px(48f, dm).toInt(), dp2px(48f, dm).toInt()),
            )
            createGroupColumn.addView(TextView(context).apply {
                text = context.getString(R.string.chat_setting_create_group)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(colors.textColorSecondary)
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp2px(6f, dm).toInt() }
            })
            userInfoLayout.addView(createGroupColumn)
        }

        idTextView = TextView(context).apply { visibility = View.GONE }
        signatureTextView = TextView(context).apply { visibility = View.GONE }
        memberCard.addView(userInfoLayout)
        contentLayout.addView(memberCard)

        addSectionTitle(context.getString(R.string.chat_setting_conversation_section))
        val conversationCard = createInsetCard(colors)
        pinRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_pin_conversation))
            onToggleChanged = { checked -> viewModel?.setPinChat(checked) }
        }
        conversationCard.addView(pinRow)
        addDivider(conversationCard)
        doNotDisturbRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_do_not_disturb))
            onToggleChanged = { checked -> viewModel?.setDoNotDisturb(checked) }
        }
        conversationCard.addView(doNotDisturbRow)
        addDivider(conversationCard)
        blacklistRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_add_blacklist))
            onToggleChanged = { viewModel?.toggleBlacklist() }
        }
        conversationCard.addView(blacklistRow)
        contentLayout.addView(conversationCard)

        addSectionTitle(context.getString(R.string.chat_setting_history_section))
        val historyCard = createInsetCard(colors)
        val historyRows = mutableListOf<View>()
        if (onSearchMessagesClick != null && !searchMessagesTitle.isNullOrBlank()) {
            historyRows += SettingRowNavigate(context).apply {
                setTitle(searchMessagesTitle.orEmpty())
                setPrimaryTitleStyle(true)
                setLeadingIcon(R.drawable.uikit_ic_search)
                setShowArrow(true)
                setOnClickListener { onSearchMessagesClick?.invoke() }
            }
        }
        if (onAutoDeleteClick != null && !autoDeleteTitle.isNullOrBlank()) {
            historyRows += SettingRowNavigate(context).apply {
                setTitle(autoDeleteTitle.orEmpty())
                setPrimaryTitleStyle(true)
                setLeadingIcon(R.drawable.uikit_ic_history_timer)
                setShowArrow(true)
                setOnClickListener { onAutoDeleteClick?.invoke() }
            }
        }
        historyRows += SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_clear_history_messages))
            setLeadingIcon(R.drawable.uikit_ic_delete_outline)
            setShowArrow(false)
            setDangerStyle(true)
            setOnClickListener { showClearHistoryConfirmation(this) }
        }
        historyRows.forEachIndexed { index, row ->
            historyCard.addView(row)
            if (index != historyRows.lastIndex) addDivider(historyCard)
        }
        contentLayout.addView(historyCard)
    }

    private fun createInsetCard(colors: ColorTokens): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(colors.bgColorOperate, 18f)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }.also(sectionCards::add)
    }

    private fun addSectionTitle(title: String) {
        val dm = resources.displayMetrics
        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ThemeStore.shared(context).themeState.value.currentTheme.tokens.color.textColorTertiary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp2px(22f, dm).toInt()
                bottomMargin = dp2px(8f, dm).toInt()
                marginStart = dp2px(10f, dm).toInt()
            }
        }
        sectionTitles += titleView
        contentLayout.addView(titleView)
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp2px(radiusDp, resources.displayMetrics)
        }
    }

    private fun appendCustomActions(parent: LinearLayout, hasPrecedingActions: Boolean) {
        val provider = ChatSettingActionConfig.customActionProvider ?: return
        val actions = provider.getActions(
            ChatSettingActionContext(
                context = context,
                scene = ChatSettingScene.C2C,
                userID = currentUserID,
                groupID = null
            )
        )
        actions.forEachIndexed { index, action ->
            if (hasPrecedingActions || index > 0) {
                addDivider(parent)
            }
            parent.addView(createCustomActionRow(action))
        }
    }

    private fun createCustomActionRow(action: ChatSettingCustomAction): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(action.title)
            setButtonStyle(
                when (action.style) {
                    ChatSettingActionStyle.LINK -> SettingRowButton.Style.LINK
                    ChatSettingActionStyle.DANGER -> SettingRowButton.Style.DANGER
                    ChatSettingActionStyle.NORMAL -> SettingRowButton.Style.NORMAL
                }
            )
            setOnClickListener { action.onClick(context) }
        }
    }

    private fun createSendMessageButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_send_messages))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener { onSendMessageClick?.invoke() }
        }
    }

    private fun createVoiceCallButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_voice_call))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener {
                onVoiceCallClick?.invoke() ?: currentUserID?.let { userID ->
                    AtomicCallEventPublisher.publishStartCall(
                        participantIds = listOf(userID),
                        mediaType = AtomicCallEventPublisher.MEDIA_TYPE_AUDIO
                    )
                }
            }
        }
    }

    private fun createVideoCallButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_video_call))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener {
                onVideoCallClick?.invoke() ?: currentUserID?.let { userID ->
                    AtomicCallEventPublisher.publishStartCall(
                        participantIds = listOf(userID),
                        mediaType = AtomicCallEventPublisher.MEDIA_TYPE_VIDEO
                    )
                }
            }
        }
    }

    private fun createClearHistoryButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_clear_history_messages))
            setDangerStyle(true)
            setOnClickListener { showClearHistoryConfirmation(this) }
        }
    }

    private fun showClearHistoryConfirmation(sourceView: View) {
        AtomicAlertDialog(context).apply {
            init {
                title = context.getString(R.string.chat_setting_clear_history_confirmation_title)
                content = context.getString(R.string.chat_setting_clear_history_confirmation_message)
                autoDismiss = true
                confirmButton(
                    context.getString(R.string.chat_setting_clear_history_confirm),
                    type = AtomicAlertDialog.TextColorPreset.RED,
                ) { _ ->
                    sourceView.isEnabled = false
                    sourceView.alpha = 0.6f
                    viewModel?.clearChatHistory(
                        onSuccess = {
                            sourceView.isEnabled = true
                            sourceView.alpha = 1f
                            AtomicToast.show(
                                context,
                                context.getString(R.string.chat_setting_clear_history_success),
                                style = AtomicToast.Style.SUCCESS,
                            )
                        },
                        onFailure = { _, _ ->
                            sourceView.isEnabled = true
                            sourceView.alpha = 1f
                            AtomicToast.show(
                                context,
                                context.getString(R.string.chat_setting_clear_history_failed),
                                style = AtomicToast.Style.ERROR,
                            )
                        },
                    )
                }
                cancelButton(context.getString(R.string.uikit_cancel))
            }
            show()
        }
    }

    private fun createDeleteFriendButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_delete_friend))
            setButtonStyle(SettingRowButton.Style.DANGER)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_delete_friend_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel?.deleteFriend(
                                onSuccess = { onContactDeleted?.invoke() },
                                onFailure = { _, desc ->
                                    AtomicToast.show(context, desc, style = AtomicToast.Style.ERROR)
                                }
                            )
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun addSpacer(parent: LinearLayout) {
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(10f, resources.displayMetrics).toInt()
            )
        }
        spacers.add(spacer)
        parent.addView(spacer)
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, resources.displayMetrics).toInt()
            )
            val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
            setBackgroundColor(colors.strokeColorPrimary)
        }
        dividers.add(divider)
        parent.addView(divider)
    }

    private fun bindViewModel() {
        val vm = viewModel ?: return
        if (viewScope != null) return
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }

        scope.launch {
            combine(vm.nickname, vm.avatar, vm.friendRemark, vm.aboutMe) { nickname, avatar, remark, signature ->
                arrayOf(nickname, avatar, remark, signature)
            }.collectLatest { values ->
                val nickname = values[0]
                val avatar = values[1]
                val remark = values[2]
                val signature = values[3]
                val displayName = nickname.ifEmpty { vm.userID }
                nicknameTextView.text = displayName
                idTextView.text = "${context.getString(R.string.chat_setting_user_id)}: ${vm.userID}"
                avatarView.setContent(
                    Avatar.AvatarContent.Image(url = avatar, fallbackName = displayName)
                )
                if (signature.isNotEmpty()) {
                    signatureTextView.visibility = View.VISIBLE
                    signatureTextView.text = context.getString(R.string.chat_setting_signature_prefix) + signature
                } else {
                    signatureTextView.visibility = View.GONE
                }
                remarkRow?.setTitle(context.getString(R.string.chat_setting_remark_name))
                remarkRow?.setValue(remark.ifEmpty { displayName })
            }
        }
        scope.launch {
            vm.isNotDisturb.collectLatest { doNotDisturbRow.setChecked(it) }
        }
        scope.launch {
            vm.isPinned.collectLatest { pinRow.setChecked(it) }
        }
        if (showChatBackground) {
            scope.launch {
                vm.chatBackgroundImageUri.collectLatest { imageUri ->
                    updateChatBackgroundRow(imageUri)
                }
            }
        }
        scope.launch {
            vm.isInBlacklist.collectLatest { blacklistRow.setChecked(it) }
        }
    }

    private fun updateChatBackgroundRow(imageUri: String?) {
        chatBackgroundRow.setTitle(context.getString(R.string.chat_setting_chat_background))
        chatBackgroundRow.setValue(
            if (imageUri.isNullOrBlank()) {
                context.getString(R.string.chat_setting_chat_background_default)
            } else {
                context.getString(R.string.chat_setting_chat_background_custom)
            }
        )
    }

    private fun showChatBackgroundPicker(viewModel: C2CChatSettingViewModel) {
        ChatBackgroundPickerDialog(
            context = context,
            selectedImageUri = viewModel.chatBackgroundImageUri.value,
            onBackgroundSelected = { imageUri ->
                if (imageUri.isNullOrBlank()) {
                    viewModel.clearChatBackground()
                } else {
                    viewModel.setChatBackground(imageUri)
                }
            }
        ).show()
    }

    private fun cleanupBinding() {
        viewScope?.cancel()
        viewScope = null
    }

    fun currentContactDisplayName(): String {
        val vm = viewModel ?: return currentUserID.orEmpty()
        return vm.friendRemark.value.ifBlank { vm.nickname.value }.ifBlank { vm.userID }
    }

    fun currentContactAvatarURL(): String? = viewModel?.avatar?.value?.takeIf { it.isNotBlank() }

    private fun applyThemeColors(colors: ColorTokens) {
        val isInsetGrouped = layoutStyle == LayoutStyle.XINGDUN_INSET_GROUPED
        val pageBackground = if (isInsetGrouped) colors.bgColorDefault else colors.bgColorTopBar
        setBackgroundColor(pageBackground)
        scrollView.setBackgroundColor(pageBackground)
        contentLayout.setBackgroundColor(pageBackground)
        if (!isInsetGrouped) userInfoLayout.setBackgroundColor(colors.bgColorOperate)
        nicknameTextView.setTextColor(colors.textColorPrimary)
        idTextView.setTextColor(colors.textColorTertiary)
        signatureTextView.setTextColor(colors.textColorTertiary)
        sectionCards.forEach { it.background = roundedBackground(colors.bgColorOperate, 18f) }
        sectionTitles.forEach { it.setTextColor(colors.textColorTertiary) }
        spacers.forEach { it.setBackgroundColor(colors.bgColorTopBar) }
        dividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewModel == null) return
        bindViewModel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupBinding()
    }
}
