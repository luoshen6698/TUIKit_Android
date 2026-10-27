package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope

class SearchMessageViewModel internal constructor(createStore: () -> SearchStoreGateway, scope: CoroutineScope? = null, pollIntervalMillis: Long = 10_000L, verificationBudgetMillis: Long = 10_000L, sourceDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO) : ViewModel() {
    internal constructor(store: SearchStoreGateway, scope: CoroutineScope) : this({ store }, scope, sourceDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
    constructor() : this({ DefaultSearchStoreGateway() })
    private val history = SearchMessageHistoryQuery(scope ?: viewModelScope, createStore, listOf(SearchType.MESSAGE), pollIntervalMillis, verificationBudgetMillis, sourceDispatcher)
    val isSearching: StateFlow<Boolean> = history.isSearching
    val isLoadingMore: StateFlow<Boolean> = history.isLoadingMore
    val searchError: StateFlow<SearchError?> = history.error
    val messageSearchResults: StateFlow<List<MessageSearchResultItem>> = history.results
    fun updateSearchQuery(query: String) = history.submit("", query)
    fun searchMore() = history.searchMore()
    fun setHistoryActive(active: Boolean) = history.setActive(active)
    override fun onCleared() { history.close(); super.onCleared() }
}
