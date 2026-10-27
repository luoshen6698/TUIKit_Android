package io.trtc.tuikit.chat.uikit.components.search.ui
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchAllViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchContactViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchGroupViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageInConversationViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.GroupSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class SearchFlowStep {
    GLOBAL_SEARCH,
    CONTACT_DETAIL,
    GROUP_DETAIL,
    MESSAGE_DETAIL,
    MESSAGE_IN_CONVERSATION_DETAIL
}

/** Optional app-owned sections appended to the stock global-search results. */
data class GlobalSearchExtensionResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class GlobalSearchExtensionSection(
    val id: String,
    val title: String,
    val results: List<GlobalSearchExtensionResult>,
)

class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var currentStep = SearchFlowStep.GLOBAL_SEARCH
    private var prevStep = SearchFlowStep.GLOBAL_SEARCH
    private var searchAllQuery = ""
    private var selectedConversation: MessageSearchResultItem? = null

    private var globalSearchPage: GlobalSearchPage? = null
    private var contactPage: SearchContactPage? = null
    private var groupPage: SearchGroupPage? = null
    private var messagePage: SearchMessagePage? = null
    private var messageInConversationPage: SearchMessageInConversationPage? = null

    private var viewScope: CoroutineScope? = null
    private val themeStore = ThemeStore.shared(context)
    private val deletionRetryStatus = TextView(context).apply {
        setText(R.string.group_deletion_search_retry)
        gravity = Gravity.CENTER
        val density = resources.displayMetrics.density
        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        visibility = View.GONE
        setOnClickListener { SearchMessageVisibility.invalidate() }
    }

    private var onContactSelect: ((FriendSearchInfo) -> Unit)? = null
    private var onGroupSelect: ((GroupSearchInfo) -> Unit)? = null
    private var onConversationSelect: ((MessageSearchResultItem) -> Unit)? = null
    private var onMessageSelect: ((MessageInfo) -> Unit)? = null
    private var onBack: (() -> Unit)? = null
    private var onGlobalQueryChange: ((String) -> Unit)? = null
    private var onExtensionResultSelect: ((GlobalSearchExtensionResult) -> Unit)? = null
    private var onMessageSelectionInvalidated: (() -> Unit)? = null

    init {
        applyTheme()
    }

    fun setup(
        onContactSelect: ((FriendSearchInfo) -> Unit)? = null,
        onGroupSelect: ((GroupSearchInfo) -> Unit)? = null,
        onConversationSelect: ((MessageSearchResultItem) -> Unit)? = null,
        onMessageSelect: ((MessageInfo) -> Unit)? = null,
        onGlobalQueryChange: ((String) -> Unit)? = null,
        onExtensionResultSelect: ((GlobalSearchExtensionResult) -> Unit)? = null,
        onBack: (() -> Unit)? = null,
        onMessageSelectionInvalidated: (() -> Unit)? = null,
    ) {
        this.onContactSelect = onContactSelect
        this.onGroupSelect = onGroupSelect
        this.onConversationSelect = onConversationSelect
        this.onMessageSelect = onMessageSelect
        this.onGlobalQueryChange = onGlobalQueryChange
        this.onExtensionResultSelect = onExtensionResultSelect
        this.onBack = onBack
        this.onMessageSelectionInvalidated = onMessageSelectionInvalidated
    }

    fun updateExtensionResults(
        query: String,
        sections: List<GlobalSearchExtensionSection>,
    ) {
        if (query.trim() == searchAllQuery.trim()) {
            globalSearchPage?.updateExtensionResults(sections)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            themeStore.themeState.collectLatest { applyTheme() }
        }
        scope.launch {
            SearchMessageVisibility.searchVerificationFailed.collectLatest { failed ->
                deletionRetryStatus.visibility = if (failed) View.VISIBLE else View.GONE
                deletionRetryStatus.setBackgroundColor(themeStore.themeState.value.currentTheme.tokens.color.bgColorInput)
                deletionRetryStatus.setTextColor(themeStore.themeState.value.currentTheme.tokens.color.textColorLink)
                deletionRetryStatus.bringToFront()
            }
        }

        navigateTo(SearchFlowStep.GLOBAL_SEARCH)
    }

    override fun onDetachedFromWindow() {
        onMessageSelectionInvalidated?.invoke()
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun navigateTo(step: SearchFlowStep) {
        onMessageSelectionInvalidated?.invoke()
        hideAllPages()
        prevStep = currentStep
        currentStep = step

        when (step) {
            SearchFlowStep.GLOBAL_SEARCH -> showGlobalSearch()
            SearchFlowStep.CONTACT_DETAIL -> showContactDetail()
            SearchFlowStep.GROUP_DETAIL -> showGroupDetail()
            SearchFlowStep.MESSAGE_DETAIL -> showMessageDetail()
            SearchFlowStep.MESSAGE_IN_CONVERSATION_DETAIL -> showMessageInConversationDetail()
            else -> showGlobalSearch()
        }
        if (deletionRetryStatus.parent == null) addView(deletionRetryStatus,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        deletionRetryStatus.bringToFront()
    }

    private fun showGlobalSearch() {
        if (globalSearchPage == null) {
            globalSearchPage = GlobalSearchPage(context, searchViewModel()).apply {
                onResultClick = { result ->
                    when (result) {
                        is FriendSearchInfo -> onContactSelect?.invoke(result)
                        is GroupSearchInfo -> onGroupSelect?.invoke(result)
                        is MessageSearchResultItem -> {
                            selectedConversation = result
                            navigateTo(SearchFlowStep.MESSAGE_IN_CONVERSATION_DETAIL)
                        }
                    }
                }
                onQueryChange = { query ->
                    searchAllQuery = query
                    onGlobalQueryChange?.invoke(query)
                }
                onExtensionResultClick = { result ->
                    onExtensionResultSelect?.invoke(result)
                }
                onShowMore = { searchType ->
                    when (searchType) {
                        SearchType.FRIEND -> navigateTo(SearchFlowStep.CONTACT_DETAIL)
                        SearchType.GROUP -> navigateTo(SearchFlowStep.GROUP_DETAIL)
                        SearchType.MESSAGE -> navigateTo(SearchFlowStep.MESSAGE_DETAIL)
                        else -> {}
                    }
                }
                onCancel = { this@SearchView.onBack?.invoke() }
            }
            addView(globalSearchPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        globalSearchPage?.visibility = View.VISIBLE
        globalSearchPage?.start()
    }

    private fun showContactDetail() {
        if (contactPage == null) {
            contactPage = SearchContactPage(context, searchViewModel()).apply {
                onContactClick = { contact -> this@SearchView.onContactSelect?.invoke(contact) }
                onQueryChange = { query -> searchAllQuery = query }
                onBack = {
                    navigateTo(SearchFlowStep.GLOBAL_SEARCH)
                    selectedConversation = null
                }
            }
            addView(contactPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        contactPage?.visibility = View.VISIBLE
        contactPage?.start(searchAllQuery)
    }

    private fun showGroupDetail() {
        if (groupPage == null) {
            groupPage = SearchGroupPage(context, searchViewModel()).apply {
                onGroupClick = { group -> this@SearchView.onGroupSelect?.invoke(group) }
                onQueryChange = { query -> searchAllQuery = query }
                onBack = {
                    navigateTo(SearchFlowStep.GLOBAL_SEARCH)
                    selectedConversation = null
                }
            }
            addView(groupPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        groupPage?.visibility = View.VISIBLE
        groupPage?.start(searchAllQuery)
    }

    private fun showMessageDetail() {
        if (messagePage == null) {
            messagePage = SearchMessagePage(context, searchViewModel()).apply {
                onConversationClick = { conversation ->
                    selectedConversation = conversation
                    navigateTo(SearchFlowStep.MESSAGE_IN_CONVERSATION_DETAIL)
                }
                onQueryChange = { query -> searchAllQuery = query }
                onBack = {
                    navigateTo(SearchFlowStep.GLOBAL_SEARCH)
                    selectedConversation = null
                }
            }
            addView(messagePage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        messagePage?.visibility = View.VISIBLE
        messagePage?.start(searchAllQuery)
    }

    private fun showMessageInConversationDetail() {
        val conversation = selectedConversation ?: return
        if (messageInConversationPage == null) {
            messageInConversationPage = SearchMessageInConversationPage(context, searchViewModel()).apply {
                onMessageClick = { message -> this@SearchView.onMessageSelect?.invoke(message) }
                onConversationSelect = { conv -> this@SearchView.onConversationSelect?.invoke(conv) }
                onQueryChange = { query ->
                    this@SearchView.onMessageSelectionInvalidated?.invoke()
                    searchAllQuery = query
                }
                onBack = {
                    if (prevStep == SearchFlowStep.GLOBAL_SEARCH) {
                        navigateTo(SearchFlowStep.GLOBAL_SEARCH)
                    } else if (prevStep == SearchFlowStep.MESSAGE_DETAIL) {
                        navigateTo(SearchFlowStep.MESSAGE_DETAIL)
                    }
                }
            }
            addView(messageInConversationPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        messageInConversationPage?.visibility = View.VISIBLE
        messageInConversationPage?.start(conversation, searchAllQuery)
    }

    private fun hideAllPages() {
        globalSearchPage?.visibility = View.GONE
        contactPage?.visibility = View.GONE
        groupPage?.visibility = View.GONE
        messagePage?.visibility = View.GONE
        messageInConversationPage?.visibility = View.GONE
    }

    private fun applyTheme() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
    }

    private inline fun <reified VM : ViewModel> searchViewModel(): VM {
        val owner = context.findViewModelStoreOwner()
            ?: error("SearchView requires a ViewModelStoreOwner host context.")
        return ViewModelProvider(owner)[VM::class.java]
    }
}
