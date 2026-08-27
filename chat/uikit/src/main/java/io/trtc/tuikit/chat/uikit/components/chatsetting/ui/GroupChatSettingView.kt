package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermissionManager
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.GroupChatSettingActionSection
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.GroupChatSettingHeaderSection
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.GroupChatSettingRowsController
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting.createGroupChatSettingUiState
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModelFactory
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.getGroupAvatarUrls
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupInviteOption
import io.trtc.tuikit.atomicxcore.api.group.GroupJoinOption
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GroupChatSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onSendMessageClick: (() -> Unit)? = null
    private var onGroupMemberClick: ((GroupMember) -> Unit)? = null
    private var onGroupMemberListClick: (() -> Unit)? = null
    private var onGroupInfoClick: (() -> Unit)? = null
    private var onGroupAnnouncementClick: (() -> Unit)? = null
    private var onGroupQRCodeClick: (() -> Unit)? = null
    private var onGroupManagementClick: (() -> Unit)? = null
    private var onTransferOwnerClick: (() -> Unit)? = null
    private var onGroupNicknameClick: (() -> Unit)? = null
    private var onGroupDeleted: (() -> Unit)? = null

    private var viewModel: GroupChatSettingViewModel? = null
    private var viewScope: CoroutineScope? = null

    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout
    private lateinit var headerSection: GroupChatSettingHeaderSection
    private lateinit var rowsController: GroupChatSettingRowsController
    private lateinit var actionSectionController: GroupChatSettingActionSection
    private lateinit var actionSection: LinearLayout
    private lateinit var actionSpacer: View
    private val themeTaggedViews = mutableSetOf<View>()

    private lateinit var memberPreviewSection: GroupMemberPreviewSection

    private var currentGroupID: String? = null
    private var isUiBuilt = false

    fun setup(
        groupID: String,
        onSendMessageClick: (() -> Unit)? = null,
        onGroupMemberClick: ((GroupMember) -> Unit)? = null,
        onGroupMemberListClick: (() -> Unit)? = null,
        onGroupInfoClick: (() -> Unit)? = null,
        onGroupAnnouncementClick: (() -> Unit)? = null,
        onGroupQRCodeClick: (() -> Unit)? = null,
        onGroupManagementClick: (() -> Unit)? = null,
        onTransferOwnerClick: (() -> Unit)? = null,
        onGroupNicknameClick: (() -> Unit)? = null,
        onGroupDeleted: (() -> Unit)? = null
    ) {
        this.onSendMessageClick = onSendMessageClick
        this.onGroupMemberClick = onGroupMemberClick
        this.onGroupMemberListClick = onGroupMemberListClick
        this.onGroupInfoClick = onGroupInfoClick
        this.onGroupAnnouncementClick = onGroupAnnouncementClick
        this.onGroupQRCodeClick = onGroupQRCodeClick
        this.onGroupManagementClick = onGroupManagementClick
        this.onTransferOwnerClick = onTransferOwnerClick
        this.onGroupNicknameClick = onGroupNicknameClick
        this.onGroupDeleted = onGroupDeleted

        val owner = context.findViewModelStoreOwner() ?: return

        cleanupBinding()
        currentGroupID = groupID
        val viewModelKey = "${GroupChatSettingViewModel::class.java.name}:$groupID"
        viewModel = ViewModelProvider(owner, GroupChatSettingViewModelFactory(groupID, context))
            .get(viewModelKey, GroupChatSettingViewModel::class.java)

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

        scrollView = ScrollView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            setBackgroundColor(getColors().bgColorTopBar)
        }

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(getColors().bgColorTopBar)
        }

        headerSection = GroupChatSettingHeaderSection(context)
        contentLayout.addView(headerSection.rootView, cardLayoutParams())
        contentLayout.addView(createSpacer(12f))

        memberPreviewSection = GroupMemberPreviewSection(context).apply {
            onHeaderClick = { onGroupMemberListClick?.invoke() ?: showMemberList() }
            onMemberClick = { member -> onGroupMemberClick?.invoke(member) }
            onAddClick = { showAddMemberDialog() }
        }
        contentLayout.addView(memberPreviewSection, cardLayoutParams())
        contentLayout.addView(createSpacer(12f))

        rowsController = GroupChatSettingRowsController(
            context = context,
            viewModelProvider = { viewModel },
            createSectionContainer = ::createSectionContainer,
            rebuildSection = ::rebuildSection,
            onOpenGroupManagement = { viewModel ->
                onGroupManagementClick?.invoke() ?: showGroupManagement(viewModel)
            },
            onOpenGroupAnnouncement = onGroupAnnouncementClick,
            onOpenGroupQRCode = onGroupQRCodeClick,
            onOpenGroupNickname = onGroupNicknameClick,
            onShowJoinMethod = ::showJoinMethodActionSheet,
            onShowInviteMethod = ::showInviteMethodActionSheet,
            onShowChatBackgroundPicker = ::showChatBackgroundPicker
        )
        val rowSections = rowsController.buildSections()
        rowSections.forEachIndexed { index, section ->
            contentLayout.addView(section, cardLayoutParams())
            if (index != rowSections.lastIndex) {
                contentLayout.addView(createSpacer(12f))
            }
        }

        actionSpacer = createSpacer(10f)
        contentLayout.addView(actionSpacer)

        actionSection = createSectionContainer()
        actionSectionController = GroupChatSettingActionSection(
            context = context,
            createDivider = ::createDivider,
            createSpacer = { createSpacer(10f) },
            canPerformAction = ::canPerformAction,
            onGroupDeletedProvider = { onGroupDeleted },
            onOpenTransferOwner = {
                val callback = onTransferOwnerClick
                if (callback == null) {
                    false
                } else {
                    callback()
                    true
                }
            }
        )
        contentLayout.addView(actionSection, cardLayoutParams())

        scrollView.addView(contentLayout)
        addView(scrollView)
    }

    fun refreshSelfNameCard() {
        viewModel?.refreshSelfNameCard()
    }

    private fun createSectionContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            applyCardBackground(getColors())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
    }

    private fun View.applyCardBackground(colors: ColorTokens) {
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(colors.bgColorOperate)
            cornerRadius = dp2px(18f, resources.displayMetrics)
        }
        clipToOutline = true
    }

    private fun cardLayoutParams(): LinearLayout.LayoutParams {
        val margin = dp2px(16f, resources.displayMetrics).toInt()
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = margin
            marginEnd = margin
        }
    }

    private fun createSpacer(heightDp: Float): View {
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(heightDp, resources.displayMetrics).toInt()
            )
            tag = TAG_SPACER
            setBackgroundColor(getColors().bgColorTopBar)
        }
        themeTaggedViews.add(spacer)
        return spacer
    }

    private fun createDivider(): View {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, resources.displayMetrics).toInt().coerceAtLeast(1)
            )
            tag = TAG_DIVIDER
            setBackgroundColor(getColors().strokeColorPrimary)
        }
        themeTaggedViews.add(divider)
        return divider
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private fun rebuildSection(section: LinearLayout, rows: List<View>) {
        val visibleRows = rows.filter { it.visibility != View.GONE }
        if (!isSectionLayoutUpToDate(section, visibleRows)) {
            section.removeAllViews()
            visibleRows.forEachIndexed { index, row ->
                section.addView(row)
                if (index != visibleRows.lastIndex) {
                    section.addView(createDivider())
                }
            }
        }
        section.visibility = if (visibleRows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun isSectionLayoutUpToDate(section: LinearLayout, visibleRows: List<View>): Boolean {
        val expectedChildCount = if (visibleRows.isEmpty()) 0 else visibleRows.size * 2 - 1
        if (section.childCount != expectedChildCount) return false
        visibleRows.forEachIndexed { index, row ->
            if (section.getChildAt(index * 2) !== row) return false
        }
        return true
    }

    private fun bindViewModel() {
        val vm = viewModel ?: return
        if (viewScope != null) return
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
                refreshViewState()
            }
        }

        scope.launch {
            combine(
                vm.groupName,
                vm.avatarURL,
                vm.notification,
                vm.groupType,
                vm.selfRole,
                vm.joinGroupApprovalType,
                vm.inviteToGroupApprovalType,
                vm.selfNameCard,
                vm.memberCount,
                vm.memberList,
                vm.isNotDisturb,
                vm.isPinned,
                vm.chatBackgroundImageUri
            ) { _ ->
                Unit
            }.collectLatest {
                refreshViewState()
            }
        }
    }

    private fun cleanupBinding() {
        viewScope?.cancel()
        viewScope = null
    }

    private fun refreshViewState() {
        val vm = viewModel ?: return
        val state = createGroupChatSettingUiState(
            groupID = vm.groupID,
            groupName = vm.groupName.value,
            avatarURL = vm.avatarURL.value,
            notification = vm.notification.value,
            groupType = vm.groupType.value,
            selfRole = vm.selfRole.value,
            joinOption = vm.joinGroupApprovalType.value,
            inviteOption = vm.inviteToGroupApprovalType.value,
            nameCard = vm.selfNameCard.value,
            memberCount = vm.memberCount.value,
            groupMembers = vm.memberList.value,
            isNotDisturb = vm.isNotDisturb.value,
            isPinned = vm.isPinned.value,
            chatBackgroundImageUri = vm.chatBackgroundImageUri.value,
            canPerformAction = ::canPerformAction
        )
        val permissions = state.permissions

        headerSection.update(
            state = state,
            onAvatarClick = ::showGroupAvatarPicker,
            onGroupNameConfirmed = vm::setGroupName,
            onGroupIdClick = ::copyGroupID,
            onGroupInfoClick = onGroupInfoClick,
        )

        memberPreviewSection.updateContent(
            members = state.groupMembers,
            memberCount = state.memberCount,
            showAddButton = permissions.canAddMember
        )

        rowsController.refresh(state, vm)
        actionSectionController.rebuild(
            actionSection = actionSection,
            actionSpacer = actionSpacer,
            viewModel = vm,
            groupType = state.groupType,
            selfRole = state.selfRole
        )
    }

    private fun showChatBackgroundPicker(viewModel: GroupChatSettingViewModel) {
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

    private fun showGroupManagement(viewModel: GroupChatSettingViewModel) {
        GroupManagementView(
            context = context,
            groupID = viewModel.groupID,
            viewModel = viewModel
        ).show()
    }

    private fun showMemberList() {
        val vm = viewModel ?: return
        val selfRole = vm.selfRole.value
        GroupMemberListView(
            context = context,
            groupID = vm.groupID,
            currentUserRole = selfRole,
            title = context.getString(R.string.chat_setting_group_members),
            onMemberClick = { member -> onGroupMemberClick?.invoke(member) },
            onSetAsAdmin = { member ->
                vm.setMemberRole(member.userID, GroupMemberRole.ADMIN)
            },
            onRemoveAdmin = { member ->
                vm.setMemberRole(member.userID, GroupMemberRole.MEMBER)
            },
            onRemoveMember = { member ->
                vm.deleteMember(listOf(member))
            },
            onViewInfo = { member -> onGroupMemberClick?.invoke(member) }
        ).show()
    }

    private fun showGroupAvatarPicker() {
        val vm = viewModel ?: return
        AvatarPickerDialog(
            context = context,
            title = context.getString(R.string.chat_setting_select_avatar),
            imageUrls = getGroupAvatarUrls(),
            onImageSelected = { _, url ->
                vm.setGroupAvatar(url)
            }
        ).show()
    }

    private fun showAddMemberDialog() {
        val vm = viewModel ?: return
        vm.loadAllGroupMembers {
            val existingMemberIds = vm.memberList.value.map { it.userID }.toSet()
            val availableContacts = vm.friendList.value.filterNot { existingMemberIds.contains(it.userID) }
            ContactPickerDialog(
                context = context,
                title = context.getString(R.string.chat_setting_add_group_members),
                contacts = availableContacts,
                onConfirm = { selectedContacts ->
                    vm.addMember(selectedContacts.map(ContactInfo::userID))
                }
            ).show()
        }
    }

    private fun copyGroupID() {
        val vm = viewModel ?: return
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText(vm.groupID, vm.groupID))
        Toast.makeText(context, context.getString(R.string.uikit_copied), Toast.LENGTH_SHORT).show()
    }

    private fun showJoinMethodActionSheet(viewModel: GroupChatSettingViewModel) {
        ActionSheet.show(
            context = context,
            options = listOf(
                ActionItem(
                    text = context.getString(R.string.chat_setting_join_method_forbid),
                    value = GroupJoinOption.FORBID
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auth),
                    value = GroupJoinOption.AUTH
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auto),
                    value = GroupJoinOption.ANY
                )
            ),
            onActionSelected = { item ->
                val selectedOption = item.value as? GroupJoinOption ?: return@show
                viewModel.setJoinGroupApproveType(selectedOption)
            }
        )
    }

    private fun showInviteMethodActionSheet(viewModel: GroupChatSettingViewModel) {
        ActionSheet.show(
            context = context,
            options = listOf(
                ActionItem(
                    text = context.getString(R.string.chat_setting_invite_method_forbid),
                    value = GroupInviteOption.FORBID
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auth),
                    value = GroupInviteOption.AUTH
                ),
                ActionItem(
                    text = context.getString(R.string.chat_setting_method_auto),
                    value = GroupInviteOption.ANY
                )
            ),
            onActionSelected = { item ->
                val selectedOption = item.value as? GroupInviteOption ?: return@show
                viewModel.setInviteGroupApproveType(selectedOption)
            }
        )
    }

    private fun canPerformAction(
        groupType: GroupType,
        selfRole: GroupMemberRole,
        permission: GroupPermission
    ): Boolean {
        return GroupPermissionManager.canPerformAction(groupType, selfRole, permission)
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorTopBar)
        if (::scrollView.isInitialized) {
            scrollView.setBackgroundColor(colors.bgColorTopBar)
        }
        if (::contentLayout.isInitialized) {
            contentLayout.setBackgroundColor(colors.bgColorTopBar)
        }
        if (::headerSection.isInitialized) {
            headerSection.applyThemeColors(colors)
        }
        if (::rowsController.isInitialized) {
            rowsController.applyThemeColors(colors)
            rowsController.settingsSection.applyCardBackground(colors)
            rowsController.aliasSection.applyCardBackground(colors)
            rowsController.switchSection.applyCardBackground(colors)
            rowsController.backgroundSection.applyCardBackground(colors)
        }
        if (::actionSection.isInitialized) {
            actionSection.applyCardBackground(colors)
        }
        themeTaggedViews.forEach { view ->
            when (view.tag) {
                TAG_SPACER -> view.setBackgroundColor(colors.bgColorTopBar)
                TAG_DIVIDER -> view.setBackgroundColor(colors.strokeColorPrimary)
            }
        }
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

    private companion object {
        const val TAG_SPACER = "chat_setting_spacer"
        const val TAG_DIVIDER = "chat_setting_divider"
    }
}
