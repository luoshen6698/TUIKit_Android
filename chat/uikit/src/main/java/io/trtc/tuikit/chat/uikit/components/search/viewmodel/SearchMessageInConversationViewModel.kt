package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchMessageInConversationViewModel internal constructor(createStore: () -> SearchStoreGateway, scope: CoroutineScope? = null, pollIntervalMillis: Long = 10_000L, verificationBudgetMillis: Long = 10_000L, sourceDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO) : ViewModel() {
    internal constructor(store: SearchStoreGateway, scope: CoroutineScope) : this({ store }, scope, sourceDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
    constructor() : this({ DefaultSearchStoreGateway() })
    private val executionScope = scope ?: viewModelScope
    private val history = SearchMessageHistoryQuery(executionScope, createStore, listOf(SearchType.MESSAGE), pollIntervalMillis, verificationBudgetMillis, sourceDispatcher)
    private var conversation = ""
    val isSearching: StateFlow<Boolean> = history.isSearching
    val isLoadingMore: StateFlow<Boolean> = history.isLoadingMore
    val searchError: StateFlow<SearchError?> = history.error
    private val messages = MutableStateFlow<List<MessageInfo>>(emptyList())
    val messagesInConversation: StateFlow<List<MessageInfo>> = messages
    init {
        executionScope.launch {
            history.results.collect { rows -> messages.value = rows.firstOrNull { it.conversationID == conversation }?.messageList.orEmpty() }
        }
    }
    fun updateSearchQuery(conversationID: String, query: String) { conversation = conversationID; history.submit(conversationID, query) }
    fun searchMore() = history.searchMore()
    fun setHistoryActive(active: Boolean) = history.setActive(active)
    override fun onCleared() { history.close(); super.onCleared() }
}
