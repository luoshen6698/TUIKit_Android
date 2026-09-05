package io.trtc.tuikit.chat.demo.chat

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePadding
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.common.observeOn
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.common.Event
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunCustomMessagePresentation
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunCustomMessageParser
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunContactDetailActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunContactForwardPickerActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunForegroundNotificationManager
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFavoriteMessageRequest
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMessageFavoritePolicy
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMessageFavoriteRepository
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunLocalMessageMarkRepository
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunPinnedMessagePolicy
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunPinnedMessageRepository
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunEmojiCompatibility
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunPinnedMessagesActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessage
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessagePage
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunRuntimeFeaturePolicy
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputActionIDs
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageActionIDs
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryFormatter
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.senderDisplayName
import io.trtc.tuikit.chat.uikit.pages.ChatPageView
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    private var activityScope: CoroutineScope? = null
    private var autoDeleteController: io.trtc.tuikit.chat.demo.xingdun.features.XingDunAutoDeleteController? = null
    private var groupPermissionJob: Job? = null
    private var groupPermissionRevision = 0L
    private val groupPermissionListener = object : V2TIMAdvancedMsgListener() {
        override fun onRecvNewMessage(message: V2TIMMessage?) {
            message ?: return
            if (!::conversationID.isInitialized || "group_${message.groupID}" != conversationID) return
            val custom = message.customElem?.data?.toString(Charsets.UTF_8)
                ?.let { XingDunCustomMessageParser.parse(it, message.customElem?.description) }
            if (message.elemType == V2TIMMessage.V2TIM_ELEM_TYPE_GROUP_TIPS ||
                custom?.requiresGroupPermissionRefresh() == true) {
                runOnUiThread { if (!isDestroyed && !isFinishing) loadGroupRuntimePermissions() }
            }
        }
    }
    private val themeStore by lazy { ThemeStore.shared(this) }
    private val contactStore by lazy { ContactStore.shared }
    private val groupStore by lazy { GroupStore.shared }

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var chatTitleIcon: ImageView
    private lateinit var tvChatTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: FrameLayout
    private lateinit var tvUnreadBadge: TextView
    private lateinit var leftContainer: LinearLayout
    private lateinit var btnMultiSelectCancel: TextView
    private lateinit var chatPageView: ChatPageView
    private lateinit var pinnedMessageBar: LinearLayout
    private lateinit var pinnedMessageIcon: ImageView
    private lateinit var pinnedMessageSummary: TextView
    private lateinit var pinnedMessageCount: TextView
    private lateinit var pinnedMessageChevron: ImageView

    private var isPeerTyping = false
    private var latestChatTitle: String = ""
    private lateinit var conversationID: String
    private var messageFavoriteEnabled = false
    private var messagePinEnabled = false
    private var canManagePinnedMessages = false
    private var pinnedPage = XingDunPinnedMessagePage()
    private val activeFavoriteMessageIDs = mutableSetOf<String>()
    private val contactCardPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val selectedUserID = result.data
            ?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_USER_ID)
            ?.trim()
            .orEmpty()
            .ifBlank {
                result.data
                    ?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_CONVERSATION_ID)
                    .orEmpty()
                    .removePrefix(C2C_CONVERSATION_ID_PREFIX)
                    .trim()
            }
        if (selectedUserID.isNotEmpty()) {
            sendContactCard(
                selectedUserID,
                result.data?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_DISPLAY_NAME),
                result.data?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_AVATAR),
            )
        }
    }
    private val pinnedMessagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val selection = XingDunPinnedMessagesActivity.readLocateSelection(result.data)
            ?: return@registerForActivityResult
        if (
            ::conversationID.isInitialized &&
            ::chatPageView.isInitialized &&
            selection.conversationID == conversationID
        ) {
            locatePinnedMessage(
                messageID = selection.messageID,
                messageSequence = selection.messageSequence,
                pinVersion = selection.pinVersion,
            )
        }
    }

    private val pinnedRepositoryListener: (String) -> Unit = { changedID ->
        if (::conversationID.isInitialized && changedID == conversationID) {
            runOnUiThread { loadPinnedMessages(force = true) }
        }
    }

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversationID"
        private const val EXTRA_LOCATE_MESSAGE = "locateMessage"
        private const val EXTRA_LOCATE_MESSAGE_ID = "locateMessageID"
        private const val EXTRA_LOCATE_MESSAGE_SEQUENCE = "locateMessageSequence"
        private const val EXTRA_LOCATE_PIN_VERSION = "locatePinnedMessageVersion"
        private const val C2C_CONVERSATION_ID_PREFIX = "c2c_"
        private const val GROUP_CONVERSATION_ID_PREFIX = "group_"
        private const val UNREAD_BADGE_DEBOUNCE_MS = 300L
        private const val FAVORITE_ACTION_ID = "xingdun.message.favorite"
        private const val PIN_ACTION_ID = "xingdun.message.pin"
        private const val MARK_ACTION_ID = "xingdun.message.mark"
        private const val REPORT_ACTION_ID = "xingdun.message.report"
        private const val CONTACT_CARD_ACTION_ID = "xingdun.messageInput.contactCard"
        private const val REDPACKET_ACTION_ID = "xingdun.messageInput.redpacket"
        private const val MENTION_ACTION_ID = "xingdun.messageInput.mention"
        private const val CONTACT_CARD_TYPE = "contact_card"

        fun start(context: Context, conversationID: String) {
            start(context, conversationID, null)
        }

        fun start(context: Context, conversationID: String, locateMessage: MessageInfo?) {
            context.startActivity(Intent(context, ChatActivity::class.java).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                if (locateMessage != null) {
                    putExtra(EXTRA_LOCATE_MESSAGE, locateMessage)
                }
            })
        }

        fun startForMessageID(
            context: Context,
            conversationID: String,
            messageID: String,
            messageSequence: Long? = null,
            pinVersion: Int = 0,
        ) {
            context.startActivity(Intent(context, ChatActivity::class.java).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                putExtra(EXTRA_LOCATE_MESSAGE_ID, messageID)
                messageSequence?.takeIf { it > 0L }?.let {
                    putExtra(EXTRA_LOCATE_MESSAGE_SEQUENCE, it)
                }
                putExtra(EXTRA_LOCATE_PIN_VERSION, pinVersion)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_chat)

        conversationID = intent?.getStringExtra(EXTRA_CONVERSATION_ID) ?: run {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        val locateMessage = intent?.getParcelableExtra<MessageInfo>(EXTRA_LOCATE_MESSAGE)
        val locateMessageID = intent?.getStringExtra(EXTRA_LOCATE_MESSAGE_ID)
        val locateMessageSequence = intent?.getLongExtra(EXTRA_LOCATE_MESSAGE_SEQUENCE, 0L) ?: 0L
        val locatePinVersion = intent?.getIntExtra(EXTRA_LOCATE_PIN_VERSION, 0) ?: 0

        rootContainer = findViewById(R.id.demo_chatRootContainer)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        chatTitleIcon = findViewById(R.id.demo_chatTitleIcon)
        tvChatTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        tvUnreadBadge = findViewById(R.id.demo_tvUnreadBadge)
        leftContainer = findViewById(R.id.demo_leftContainer)
        btnMultiSelectCancel = findViewById(R.id.demo_btnMultiSelectCancel)
        btnMultiSelectCancel.text = getString(R.string.demo_chat_header_cancel)
        updateChatTitle(ChatTitleResolver.resolve(conversationID = conversationID))
        val chatPageContainer = findViewById<FrameLayout>(R.id.demo_chatPageContainer)
        pinnedMessageBar = findViewById(R.id.demo_pinnedMessageBar)
        pinnedMessageIcon = findViewById(R.id.demo_pinnedMessageIcon)
        pinnedMessageSummary = findViewById(R.id.demo_pinnedMessageSummary)
        pinnedMessageCount = findViewById(R.id.demo_pinnedMessageCount)
        pinnedMessageChevron = findViewById(R.id.demo_pinnedMessageChevron)
        messageFavoriteEnabled = XingDunSessionManager.currentSession()?.features?.messageFavorite == true
        messagePinEnabled = XingDunSessionManager.currentSession()?.features?.messagePin == true
        canManagePinnedMessages = conversationID.startsWith(C2C_CONVERSATION_ID_PREFIX)
        pinnedMessageBar.setOnClickListener {
            pinnedMessagesLauncher.launch(
                XingDunPinnedMessagesActivity.createSelectionIntent(this, conversationID),
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            chatPageContainer.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.setOnClickListener {
            handleChatSettingNavigation(
                userID = getUserID(conversationID),
                groupID = getGroupID(conversationID)
            )
        }
        btnMore.expandTouchTarget()

        val isC2CConversation = conversationID.startsWith(C2C_CONVERSATION_ID_PREFIX)
        val isGroupConversation = conversationID.startsWith(GROUP_CONVERSATION_ID_PREFIX)
        chatTitleIcon.visibility = if (isGroupConversation) View.VISIBLE else View.GONE
        val featureAvailability = XingDunRuntimeFeaturePolicy.chatAvailability(
            features = XingDunSessionManager.currentSession()?.features,
            isDirectConversation = isC2CConversation,
            isGroupConversation = isGroupConversation,
        )
        val messageListConfig = ChatMessageListConfig(
            isSupportReaction = false,
            isSupportConvertToText = false,
            isSupportTranslate = false,
            isSupportListenFromHere = false,
            isGroupCallEnabled = featureAvailability.groupCall,
        )
        XingDunCustomMessagePresentation.configure(messageListConfig)
        configureBusinessMessageActions(messageListConfig)
        val messageInputConfig = ChatMessageInputConfig(
            isShowAudioCall = featureAvailability.audioCall,
            isShowVideoCall = featureAvailability.videoCall,
        )
        messageInputConfig.transformOutgoingText(XingDunEmojiCompatibility::transformOutgoingText)
        configureMessageInputActions(messageInputConfig, isC2CConversation, featureAvailability.redpacket)

        chatPageView = ChatPageView(this)
        chatPageContainer.addView(chatPageView)
        chatPageView.setup(
            conversationID = conversationID,
            messageListConfig = messageListConfig,
            messageInputConfig = messageInputConfig,
            locateMessage = locateMessage,
            onUserClick = { userID ->
                openContactDetailFromMessage(userID)
            },
            onMultiSelectStateChanged = { isMultiSelect ->
                updateHeaderForMultiSelect(isMultiSelect)
            },
            onTypingStatusChanged = { isTyping ->
                isPeerTyping = isTyping
                tvChatTitle.text = if (isTyping) {
                    getString(R.string.demo_chat_typing_indicator)
                } else {
                    latestChatTitle
                }
            }
        )
        if (isGroupConversation) {
            chatPageView.setComposerRestriction(getString(R.string.xingdun_group_sending_permission_loading))
        }
        if (!locateMessageID.isNullOrBlank()) {
            locatePinnedMessage(
                messageID = locateMessageID,
                messageSequence = locateMessageSequence.takeIf { it > 0L },
                pinVersion = locatePinVersion,
            )
        }
        btnMultiSelectCancel.setOnClickListener {
            chatPageView.exitMultiSelectMode()
        }
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        if (XingDunSessionManager.currentSession()?.features?.autoDelete == true) {
            autoDeleteController = io.trtc.tuikit.chat.demo.xingdun.features.XingDunAutoDeleteController(
                this, conversationID, requireNotNull(activityScope), requireNotNull(chatPageView.messageStore()),
            )
        }
        V2TIMManager.getMessageManager().addAdvancedMsgListener(groupPermissionListener)

        if (messageFavoriteEnabled) {
            activityScope?.launch { runCatching { XingDunMessageFavoriteRepository.loadRecent() } }
        }

        if (messagePinEnabled) {
            XingDunPinnedMessageRepository.addListener(pinnedRepositoryListener)
            loadPinnedMessages()
        }

        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }

        activityScope?.let { scope ->
            EventBus.observeOn<Event.ContactDeleted>(scope) { event ->
                if ("c2c_${event.contactID}" == conversationID) {
                    finish()
                }
            }
            EventBus.observeOn<Event.GroupDeleted>(scope) { event ->
                if ("group_${event.groupID}" == conversationID) {
                    finish()
                }
            }
            scope.launch {
                groupStore.groupEventFlow.collectLatest { event ->
                    when (event) {
                        is GroupEvent.OnKickedFromGroup -> {
                            if ("group_${event.groupID}" == conversationID) {
                                finish()
                            }
                        }
                        is GroupEvent.OnGroupDismissed -> {
                            if ("group_${event.groupID}" == conversationID) {
                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        val conversationListStore = ConversationListStore.create()
        conversationListStore.getConversationInfo(conversationID, object : GetConversationInfoCompletionHandler {
            override fun onSuccess(conversationInfo: ConversationInfo) {
                updateChatTitle(
                    ChatTitleResolver.resolve(
                        conversationTitle = conversationInfo.title,
                        conversationID = conversationID
                    )
                )
            }

            override fun onFailure(code: Int, desc: String) {}
        })

        val currentConversationFlow = conversationListStore.state.conversationList
            .map { list -> list.firstOrNull { it.conversationID == conversationID } }
            .stateIn(activityScope!!, SharingStarted.WhileSubscribed(5000), null)

        activityScope?.launch {
            currentConversationFlow.collect { info ->
                if (info != null) {
                    updateChatTitle(
                        ChatTitleResolver.resolve(
                            conversationTitle = info.title,
                            conversationID = conversationID
                        )
                    )
                }
            }
        }

        activityScope?.launch {
            observeTotalUnreadCount(conversationListStore)
        }
    }

    private fun configureBusinessMessageActions(config: ChatMessageListConfig) {
        config.customizeActions {
            val message = editorContext.message
            val messageID = message.msgID.takeIf(String::isNotBlank) ?: return@customizeActions
            if (XingDunCustomMessageParser.parse(message)?.isControl == true) return@customizeActions
            if (message.status in setOf(MessageStatus.REVOKED, MessageStatus.DELETED)) return@customizeActions

            if (messageFavoriteEnabled) {
                val isFavorite = XingDunMessageFavoriteRepository.isFavorite(messageID)
                val favoriteAction = MessageCustomAction(
                    ID = FAVORITE_ACTION_ID,
                    title = getString(
                        if (isFavorite) R.string.xingdun_favorite_action_remove
                        else R.string.xingdun_favorite_action_add,
                    ),
                    iconResID = R.drawable.xingdun_ic_mine_favorite,
                    action = { handleFavoriteMessageAction(it) },
                )
                if (!insertBefore(MessageActionIDs.DELETE, favoriteAction)) add(favoriteAction)
            }

            if (messagePinEnabled && canManagePinnedMessages) {
                val isPinned = XingDunPinnedMessageRepository.isPinned(
                    this@ChatActivity,
                    conversationID,
                    messageID,
                )
                val pinAction = MessageCustomAction(
                    ID = PIN_ACTION_ID,
                    title = getString(if (isPinned) R.string.xingdun_pinned_action_unpin else R.string.xingdun_pinned_action_pin),
                    iconResID = R.drawable.xingdun_ic_pin,
                    action = { handlePinnedMessageAction(it) },
                )
                if (!insertBefore(MessageActionIDs.DELETE, pinAction)) add(pinAction)
            }

            val isMarked = XingDunLocalMessageMarkRepository.isMarked(conversationID, messageID)
            val markAction = MessageCustomAction(
                ID = MARK_ACTION_ID,
                title = getString(if (isMarked) R.string.xingdun_mark_action_remove else R.string.xingdun_mark_action_add),
                iconResID = R.drawable.xingdun_ic_message_mark,
                action = { handleLocalMarkAction(it) },
            )
            if (!insertBefore(MessageActionIDs.DELETE, markAction)) add(markAction)

            if (shouldOfferMessageReport(message)) {
                val reportAction = MessageCustomAction(
                    ID = REPORT_ACTION_ID,
                    title = getString(R.string.xingdun_report_message_action),
                    iconResID = R.drawable.xingdun_ic_mine_report,
                    action = { reportMessage(it) },
                )
                if (!insertBefore(MessageActionIDs.DELETE, reportAction)) add(reportAction)
            }
        }
    }

    private fun handleLocalMarkAction(message: MessageInfo) {
        val messageID = message.msgID.takeIf(String::isNotBlank) ?: return
        val marked = XingDunLocalMessageMarkRepository.toggle(conversationID, messageID)
        chatPageView.refreshMessagePresentation(messageID)
        Toast.makeText(
            this,
            if (marked) R.string.xingdun_message_marked else R.string.xingdun_message_unmarked,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun shouldOfferMessageReport(message: MessageInfo): Boolean {
        if (message.isSentBySelf || message.messageType == MessageType.TEXT || message.messageType == MessageType.TIPS) return false
        if (message.status in setOf(MessageStatus.REVOKED, MessageStatus.DELETED)) return false
        return XingDunCustomMessageParser.parse(message)?.type != "redpacket"
    }

    private fun reportMessage(message: MessageInfo) {
        val messageID = message.msgID.takeIf(String::isNotBlank) ?: return
        val sender = message.senderDisplayName.trim().ifBlank { message.from.userID }
        XingDunFeatureActivity.startReport(
            context = this,
            targetType = "message",
            targetID = messageID,
            displayName = getString(R.string.xingdun_report_message_target, sender),
            displayID = messageID,
        )
    }

    private fun handleFavoriteMessageAction(message: MessageInfo) {
        val messageID = message.msgID.takeIf(String::isNotBlank) ?: return
        if (!activeFavoriteMessageIDs.add(messageID)) return
        val currentlyFavorite = XingDunMessageFavoriteRepository.isFavorite(messageID)
        val scope = activityScope ?: run {
            activeFavoriteMessageIDs.remove(messageID)
            return
        }
        scope.launch {
            val result = runCatching {
                if (currentlyFavorite) {
                    XingDunMessageFavoriteRepository.unfavorite(messageID)
                } else {
                    XingDunMessageFavoriteRepository.favorite(favoriteRequest(message))
                }
            }
            activeFavoriteMessageIDs.remove(messageID)
            Toast.makeText(
                this@ChatActivity,
                result.fold(
                    onSuccess = {
                        if (currentlyFavorite) R.string.xingdun_favorite_removed
                        else R.string.xingdun_favorite_added
                    },
                    onFailure = { R.string.xingdun_favorite_update_failed },
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun favoriteRequest(message: MessageInfo): XingDunFavoriteMessageRequest {
        val summary = MessageListMessageSummaryFormatter().format(this, message, conversationID)
        val text = when (val payload = message.messagePayload) {
            is TextMessagePayload -> payload.text
            is CustomMessagePayload -> XingDunCustomMessageParser.parse(message)?.summary(this) ?: payload.description.orEmpty()
            else -> ""
        }
        val timestamp = message.timestamp?.let { if (it > 10_000_000_000L) it else it * 1_000L }
        return XingDunFavoriteMessageRequest(
            messageId = message.msgID,
            conversationId = conversationID,
            messageSequence = message.sequence,
            senderId = message.from.userID,
            messageType = XingDunMessageFavoritePolicy.serverMessageType(message.messageType.name),
            text = text.ifBlank { summary.takeIf { message.messageType.name == "CUSTOM" }.orEmpty() },
            attachment = favoriteAttachment(message),
            sentAt = timestamp,
        )
    }

    private fun favoriteAttachment(message: MessageInfo): Any = when (val payload = message.messagePayload) {
        is ImageMessagePayload -> {
            val original = payload.originalImageURL?.takeIf(String::isNotBlank)
            val thumbnail = payload.thumbImageURL?.takeIf(String::isNotBlank)
                ?: payload.largeImageURL?.takeIf(String::isNotBlank)
                ?: original
            buildList {
                val info = buildList {
                    original?.let { add(mapOf("Type" to 1, "URL" to it)) }
                    thumbnail?.let { add(mapOf("Type" to 3, "URL" to it)) }
                }
                if (info.isNotEmpty()) add(mapOf("MsgType" to "TIMImageElem", "MsgContent" to mapOf("ImageInfoArray" to info)))
            }
        }
        is VideoMessagePayload -> mapOf(
            "ThumbUrl" to payload.videoSnapshotURL.orEmpty(),
            "VideoUrl" to payload.videoURL.orEmpty(),
        ).filterValues(String::isNotBlank).let { content ->
            if (content.isEmpty()) emptyList()
            else listOf(mapOf("MsgType" to "TIMVideoFileElem", "MsgContent" to content))
        }
        is AudioMessagePayload -> listOf(
            mapOf(
                "MsgType" to "TIMSoundElem",
                "MsgContent" to mapOf(
                    "Second" to payload.audioDuration,
                    "Url" to payload.audioURL.orEmpty(),
                    "Size" to payload.audioSize,
                ),
            ),
        )
        else -> emptyList<Map<String, Any>>()
    }

    private fun handlePinnedMessageAction(message: MessageInfo) {
        val messageID = message.msgID.takeIf(String::isNotBlank) ?: return
        val currentlyPinned = XingDunPinnedMessageRepository.isPinned(this, conversationID, messageID)
        val summary = MessageListMessageSummaryFormatter().format(this, message, conversationID)
        if (conversationID.startsWith(C2C_CONVERSATION_ID_PREFIX)) {
            val pinned = XingDunPinnedMessageRepository.toggleDirect(
                this,
                conversationID,
                messageID,
                message.sequence,
                message.from.userID,
                message.senderDisplayName,
                message.messageType.name,
                summary,
            )
            Toast.makeText(
                this,
                if (pinned) R.string.xingdun_pinned_pinned else R.string.xingdun_pinned_unpinned,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (!canManagePinnedMessages) return
        activityScope?.launch {
            val result = runCatching {
                if (currentlyPinned) {
                    XingDunPinnedMessageRepository.unpinGroup(conversationID, messageID)
                } else {
                    XingDunPinnedMessageRepository.pinGroup(
                        conversationID,
                        messageID,
                        conversationID.removePrefix(GROUP_CONVERSATION_ID_PREFIX),
                        message.sequence,
                    )
                }
            }
            Toast.makeText(
                this@ChatActivity,
                result.fold(
                    onSuccess = {
                        if (currentlyPinned) R.string.xingdun_pinned_unpinned else R.string.xingdun_pinned_pinned
                    },
                    onFailure = { R.string.xingdun_pinned_update_failed },
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun loadPinnedMessages(force: Boolean = false) {
        if (!messagePinEnabled || activityScope == null) return
        XingDunPinnedMessageRepository.cached(conversationID)?.let {
            pinnedPage = it
            renderPinnedMessageBar()
        }
        activityScope?.launch {
            runCatching { XingDunPinnedMessageRepository.load(this@ChatActivity, conversationID, force) }
                .onSuccess {
                    pinnedPage = it
                    renderPinnedMessageBar()
                }
                .onFailure {
                    if (pinnedPage.items.isEmpty()) pinnedMessageBar.visibility = View.GONE
                }
        }
    }

    private fun locatePinnedMessage(messageID: String, messageSequence: Long?, pinVersion: Int) {
        if (!::chatPageView.isInitialized || messageID.isBlank()) return
        chatPageView.post {
            chatPageView.locateMessageByID(
                messageID = messageID,
                messageSequence = messageSequence,
            ) { located ->
                if (located && pinVersion > 0) {
                    XingDunPinnedMessageRepository.markRead(
                        this,
                        XingDunPinnedMessage(
                            messageId = messageID,
                            conversationId = conversationID,
                            isPinned = true,
                            version = pinVersion,
                        ),
                    )
                } else if (!located) {
                    Toast.makeText(this, R.string.xingdun_pinned_locate_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadGroupRuntimePermissions() {
        if (!conversationID.startsWith(GROUP_CONVERSATION_ID_PREFIX)) return
        val revision = ++groupPermissionRevision
        groupPermissionJob?.cancel()
        groupPermissionJob = activityScope?.launch {
            val result = runCatching {
                val session = XingDunSessionManager.currentSession() ?: error("Missing session")
                XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session,
                    "team/detail",
                    mapOf("team_id" to conversationID.removePrefix(GROUP_CONVERSATION_ID_PREFIX)),
                    XingDunGroupDetail::class.java,
                )
            }
            if (revision != groupPermissionRevision) return@launch
            result.onSuccess { detail ->
                if (messagePinEnabled) {
                    canManagePinnedMessages = XingDunPinnedMessagePolicy.canManage(
                        detail.currentUserRole,
                        detail.currentUserIsAssignedCs,
                        detail.pinMessageMode,
                    )
                }
                chatPageView.setComposerRestriction(
                    getString(R.string.xingdun_group_sending_disabled).takeUnless { detail.canSendMessages },
                )
            }.onFailure {
                if (it is CancellationException) throw it
                chatPageView.setComposerRestriction(
                    getString(R.string.xingdun_group_sending_permission_unavailable),
                )
            }
        }
    }

    private fun renderPinnedMessageBar() {
        val pins = XingDunPinnedMessageRepository.unreadPins(this, conversationID, pinnedPage.items)
        val latest = pins.firstOrNull()
        if (latest == null) {
            pinnedMessageBar.visibility = View.GONE
            return
        }
        pinnedMessageSummary.text = pinnedSummary(latest)
        pinnedMessageCount.text = getString(R.string.xingdun_pinned_banner_count, pins.size)
        pinnedMessageBar.visibility = View.VISIBLE
    }

    private fun pinnedSummary(pin: XingDunPinnedMessage): String {
        pin.message?.text?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        return getString(
            when (XingDunPinnedMessagePolicy.summaryType(pin.message?.messageType)) {
                XingDunPinnedMessagePolicy.SummaryType.IMAGE -> R.string.xingdun_pinned_summary_image
                XingDunPinnedMessagePolicy.SummaryType.AUDIO -> R.string.xingdun_pinned_summary_audio
                XingDunPinnedMessagePolicy.SummaryType.VIDEO -> R.string.xingdun_pinned_summary_video
                XingDunPinnedMessagePolicy.SummaryType.FILE -> R.string.xingdun_pinned_summary_file
                XingDunPinnedMessagePolicy.SummaryType.CUSTOM -> R.string.xingdun_pinned_summary_custom
                XingDunPinnedMessagePolicy.SummaryType.MESSAGE -> R.string.xingdun_pinned_summary_message
            },
        )
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeTotalUnreadCount(store: ConversationListStore) {
        store.state.totalUnreadCount
            .debounce(UNREAD_BADGE_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { total ->
                if (total > 0L) {
                    badgeContainer.visibility = View.VISIBLE
                    tvUnreadBadge.text = if (total > 99L) "99+" else total.toString()
                } else {
                    badgeContainer.visibility = View.GONE
                }
            }
    }

    private fun configureMessageInputActions(
        config: ChatMessageInputConfig,
        isC2CConversation: Boolean,
        redpacketEnabled: Boolean,
    ) {
        config.customizeActions {
            val takePhoto = items.firstOrNull { it.ID == MessageInputActionIDs.TAKE_PHOTO }
            val recordVideo = items.firstOrNull { it.ID == MessageInputActionIDs.RECORD_VIDEO }

            replace(MessageInputActionIDs.ALBUM) { action ->
                action.copy(title = getString(R.string.xingdun_chat_more_photos))
            }
            if (takePhoto != null) {
                replace(MessageInputActionIDs.TAKE_PHOTO) { action ->
                    action.copy(
                        title = getString(R.string.xingdun_chat_more_capture),
                        onClick = {
                            showCaptureTypeChooser(
                                takePhoto = takePhoto.onClick,
                                recordVideo = recordVideo?.onClick,
                            )
                        },
                    )
                }
            }
            remove(MessageInputActionIDs.RECORD_VIDEO)

            if (isC2CConversation) {
                moveBefore(MessageInputActionIDs.AUDIO_CALL, MessageInputActionIDs.VIDEO_CALL)
            }
            if (redpacketEnabled) {
                add(
                    MessageInputMenuAction(
                        ID = REDPACKET_ACTION_ID,
                        title = getString(R.string.xingdun_chat_more_redpacket),
                        iconResID = R.drawable.xingdun_ic_gift_white,
                        iconTintColor = themeStore.themeState.value.currentTheme.tokens.color.textColorSecondary,
                        onClick = {
                            XingDunFeatureActivity.start(
                                this@ChatActivity,
                                XingDunFeatureActivity.MODE_REDPACKET_SEND,
                                conversationID,
                            )
                        },
                    )
                )
            }
            add(
                MessageInputMenuAction(
                    ID = CONTACT_CARD_ACTION_ID,
                    title = getString(R.string.xingdun_custom_contact),
                    iconResID = R.drawable.xingdun_ic_contact_card,
                    onClick = {
                        contactCardPicker.launch(
                            XingDunContactForwardPickerActivity.contactCardIntent(
                                this@ChatActivity,
                                conversationID,
                            )
                        )
                    },
                )
            )
            if (!isC2CConversation) {
                add(
                    MessageInputMenuAction(
                        ID = MENTION_ACTION_ID,
                        title = getString(R.string.xingdun_chat_more_mention),
                        iconResID = R.drawable.xingdun_ic_mention,
                        onClick = { chatPageView.showMentionMemberDialog() },
                    )
                )
            }
        }
    }

    private fun showCaptureTypeChooser(
        takePhoto: () -> Unit,
        recordVideo: (() -> Unit)?,
    ) {
        val entries = arrayOf(
            getString(R.string.xingdun_chat_capture_photo),
            getString(R.string.xingdun_chat_capture_video),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_chat_more_capture)
            .setItems(entries) { _, index ->
                if (index == 0) takePhoto() else recordVideo?.invoke()
            }
            .setNegativeButton(R.string.xingdun_cancel, null)
            .show()
    }

    private fun sendContactCard(userID: String, selectedDisplayName: String? = null, selectedAvatar: String? = null) {
        val contact = contactStore.state.friendList.value.firstOrNull { it.userID == userID }
        val displayName = selectedDisplayName?.trim().orEmpty()
            .ifBlank { contact?.friendRemark?.trim().orEmpty() }
            .ifBlank { contact?.nickname?.trim().orEmpty() }
            .ifBlank { userID }
        if (contact == null && selectedDisplayName.isNullOrBlank()) {
            Toast.makeText(this, R.string.xingdun_contact_card_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val avatar = selectedAvatar?.trim().orEmpty()
            .ifBlank { contact?.avatarURL?.trim().orEmpty() }
        val payload = JsonObject().apply {
            addProperty("type", CONTACT_CARD_TYPE)
            add("data", JsonObject().apply {
                addProperty("accid", userID)
                addProperty("name", displayName)
                avatar.takeIf(String::isNotEmpty)?.let { addProperty("avatar", it) }
            })
        }.toString()
        MessageInputStore.create(conversationID).sendMessage(
            SendMessagePayload.CustomSendMessagePayload(payload, CONTACT_CARD_TYPE, ""),
            SendMessageOption(),
            object : CompletionHandler {
                override fun onSuccess() {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, R.string.xingdun_contact_detail_recommended, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, R.string.xingdun_contact_detail_recommend_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    private fun handleChatSettingNavigation(userID: String? = null, groupID: String? = null) {
        if (!userID.isNullOrEmpty()) {
            contactStore.getContactInfo(
                userIDList = listOf(userID),
                completion = object : GetContactInfoCompletionHandler {
                    override fun onSuccess(contactInfoList: List<ContactInfo>) {
                        val info = contactInfoList.firstOrNull() ?: return
                        if (info.isFriend == true) {
                            ChatSettingActivity.startC2C(this@ChatActivity, userID)
                        } else {
                            ContactFlowLauncher.showAddFriendForContact(
                                context = this@ChatActivity,
                                contactInfo = info
                            )
                        }
                    }

                    override fun onFailure(code: Int, desc: String) {
                    }
                }
            )
        } else if (!groupID.isNullOrEmpty()) {
            ChatSettingActivity.startGroup(this, groupID)
        }
    }

    private fun openContactDetailFromMessage(userID: String) {
        val peerUserID = getUserID(conversationID) ?: return
        if (userID != peerUserID) return

        contactStore.getContactInfo(
            userIDList = listOf(peerUserID),
            completion = object : GetContactInfoCompletionHandler {
                override fun onSuccess(contactInfoList: List<ContactInfo>) {
                    runOnUiThread {
                        val contact = contactInfoList.firstOrNull()
                        if (contact?.isFriend == true) {
                            XingDunContactDetailActivity.start(this@ChatActivity, contact)
                        } else {
                            XingDunContactDetailActivity.start(
                                this@ChatActivity,
                                peerUserID,
                                contact?.nickname ?: latestChatTitle,
                                contact?.avatarURL,
                            )
                        }
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    runOnUiThread {
                        XingDunContactDetailActivity.start(
                            this@ChatActivity,
                            peerUserID,
                            latestChatTitle,
                            null,
                        )
                    }
                }
            },
        )
    }

    private fun getUserID(conversationID: String): String? {
        return if (conversationID.startsWith(C2C_CONVERSATION_ID_PREFIX)) {
            conversationID.removePrefix(C2C_CONVERSATION_ID_PREFIX)
        } else {
            null
        }
    }

    private fun getGroupID(conversationID: String): String? {
        return if (conversationID.startsWith(GROUP_CONVERSATION_ID_PREFIX)) {
            conversationID.removePrefix(GROUP_CONVERSATION_ID_PREFIX)
        } else {
            null
        }
    }

    private fun updateHeaderForMultiSelect(isMultiSelect: Boolean) {
        leftContainer.visibility = if (isMultiSelect) View.GONE else View.VISIBLE
        btnMultiSelectCancel.visibility = if (isMultiSelect) View.VISIBLE else View.GONE
        btnMore.visibility = if (isMultiSelect) View.INVISIBLE else View.VISIBLE
        if (isMultiSelect) {
            btnMultiSelectCancel.expandTouchTarget()
        }
    }

    private fun updateChatTitle(title: String) {
        latestChatTitle = title
        if (!isPeerTyping) {
            tvChatTitle.text = title
        }
    }

    override fun onBackPressed() {
        if (::chatPageView.isInitialized && chatPageView.isInMultiSelectMode()) {
            chatPageView.exitMultiSelectMode()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        autoDeleteController?.start()
        intent?.getStringExtra(EXTRA_CONVERSATION_ID)?.let(XingDunForegroundNotificationManager::enterConversation)
        if (::conversationID.isInitialized && ::chatPageView.isInitialized) {
            loadGroupRuntimePermissions()
        }
    }

    override fun onPause() {
        autoDeleteController?.stop()
        intent?.getStringExtra(EXTRA_CONVERSATION_ID)?.let(XingDunForegroundNotificationManager::leaveConversation)
        super.onPause()
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        chatTitleIcon.imageTintList = ColorStateList.valueOf(0xFF23B39C.toInt())
        tvChatTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        btnMore.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        btnMultiSelectCancel.setTextColor(colors.textColorLink)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.buttonColorOff)
        }
        badgeContainer.background = badgeBg
        tvUnreadBadge.setTextColor(colors.textColorButton)

        pinnedMessageBar.setBackgroundColor(colors.bgColorInput)
        pinnedMessageIcon.imageTintList = ColorStateList.valueOf(colors.textColorLink)
        pinnedMessageSummary.setTextColor(colors.textColorPrimary)
        pinnedMessageCount.setTextColor(colors.textColorLink)
        pinnedMessageChevron.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
    }

    override fun onDestroy() {
        V2TIMManager.getMessageManager().removeAdvancedMsgListener(groupPermissionListener)
        if (messagePinEnabled) XingDunPinnedMessageRepository.removeListener(pinnedRepositoryListener)
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

}
