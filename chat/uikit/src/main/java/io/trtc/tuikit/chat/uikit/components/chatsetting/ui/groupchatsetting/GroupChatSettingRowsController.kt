package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowNavigate
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowToggle
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.TextInputDialog
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole

internal class GroupChatSettingRowsController(
    private val context: Context,
    private val viewModelProvider: () -> GroupChatSettingViewModel?,
    private val createSectionContainer: () -> LinearLayout,
    private val rebuildSection: (LinearLayout, List<View>) -> Unit,
    private val onOpenGroupManagement: (GroupChatSettingViewModel) -> Unit,
    private val onOpenGroupAnnouncement: (() -> Unit)?,
    private val onOpenGroupQRCode: (() -> Unit)?,
    private val onOpenGroupNickname: (() -> Unit)?,
    private val autoDeleteTitle: String?,
    private val onOpenAutoDelete: (() -> Unit)?,
    private val isAssignedCustomerServiceProvider: () -> Boolean,
    private val onShowJoinMethod: (GroupChatSettingViewModel) -> Unit,
    private val onShowInviteMethod: (GroupChatSettingViewModel) -> Unit,
    private val onShowChatBackgroundPicker: (GroupChatSettingViewModel) -> Unit
) {
    lateinit var settingsSection: LinearLayout
        private set
    lateinit var aliasSection: LinearLayout
        private set
    lateinit var switchSection: LinearLayout
        private set
    lateinit var backgroundSection: LinearLayout
        private set

    private lateinit var groupNoticeRow: SettingRowNavigate
    private lateinit var groupQRCodeRow: SettingRowNavigate
    private lateinit var groupTypeRow: SettingRowNavigate
    private lateinit var joinMethodRow: SettingRowNavigate
    private lateinit var inviteMethodRow: SettingRowNavigate
    private lateinit var myAliasRow: SettingRowNavigate
    private lateinit var groupManageRow: SettingRowNavigate
    private lateinit var autoDeleteRow: SettingRowNavigate
    private lateinit var chatBackgroundRow: SettingRowNavigate
    private lateinit var doNotDisturbRow: SettingRowToggle
    private lateinit var pinRow: SettingRowToggle

    fun buildSections(): List<LinearLayout> {
        settingsSection = createSectionContainer()
        groupNoticeRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_notice))
        }
        groupQRCodeRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_qr_code))
            setShowArrow(true)
            visibility = if (onOpenGroupQRCode == null) View.GONE else View.VISIBLE
            setOnClickListener { onOpenGroupQRCode?.invoke() }
        }
        groupManageRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_management))
            setShowArrow(true)
        }
        autoDeleteRow = SettingRowNavigate(context).apply {
            setTitle(autoDeleteTitle.orEmpty())
            setShowArrow(true)
        }
        groupTypeRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_type))
            setShowArrow(false)
        }
        joinMethodRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_join_group_method))
        }
        inviteMethodRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_invited_method))
        }
        rebuildSettingsSection()

        aliasSection = createSectionContainer()
        myAliasRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_my_alias_in_group))
        }
        rebuildSection(aliasSection, listOf(myAliasRow))

        switchSection = createSectionContainer()
        doNotDisturbRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_do_not_disturb))
            onToggleChanged = { checked -> viewModelProvider()?.setDoNotDisturb(checked) }
        }
        pinRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_pin))
            onToggleChanged = { checked -> viewModelProvider()?.setPinChat(checked) }
        }
        rebuildSection(switchSection, listOf(doNotDisturbRow, pinRow))

        backgroundSection = createSectionContainer()
        chatBackgroundRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_chat_background))
            setShowArrow(true)
            setOnClickListener {
                viewModelProvider()?.let(onShowChatBackgroundPicker)
            }
        }
        rebuildSection(backgroundSection, listOf(chatBackgroundRow))

        return listOf(settingsSection, aliasSection, switchSection, backgroundSection)
    }

    fun refresh(state: GroupChatSettingUiState, viewModel: GroupChatSettingViewModel) {
        val permissions = state.permissions

        groupNoticeRow.setValue(
            state.notification.ifEmpty { context.getString(R.string.chat_setting_no_group_notice) }
        )
        if (onOpenGroupAnnouncement != null) {
            groupNoticeRow.setShowArrow(true)
            groupNoticeRow.setCustomAccessory(null)
            groupNoticeRow.isClickable = true
            groupNoticeRow.setOnClickListener { onOpenGroupAnnouncement.invoke() }
        } else {
            groupNoticeRow.setShowArrow(false)
            groupNoticeRow.setCustomAccessory(
                if (permissions.canEditGroupNotice) R.drawable.chat_setting_group_name_edit_icon else null
            )
            if (permissions.canEditGroupNotice) {
                groupNoticeRow.isClickable = true
                groupNoticeRow.setOnClickListener {
                    TextInputDialog(
                        context = context,
                        title = context.getString(R.string.chat_setting_group_notice),
                        initialText = state.notification,
                        maxLength = 300,
                        multiline = true,
                        onConfirm = { value ->
                            if (value != state.notification) {
                                viewModel.setGroupNotice(value)
                            }
                        }
                    ).show()
                }
            } else {
                groupNoticeRow.isClickable = false
                groupNoticeRow.setOnClickListener(null)
            }
        }

        groupQRCodeRow.visibility = if (onOpenGroupQRCode == null) View.GONE else View.VISIBLE
        groupQRCodeRow.setShowArrow(true)
        groupQRCodeRow.setCustomAccessory(null)
        groupQRCodeRow.isClickable = onOpenGroupQRCode != null
        groupQRCodeRow.setOnClickListener { onOpenGroupQRCode?.invoke() }

        groupManageRow.visibility = if (permissions.canOpenGroupManagement) View.VISIBLE else View.GONE
        if (permissions.canOpenGroupManagement) {
            groupManageRow.setShowArrow(true)
            groupManageRow.setCustomAccessory(null)
            groupManageRow.isClickable = true
            groupManageRow.setOnClickListener {
                onOpenGroupManagement(viewModel)
            }
        } else {
            groupManageRow.isClickable = false
            groupManageRow.setOnClickListener(null)
        }

        val canManageAutoDelete = state.selfRole == GroupMemberRole.OWNER ||
            state.selfRole == GroupMemberRole.ADMIN ||
            isAssignedCustomerServiceProvider()
        autoDeleteRow.visibility = if (canManageAutoDelete && onOpenAutoDelete != null && !autoDeleteTitle.isNullOrBlank()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        autoDeleteRow.isClickable = autoDeleteRow.visibility == View.VISIBLE
        autoDeleteRow.setOnClickListener { onOpenAutoDelete?.invoke() }

        groupTypeRow.setValue(
            context.getString(GroupChatSettingTextMapper.groupTypeTextRes(state.groupType))
        )
        groupTypeRow.setShowArrow(false)
        groupTypeRow.setCustomAccessory(null)
        groupTypeRow.isClickable = false

        joinMethodRow.setValue(
            GroupChatSettingTextMapper.joinOptionTextRes(state.joinOption)?.let { context.getString(it) } ?: ""
        )
        joinMethodRow.setCustomAccessory(null)
        joinMethodRow.setShowArrow(permissions.canEditJoinOption)
        if (permissions.canEditJoinOption) {
            joinMethodRow.isClickable = true
            joinMethodRow.setOnClickListener { onShowJoinMethod(viewModel) }
        } else {
            joinMethodRow.isClickable = false
            joinMethodRow.setOnClickListener(null)
        }

        inviteMethodRow.setValue(
            GroupChatSettingTextMapper.inviteOptionTextRes(state.inviteOption)?.let { context.getString(it) } ?: ""
        )
        inviteMethodRow.setCustomAccessory(null)
        inviteMethodRow.setShowArrow(permissions.canEditInviteOption)
        if (permissions.canEditInviteOption) {
            inviteMethodRow.isClickable = true
            inviteMethodRow.setOnClickListener { onShowInviteMethod(viewModel) }
        } else {
            inviteMethodRow.isClickable = false
            inviteMethodRow.setOnClickListener(null)
        }

        myAliasRow.setValue(
            state.nameCard?.takeIf { it.isNotBlank() } ?: context.getString(R.string.chat_setting_not_set)
        )
        myAliasRow.setShowArrow(onOpenGroupNickname != null && permissions.canEditSelfNameCard)
        myAliasRow.setCustomAccessory(if (onOpenGroupNickname == null && permissions.canEditSelfNameCard) {
            R.drawable.chat_setting_group_name_edit_icon
        } else {
            null
        })
        if (permissions.canEditSelfNameCard) {
            myAliasRow.isClickable = true
            myAliasRow.setOnClickListener {
                val callback = onOpenGroupNickname
                if (callback != null) {
                    callback()
                } else {
                    TextInputDialog(
                        context = context,
                        title = context.getString(R.string.chat_setting_modify_group_name_card),
                        initialText = viewModel.selfNameCard.value ?: "",
                        onConfirm = { value -> viewModel.setGroupNickname(value) }
                    ).show()
                }
            }
        } else {
            myAliasRow.isClickable = false
            myAliasRow.setOnClickListener(null)
        }

        doNotDisturbRow.visibility = if (permissions.canToggleDoNotDisturb) View.VISIBLE else View.GONE
        doNotDisturbRow.setChecked(state.isNotDisturb)
        pinRow.visibility = if (permissions.canTogglePinned) View.VISIBLE else View.GONE
        pinRow.setChecked(state.isPinned)

        chatBackgroundRow.setTitle(context.getString(R.string.chat_setting_chat_background))
        chatBackgroundRow.setValue(
            if (state.hasCustomChatBackground) {
                context.getString(R.string.chat_setting_chat_background_custom)
            } else {
                context.getString(R.string.chat_setting_chat_background_default)
            }
        )
        chatBackgroundRow.setShowArrow(true)
        chatBackgroundRow.setOnClickListener { onShowChatBackgroundPicker(viewModel) }

        rebuildSettingsSection()
        rebuildSection(aliasSection, listOf(myAliasRow))
        rebuildSection(switchSection, listOf(doNotDisturbRow, pinRow))
        rebuildSection(backgroundSection, listOf(chatBackgroundRow))
    }

    fun applyThemeColors(colors: ColorTokens) {
        if (::settingsSection.isInitialized) {
            settingsSection.setBackgroundColor(colors.bgColorOperate)
        }
        if (::aliasSection.isInitialized) {
            aliasSection.setBackgroundColor(colors.bgColorOperate)
        }
        if (::switchSection.isInitialized) {
            switchSection.setBackgroundColor(colors.bgColorOperate)
        }
        if (::backgroundSection.isInitialized) {
            backgroundSection.setBackgroundColor(colors.bgColorOperate)
        }
    }

    private fun rebuildSettingsSection() {
        rebuildSection(
            settingsSection,
            listOf(groupNoticeRow, groupQRCodeRow, groupManageRow, autoDeleteRow, groupTypeRow, joinMethodRow, inviteMethodRow)
        )
    }
}
