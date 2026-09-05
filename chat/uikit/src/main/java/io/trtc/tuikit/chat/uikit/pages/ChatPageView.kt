package io.trtc.tuikit.chat.uikit.pages
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingIndicatorController
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.MessageInputView
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.uikit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val messageListView: MessageListView
    private val messageInputView: MessageInputView

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null

    private var typingController: TypingIndicatorController? = null
    private var typingCollectJob: Job? = null
    private var typingConversationID: String? = null
    private var typingEnabled = false

    private var onTypingStatusChanged: (Boolean) -> Unit = {}
    private var isMultiSelect = false
    private var composerRestriction: CharSequence? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.uikit_page_chat, this, true)
        messageListView = findViewById(R.id.uikit_message_list_view)
        messageInputView = findViewById(R.id.uikit_message_input_view)
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        obtainTypingControllerIfNeeded()
        bindTypingController()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseTypingController()
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
    }

    private fun obtainTypingControllerIfNeeded() {
        if (!isAttachedToWindow || !typingEnabled || typingController != null) return
        val conversationID = typingConversationID ?: return
        typingController = TypingIndicatorController.obtain(conversationID)
    }

    private fun releaseTypingController() {
        typingCollectJob?.cancel()
        typingCollectJob = null
        typingController?.let { TypingIndicatorController.release(it) }
        typingController = null
    }

    private fun bindTypingController() {
        typingCollectJob?.cancel()
        typingCollectJob = null
        val controller = typingController ?: return
        val scope = viewScope ?: return
        typingCollectJob = scope.launch {
            controller.typingState.collectLatest { isTyping ->
                onTypingStatusChanged(isTyping)
            }
        }
    }

    fun messageStore(): io.trtc.tuikit.atomicxcore.api.message.MessageListStore? = messageListView.messageStore()

    fun setup(
        conversationID: String,
        locateMessage: MessageInfo? = null,
        messageListConfig: MessageListConfigProtocol = ChatMessageListConfig(),
        messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig(),
        onTypingStatusChanged: (Boolean) -> Unit = {},
        onMultiSelectStateChanged: (Boolean) -> Unit = {},
        onUserClick: (String) -> Unit = {}
    ) {
        this.onTypingStatusChanged = onTypingStatusChanged

        releaseTypingController()
        typingConversationID = conversationID
        typingEnabled = messageListConfig.enableTyping && ConversationIDUtil.isC2C(conversationID)
        obtainTypingControllerIfNeeded()
        bindTypingController()

        messageListView.setup(
            conversationID = conversationID,
            config = messageListConfig,
            locateMessage = locateMessage,
            onMultiSelectStateChanged = { isMultiSelect ->
                this.isMultiSelect = isMultiSelect
                updateComposerVisibility()
                onMultiSelectStateChanged(isMultiSelect)
            },
            onUserClick = onUserClick
        )
        val typingStatusSender: ((Boolean) -> Unit)? = if (typingEnabled) {
            { isTyping -> typingController?.sendTypingStatus(isTyping) }
        } else {
            null
        }
        messageInputView.setup(
            conversationID = conversationID,
            config = messageInputConfig,
            typingStatusSender = typingStatusSender
        )
    }

    fun exitMultiSelectMode() {
        messageListView.exitMultiSelectMode()
    }

    fun isInMultiSelectMode(): Boolean {
        return messageListView.isInMultiSelectMode()
    }

    fun locateMessageByID(
        messageID: String,
        messageSequence: Long? = null,
        completion: (Boolean) -> Unit = {},
    ) {
        messageListView.locateMessageByID(
            messageID = messageID,
            messageSequence = messageSequence,
            completion = completion,
        )
    }

    fun refreshMessagePresentation(messageID: String) {
        messageListView.refreshMessagePresentation(messageID)
    }

    fun showMentionMemberDialog() {
        messageInputView.showMentionMemberDialog()
    }

    fun setComposerRestriction(reason: CharSequence?) {
        composerRestriction = reason?.takeIf(CharSequence::isNotEmpty)
        messageInputView.setComposerRestriction(composerRestriction)
        if (composerRestriction != null) {
            messageInputView.clearFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(windowToken, 0)
        }
        updateComposerVisibility()
    }

    private fun updateComposerVisibility() {
        messageInputView.visibility = if (!isMultiSelect) View.VISIBLE else View.GONE
    }
}
