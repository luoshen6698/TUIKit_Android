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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchActivity : BaseActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var searchView: SearchView

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null
    private var extensionSearchJob: Job? = null

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
                ChatActivity.start(this, "c2c_${contact.userID}")
            },
            onGroupSelect = { group ->
                ChatActivity.start(this, "group_${group.groupID}")
            },
            onConversationSelect = { conversation ->
                val conversationID = conversation.conversationID
                if (!conversationID.isNullOrEmpty()) {
                    ChatActivity.start(this, conversationID)
                }
            },
            onMessageSelect = { message ->
                val conversationID = message.conversationID
                ChatActivity.start(this, conversationID, message)
            },
            onGlobalQueryChange = ::searchXingDunExtensions,
            onExtensionResultSelect = ::openXingDunExtensionResult,
            onBack = { finish() }
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

    private fun searchXingDunExtensions(query: String) {
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
