package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchCategory(val type: SearchType, val results: List<Any>, val hasMore: Boolean = false, val totalCount: Int = 0)

class SearchAllViewModel internal constructor(createStore: () -> SearchStoreGateway, scope: CoroutineScope? = null, pollIntervalMillis: Long = 10_000L, verificationBudgetMillis: Long = 10_000L, sourceDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO) : ViewModel() {
    internal constructor(store: SearchStoreGateway, scope: CoroutineScope) : this({ store }, scope, sourceDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
    constructor() : this({ DefaultSearchStoreGateway() })
    private val executionScope = scope ?: viewModelScope
    private val history = SearchMessageHistoryQuery(executionScope, createStore, listOf(SearchType.FRIEND, SearchType.GROUP, SearchType.MESSAGE), pollIntervalMillis, verificationBudgetMillis, sourceDispatcher)
    private val directory = MutableStateFlow<List<SearchCategory>>(emptyList())
    private val categories = MutableStateFlow<List<SearchCategory>>(emptyList())
    val searchCategories: StateFlow<List<SearchCategory>> = categories
    val isSearching: StateFlow<Boolean> = history.isSearching
    val searchError: StateFlow<SearchError?> = history.error
    init {
        executionScope.launch {
            history.storeState.collectLatest { state ->
                directory.value = emptyList()
                if (state != null) {
                    val friends = combine(state.friendList, state.friendTotalCount, state.hasMoreFriends) { rows, count, more ->
                        SearchCategory(SearchType.FRIEND, rows.take(3), more || rows.size > 3, count)
                    }
                    val groups = combine(state.groupList, state.groupTotalCount, state.hasMoreGroups) { rows, count, more ->
                        SearchCategory(SearchType.GROUP, rows.take(3), more || rows.size > 3, count)
                    }
                    combine(friends, groups) { friend, group -> listOf(friend, group).filter { it.results.isNotEmpty() } }
                        .collect { directory.value = it }
                }
            }
        }
        executionScope.launch {
            combine(directory, history.results) { entries, rows ->
                entries + if (rows.isEmpty()) emptyList() else listOf(SearchCategory(SearchType.MESSAGE,
                    rows.take(3), rows.size > 3 || history.storeState.value?.hasMoreMessageResults?.value == true,
                    rows.sumOf { it.messageCount }))
            }.collect { categories.value = it }
        }
    }
    fun updateSearchQuery(query: String) = history.submit("", query)
    fun setHistoryActive(active: Boolean) = history.setActive(active)
    override fun onCleared() { history.close(); super.onCleared() }
}
