package io.trtc.tuikit.chat.demo.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.components.search.ui.SearchView
import io.trtc.tuikit.chat.uikit.components.search.ui.GlobalSearchExtensionResult
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMessageDeletionVisibility
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchGroupHistoryAccess
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageInConversationViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : BaseActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var searchView: SearchView

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null
    private var extensionSearchJob: Job? = null
    private var sourceSession: String? = null
    private var locateRevision = 0L

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, SearchActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        sourceSession = SearchMessageVisibility.currentSessionKey()

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        rootContainer = FrameLayout(this)
        setContentView(rootContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        searchView = SearchView(this)
        rootContainer.addView(
            searchView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        searchView.setup(
            onContactSelect = { contact ->
                locateRevision++
                ChatActivity.start(this, "c2c_${contact.userID}")
            },
            onGroupSelect = { group ->
                locateRevision++
                ChatActivity.start(this, "group_${group.groupID}")
            },
            onConversationSelect = { conversation ->
                locateRevision++
                val conversationID = conversation.conversationID
                if (!conversationID.isNullOrEmpty()) {
                    ChatActivity.start(this, conversationID)
                }
            },
            onMessageSelect = ::locateSearchMessage,
            onGlobalQueryChange = ::searchXingDunExtensions,
            onExtensionResultSelect = ::openXingDunExtensionResult,
            onBack = { finish() },
            onMessageSelectionInvalidated = { locateRevision++ },
        )
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
    }

    override fun onDestroy() {
        super.onDestroy()
        extensionSearchJob?.cancel()
        activityScope?.cancel()
        activityScope = null
    }

    override fun onPause() {
        locateRevision++
        super.onPause()
    }

    private fun locateSearchMessage(message: MessageInfo) {
        val conversationID = message.conversationID
        if (conversationID.isBlank() || message.msgID.isBlank()) return
        val selected = message.copy(quoteInfo = message.quoteInfo?.copy())
        val owner = sourceSession
        val selection = ++locateRevision
        val rows = ViewModelProvider(this)[SearchMessageInConversationViewModel::class.java].messagesInConversation
        fun isCurrent() = !isDestroyed && !isFinishing && selection == locateRevision &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            owner != null && owner == SearchMessageVisibility.currentSessionKey() &&
            rows.value.any { it.msgID == selected.msgID } &&
            !XingDunMessageDeletionVisibility.isDeleted(conversationID, selected.msgID)
        lifecycleScope.launch {
            val checked = XingDunHistoryMediaGate.checked(conversationID, selected.msgID, ::isCurrent,
                filter = { withContext(Dispatchers.IO) {
                    if (conversationID.startsWith("group_")) SearchMessageVisibility.messagesForAction(conversationID, listOf(selected))
                    else XingDunMessageDeletionVisibility.filterMessages(applicationContext, conversationID, listOf(selected))
                } },
                authorize = { withContext(Dispatchers.IO) { SearchGroupHistoryAccess.allows(conversationID, owner) } },
                onDenied = {
                    android.widget.Toast.makeText(this@SearchActivity, io.trtc.tuikit.chat.app.R.string.xingdun_group_access_denied,
                        android.widget.Toast.LENGTH_LONG).show()
                },
            ) ?: return@launch
            val target = checked.firstOrNull { it.msgID == selected.msgID } ?: return@launch
            if (isCurrent()) ChatActivity.start(this@SearchActivity, conversationID, target)
        }
    }

    private fun searchXingDunExtensions(query: String) {
        locateRevision++
        extensionSearchJob?.cancel()
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            searchView.updateExtensionResults(query, emptyList())
            return
        }
        extensionSearchJob = lifecycleScope.launch {
            delay(300)
            val sections = XingDunGlobalSearchExtensions.search(this@SearchActivity, keyword)
            searchView.updateExtensionResults(keyword, sections)
        }
    }

    private fun openXingDunExtensionResult(result: GlobalSearchExtensionResult) {
        locateRevision++
        when (result.metadata["kind"]) {
            "favorite" -> {
                val conversationID = result.metadata["conversation_id"].orEmpty()
                if (conversationID.isNotEmpty()) {
                    ChatActivity.start(this, conversationID)
                } else {
                    XingDunFeatureActivity.start(this, XingDunFeatureActivity.MODE_FAVORITES)
                }
            }
            "workspace" -> {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TAB_WORKSPACE)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }
            "workspace_create" -> XingDunFeatureActivity.start(
                this,
                XingDunFeatureActivity.MODE_WORKSPACE_CREATE,
                result.metadata["type"].orEmpty(),
            )
            "workspace_pending" -> XingDunFeatureActivity.start(
                this,
                XingDunFeatureActivity.MODE_WORKSPACE_PENDING,
            )
        }
    }
}

val MessageInfo.conversationID: String
    get() = if (conversationType == ConversationType.GROUP) {
        "group_$to"
    } else {
        if (isSentBySelf) "c2c_$to" else "c2c_${from.userID}"
    }
