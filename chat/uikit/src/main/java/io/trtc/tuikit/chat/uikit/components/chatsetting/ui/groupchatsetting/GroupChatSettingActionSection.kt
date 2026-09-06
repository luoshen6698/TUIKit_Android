package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionStyle
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingCustomAction
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingScene
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowNavigate
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole

internal class GroupChatSettingActionSection(
    private val context: Context,
    private val createDivider: () -> View,
    private val canPerformAction: (GroupType, GroupMemberRole, GroupPermission) -> Boolean,
    private val onGroupDeletedProvider: () -> (() -> Unit)?,
    private val displayGroupIDProvider: () -> String?,
) {
    fun rebuild(
        safetySection: LinearLayout,
        safetyTitle: View,
        safetySpacer: View,
        dangerSection: LinearLayout,
        dangerSpacer: View,
        viewModel: GroupChatSettingViewModel,
        groupType: GroupType,
        selfRole: GroupMemberRole
    ) {
        val safetyRows = customActions(viewModel).map { createCustomActionRow(it) }.toMutableList()
        if (canPerformAction(groupType, selfRole, GroupPermission.CLEAR_HISTORY_MESSAGES)) {
            safetyRows.add(createClearHistoryRow(viewModel))
        }

        val dangerRows = mutableListOf<SettingRowNavigate>()
        if (canPerformAction(groupType, selfRole, GroupPermission.DELETE_AND_QUIT)) {
            dangerRows.add(createDeleteAndQuitRow(viewModel))
        }
        if (canPerformAction(groupType, selfRole, GroupPermission.DISMISS_GROUP)) {
            dangerRows.add(createDismissGroupRow(viewModel))
        }

        rebuildSection(safetySection, safetyRows)
        rebuildSection(dangerSection, dangerRows)

        val hasSafetyActions = safetyRows.isNotEmpty()
        safetyTitle.visibility = if (hasSafetyActions) View.VISIBLE else View.GONE
        safetySpacer.visibility = if (hasSafetyActions) View.VISIBLE else View.GONE
        dangerSpacer.visibility = if (dangerRows.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun customActions(viewModel: GroupChatSettingViewModel): List<ChatSettingCustomAction> {
        val provider = ChatSettingActionConfig.customActionProvider ?: return emptyList()
        return provider.getActions(
            ChatSettingActionContext(
                context = context,
                scene = ChatSettingScene.GROUP,
                userID = null,
                groupID = viewModel.groupID,
                displayName = viewModel.groupName.value.takeIf(String::isNotBlank),
                displayID = displayGroupIDProvider(),
            )
        )
    }

    private fun createCustomActionRow(action: ChatSettingCustomAction): SettingRowNavigate {
        return SettingRowNavigate(context).apply {
            setTitle(action.title)
            setShowArrow(action.style != ChatSettingActionStyle.DANGER)
            setDangerStyle(action.style == ChatSettingActionStyle.DANGER)
            setPrimaryTitleStyle(action.style != ChatSettingActionStyle.DANGER)
            setOnClickListener { action.onClick(context) }
        }
    }

    private fun createClearHistoryRow(viewModel: GroupChatSettingViewModel): SettingRowNavigate {
        return SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_clear_history_messages))
            setShowArrow(false)
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        title = context.getString(R.string.chat_setting_clear_history_confirmation_title)
                        content = context.getString(R.string.chat_setting_clear_history_confirmation_message)
                        autoDismiss = true
                        confirmButton(
                            context.getString(R.string.chat_setting_clear_history_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED,
                        ) { _ ->
                            isEnabled = false
                            alpha = 0.6f
                            viewModel.clearChatHistory(
                                onSuccess = {
                                    isEnabled = true
                                    alpha = 1f
                                    AtomicToast.show(
                                        context,
                                        context.getString(R.string.chat_setting_clear_history_success),
                                        style = AtomicToast.Style.SUCCESS,
                                    )
                                },
                                onFailure = { _, _ ->
                                    isEnabled = true
                                    alpha = 1f
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
        }
    }

    private fun createDeleteAndQuitRow(viewModel: GroupChatSettingViewModel): SettingRowNavigate {
        return SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_delete_and_quit))
            setShowArrow(false)
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_delete_and_quit_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel.quitGroup(
                                onSuccess = { onGroupDeletedProvider()?.invoke() },
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

    private fun createDismissGroupRow(viewModel: GroupChatSettingViewModel): SettingRowNavigate {
        return SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_dismiss_group))
            setShowArrow(false)
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_dismiss_group_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel.dismissGroup(
                                onSuccess = { onGroupDeletedProvider()?.invoke() },
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

    private fun rebuildSection(section: LinearLayout, rows: List<View>) {
        section.removeAllViews()
        rows.forEachIndexed { index, row ->
            section.addView(row)
            if (index != rows.lastIndex) {
                section.addView(createDivider())
            }
        }
        section.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }
}
