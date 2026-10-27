package io.trtc.tuikit.chat.demo.chat

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.C2CChatSettingView
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.GroupChatSettingView
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.common.Event
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupInfoActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunContactDetailActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupMemberPreviewNavigator
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupMemberPreviewScope
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupMemberPreviewFailure
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupMemberPolicy
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupAnnouncementActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupQRCodeActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupManagementActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupMembersActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupTransferOwnerActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupNicknameActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunAutoDeleteActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunPinnedMessagesActivity
import io.trtc.tuikit.chat.demo.search.ConversationSearchActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

open class ChatSettingActivity : BaseActivity() {

    override val requiresLogin: Boolean get() = !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private var activityScope: CoroutineScope? = null
    private val themeStore by lazy { ThemeStore.shared(this) }
    private val contactStore by lazy { ContactStore.shared }
    private var groupSettingView: GroupChatSettingView? = null
    private val memberPreviewNavigator by lazy {
        XingDunGroupMemberPreviewNavigator(
            currentScope = {
                val session = XingDunSessionManager.currentSession()
                val generation = XingDunSessionManager.currentAuthenticationGeneration()
                if (session == null || generation == null) null else XingDunGroupMemberPreviewScope(
                    session.companyCode, session.sdkAppId, session.timUserId, generation
                )
            },
            loadGroup = { groupID ->
                val session = checkNotNull(XingDunSessionManager.currentSession())
                XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session, "team/detail", mapOf("team_id" to groupID), XingDunGroupDetail::class.java
                )
            },
            loadContact = { userID ->
                suspendCancellableCoroutine { continuation ->
                    contactStore.getContactInfo(listOf(userID), object : GetContactInfoCompletionHandler {
                        override fun onSuccess(contactInfoList: List<ContactInfo>) {
                            if (continuation.isActive) continuation.resume(contactInfoList.singleOrNull())
                        }
                        override fun onFailure(code: Int, desc: String) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            },
            openProfile = { sourceGroupID, info ->
                XingDunContactDetailActivity.startFromGroup(this, sourceGroupID, info.userID, info.nickname, info.avatarURL)
            },
            showFailure = { failure ->
                Toast.makeText(this, if (failure == XingDunGroupMemberPreviewFailure.RESTRICTED)
                    R.string.xingdun_group_member_card_restricted else R.string.xingdun_contact_detail_load_failed,
                    Toast.LENGTH_LONG).show()
            },
            isActive = { !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
        )
    }

    companion object {
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_GROUP_ID = "group_id"
        private const val EXTRA_NEED_NAVIGATE_TO_CHAT = "need_navigate_to_chat"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_chat_setting_preview"

        fun startC2C(
            context: Context,
            userID: String,
            needNavigateToChat: Boolean = false
        ) {
            context.startActivity(Intent(context, ChatSettingActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userID)
                putExtra(EXTRA_NEED_NAVIGATE_TO_CHAT, needNavigateToChat)
            })
        }

        fun startGroup(
            context: Context,
            groupID: String,
            needNavigateToChat: Boolean = false
        ) {
            context.startActivity(Intent(context, ChatSettingActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupID)
                putExtra(EXTRA_NEED_NAVIGATE_TO_CHAT, needNavigateToChat)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_chat_setting)

        val userID = intent.getStringExtra(EXTRA_USER_ID)
        val groupID = intent.getStringExtra(EXTRA_GROUP_ID)
        val needNavigateToChat = intent.getBooleanExtra(EXTRA_NEED_NAVIGATE_TO_CHAT, false)
        val autoDeleteEnabled = XingDunSessionManager.currentSession()?.features?.autoDelete == true
        val messagePinEnabled = XingDunSessionManager.currentSession()?.features?.messagePin == true

        val rootContainer = findViewById<LinearLayout>(R.id.demo_chatSettingRootContainer)
        val headerContainer = findViewById<LinearLayout>(R.id.demo_chatHeaderContainer)
        val tvTitle = findViewById<TextView>(R.id.demo_tvChatTitle)
        val btnBack = findViewById<ImageView>(R.id.demo_btnBack)
        val btnMore = findViewById<ImageView>(R.id.demo_btnMore)
        val leftContainer = findViewById<LinearLayout>(R.id.demo_leftContainer)
        val headerDivider = findViewById<View>(R.id.demo_headerDivider)
        val badgeContainer = findViewById<FrameLayout>(R.id.demo_badgeContainer)
        val settingContainer = findViewById<FrameLayout>(R.id.demo_chatSettingContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            settingContainer.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.visibility = View.GONE
        badgeContainer.visibility = View.GONE

        if (!userID.isNullOrEmpty()) {
            tvTitle.text = getString(R.string.demo_chat_setting_chat_settings)
            val settingView = C2CChatSettingView(this)
            settingContainer.addView(settingView)
            settingView.setup(
                userID = userID,
                onSendMessageClick = {
                    if (needNavigateToChat) {
                        ChatActivity.start(this, "c2c_$userID")
                    }
                    finish()
                },
                autoDeleteTitle = getString(R.string.xingdun_auto_delete_title).takeIf { autoDeleteEnabled },
                onAutoDeleteClick = if (autoDeleteEnabled) {
                    { XingDunAutoDeleteActivity.start(this, "c2c_$userID", canUpdate = true) }
                } else {
                    null
                },
                searchMessagesTitle = getString(R.string.xingdun_search_chat_content),
                onSearchMessagesClick = {
                    ConversationSearchActivity.start(
                        context = this,
                        conversationID = "c2c_$userID",
                        displayName = settingView.currentContactDisplayName(),
                        avatarURL = settingView.currentContactAvatarURL(),
                    )
                },
                onContactDeleted = {
                    EventBus.post(Event.ContactDeleted(userID))
                    finish()
                },
                showChatBackground = false,
                onCreateGroupClick = {
                    ContactFlowLauncher.showCreateGroupChatPage(
                        context = this,
                        initialSelectedUserIDs = listOf(userID),
                    ) { conversationID ->
                        ChatActivity.start(this, conversationID)
                    }
                },
                layoutStyle = C2CChatSettingView.LayoutStyle.XINGDUN_INSET_GROUPED,
            )
        } else if (!groupID.isNullOrEmpty()) {
            tvTitle.text = getString(R.string.demo_chat_setting_group_settings)
            val settingView = GroupChatSettingView(this)
            groupSettingView = settingView
            settingContainer.addView(settingView)
            settingView.setup(
                groupID = groupID,
                onSendMessageClick = {
                    if (needNavigateToChat) {
                        ChatActivity.start(this, "group_$groupID")
                    }
                    finish()
                },
                onGroupMemberClick = { member ->
                    handleGroupMemberClick(member.userID)
                },
                onGroupMemberListClick = {
                    XingDunGroupMembersActivity.start(this, groupID)
                },
                onGroupInfoClick = {
                    XingDunGroupInfoActivity.start(this, groupID)
                },
                onGroupAnnouncementClick = {
                    XingDunGroupAnnouncementActivity.start(this, groupID)
                },
                onGroupQRCodeClick = {
                    XingDunGroupQRCodeActivity.start(this, groupID)
                },
                onGroupManagementClick = {
                    XingDunGroupManagementActivity.start(this, groupID)
                },
                onTransferOwnerClick = {
                    XingDunGroupTransferOwnerActivity.start(this, groupID)
                },
                onGroupNicknameClick = {
                    XingDunGroupNicknameActivity.start(this, groupID)
                },
                autoDeleteTitle = getString(R.string.xingdun_auto_delete_title).takeIf { autoDeleteEnabled },
                onAutoDeleteClick = if (autoDeleteEnabled) {
                    { XingDunAutoDeleteActivity.start(this, "group_$groupID", canUpdate = true) }
                } else {
                    null
                },
                pinnedMessagesTitle = getString(R.string.xingdun_pinned_messages_entry).takeIf { messagePinEnabled },
                onPinnedMessagesClick = if (messagePinEnabled) {
                    { XingDunPinnedMessagesActivity.start(this, "group_$groupID") }
                } else {
                    null
                },
                searchMessagesTitle = getString(R.string.xingdun_search_chat_content),
                onSearchMessagesClick = {
                    ConversationSearchActivity.start(
                        context = this,
                        conversationID = "group_$groupID",
                        displayName = groupSettingView?.currentGroupDisplayName().orEmpty()
                            .ifBlank { getString(R.string.demo_chat_setting_group_settings) },
                        avatarURL = groupSettingView?.currentGroupAvatarURL()
                    )
                },
                showGroupPolicySummary = false,
                showChatBackground = false,
                showInternalGroupID = false,
                onGroupDeleted = {
                    EventBus.post(Event.GroupDeleted(groupID))
                    finish()
                }
            )
        }

        val currentColors = themeStore.themeState.value.currentTheme.tokens.color
        rootContainer.setBackgroundColor(currentColors.bgColorOperate)
        headerContainer.setBackgroundColor(currentColors.bgColorOperate)
        tvTitle.setTextColor(currentColors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(currentColors.textColorSecondary)
        headerDivider.setBackgroundColor(currentColors.strokeColorPrimary)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        if (!groupID.isNullOrEmpty()) {
            activityScope?.launch {
                val session = XingDunSessionManager.currentSession() ?: return@launch
                runCatching {
                    XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                        session,
                        "team/detail",
                        mapOf("team_id" to groupID),
                        XingDunGroupDetail::class.java,
                    )
                }.onSuccess { detail ->
                    groupSettingView?.setDisplayGroupID(detail.publicGroupId)
                    groupSettingView?.setCanInviteMembersOverride(XingDunGroupMemberPolicy.canInvite(detail))
                    if (autoDeleteEnabled) {
                        groupSettingView?.setAssignedCustomerService(detail.currentUserIsAssignedCs)
                    }
                }
            }
        }
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                val colors = state.currentTheme.tokens.color
                rootContainer.setBackgroundColor(colors.bgColorOperate)
                headerContainer.setBackgroundColor(colors.bgColorOperate)
                tvTitle.setTextColor(colors.textColorPrimary)
                btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
                headerDivider.setBackgroundColor(colors.strokeColorPrimary)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        groupSettingView?.refreshGroupData()
    }

    private fun handleGroupMemberClick(memberUserID: String) {
        lifecycleScope.launch {
            memberPreviewNavigator.open(intent.getStringExtra(EXTRA_GROUP_ID).orEmpty(), memberUserID)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope?.cancel()
        activityScope = null
    }
}
