package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactListSearchBarView
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat.ContactSelectionStateMerger
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.ui.UserPickerView
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContactPickerDialog(
    private val context: Context,
    private val title: String,
    contacts: List<ContactInfo>,
    private val maxSelection: Int = 100,
    preSelectedUserIds: List<String> = emptyList(),
    private val allowEmptyConfirm: Boolean = false,
    private val onConfirm: (List<ContactInfo>) -> Unit
) {

    private val conversationListStore = ConversationListStore.create()
    private val allContacts = contacts.map { contact ->
        UserPickerData(
            key = contact.userID,
            label = contact.displayName,
            avatarUrl = contact.avatarURL,
            extraData = contact
        )
    }
    private var selectedContacts = allContacts.filter { it.key in preSelectedUserIds }
    private var recentUserIds = emptyList<String>()
    private var showsRecentContacts = true
    private var searchQuery = ""

    private var dialog: Dialog? = null
    private var dialogScope: CoroutineScope? = null
    private var pickerView: UserPickerView? = null
    private var navBar: DialogNavBar? = null
    private var recentTabView: TextView? = null
    private var contactsTabView: TextView? = null

    fun show() {
        dismiss()
        val colors = colors()
        val currentDialog = Dialog(context, android.R.style.Theme_NoTitleBar).apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
            }
        }
        dialog = currentDialog

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        navBar = DialogNavBar.create(
            context,
            DialogNavBar.Config(
                mode = DialogNavBar.Mode.CancelTitleConfirm,
                title = title,
                colors = colors,
                onLeadingClick = ::dismiss,
                onConfirmClick = {
                    if (selectedContacts.isNotEmpty() || allowEmptyConfirm) {
                        onConfirm(selectedContacts.map { it.extraData })
                        dismiss()
                    }
                }
            )
        ).also(rootLayout::addView)

        rootLayout.addView(ContactListSearchBarView(context).apply {
            onQueryChange = { query ->
                searchQuery = query
                applyVisibleContacts()
            }
        })
        rootLayout.addView(createTabs(colors))

        pickerView = UserPickerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setMaxCount(maxSelection)
            setShowIdentifierSubtitle(true)
            setAvatarShape(Avatar.AvatarShape.RoundRectangle)
            setOnSelectedChangedListener<ContactInfo> { visibleSelection ->
                selectedContacts = ContactSelectionStateMerger.merge(
                    currentSelected = selectedContacts,
                    visibleItems = visibleContacts(),
                    visibleSelectedItems = visibleSelection,
                    selectedKeySelector = { it.key },
                    visibleKeySelector = { it.key },
                    visibleToSelectedMapper = { it }
                )
                updateConfirmState(colors)
            }
        }.also(rootLayout::addView)

        currentDialog.setOnDismissListener {
            dialogScope?.cancel()
            dialogScope = null
            if (dialog === currentDialog) dialog = null
        }
        currentDialog.setContentView(rootLayout)
        currentDialog.show()
        updateTabs(colors)
        applyVisibleContacts()
        updateConfirmState(colors)
        observeRecentConversations()
    }

    fun dismiss() {
        val current = dialog
        dialog = null
        dialogScope?.cancel()
        dialogScope = null
        if (current?.isShowing == true) current.dismiss()
    }

    private fun observeRecentConversations() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialogScope = scope
        scope.launch {
            conversationListStore.state.conversationList.collectLatest { conversations ->
                recentUserIds = conversations.mapNotNull { conversation ->
                    ConversationIDUtil.userIdOrNull(conversation.conversationID)
                }
                applyVisibleContacts()
            }
        }
        conversationListStore.loadConversations(
            ConversationLoadOption(),
            object : CompletionHandler {
                override fun onSuccess() = Unit
                override fun onFailure(code: Int, desc: String) = Unit
            }
        )
    }

    private fun createTabs(colors: ColorTokens): View {
        val dm = context.resources.displayMetrics
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                dp2px(16f, dm).toInt(),
                0,
                dp2px(16f, dm).toInt(),
                dp2px(10f, dm).toInt()
            )
            setBackgroundColor(colors.bgColorOperate)
            recentTabView = selectionTab(R.string.contact_list_recent_conversations) {
                showsRecentContacts = true
                updateTabs(colors)
                applyVisibleContacts()
            }
            contactsTabView = selectionTab(R.string.contact_list_contacts) {
                showsRecentContacts = false
                updateTabs(colors)
                applyVisibleContacts()
            }
            addView(recentTabView, LinearLayout.LayoutParams(0, dp2px(38f, dm).toInt(), 1f))
            addView(contactsTabView, LinearLayout.LayoutParams(0, dp2px(38f, dm).toInt(), 1f))
        }
    }

    private fun selectionTab(textRes: Int, onClick: () -> Unit): TextView = TextView(context).apply {
        setText(textRes)
        gravity = Gravity.CENTER
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        setOnClickListener { onClick() }
    }

    private fun updateTabs(colors: ColorTokens) {
        val radius = dp2px(19f, context.resources.displayMetrics)
        fun style(view: TextView?, selected: Boolean) {
            view ?: return
            view.setTextColor(if (selected) colors.textColorPrimary else colors.textColorSecondary)
            view.background = GradientDrawable().apply {
                setColor(if (selected) colors.bgColorTopBar else colors.bgColorInput)
                cornerRadius = radius
            }
        }
        style(recentTabView, showsRecentContacts)
        style(contactsTabView, !showsRecentContacts)
    }

    private fun visibleContacts(): List<UserPickerData<ContactInfo>> {
        val source = if (showsRecentContacts) {
            ContactPickerSourcePolicy.recent(allContacts, recentUserIds)
        } else {
            allContacts
        }
        return ContactPickerSourcePolicy.filter(source, searchQuery) { contact, query ->
            contact.extraData.matchesSearchQuery(query)
        }
    }

    private fun applyVisibleContacts() {
        pickerView?.apply {
            setAlphabeticalGroupingEnabled(!showsRecentContacts)
            setDataSource(visibleContacts())
            setDefaultSelectedItems(selectedContacts.map { it.key })
        }
    }

    private fun updateConfirmState(colors: ColorTokens) {
        navBar?.setConfirmEnabled(selectedContacts.isNotEmpty() || allowEmptyConfirm, colors)
    }

    private fun colors(): ColorTokens = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
}
