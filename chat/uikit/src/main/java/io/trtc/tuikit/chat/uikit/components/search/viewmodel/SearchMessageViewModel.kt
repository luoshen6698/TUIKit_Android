package io.trtc.tuikit.chat.uikit.components.search.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import io.trtc.tuikit.atomicxcore.api.search.SearchOption
import io.trtc.tuikit.atomicxcore.api.search.SearchState
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SearchMessageViewModel internal constructor(
    private val searchStore: SearchStoreGateway
) : ViewModel() {

    constructor() : this(DefaultSearchStoreGateway())

    private val state: SearchState = searchStore.state
    private var lastSubmittedQuery: String = ""
    private val ownerSession = SearchMessageVisibility.currentSessionKey()
    private var messageQuerySession: String? = null

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _searchError = MutableStateFlow<SearchError?>(null)
    val searchError: StateFlow<SearchError?> = _searchError

    private val _messageSearchResults = MutableStateFlow<List<MessageSearchResultItem>>(emptyList())
    val messageSearchResults: StateFlow<List<MessageSearchResultItem>> = _messageSearchResults

    init {
        viewModelScope.launch {
            SearchMessageVisibility.revision.collectLatest {
                _messageSearchResults.value = if (ownerSession == SearchMessageVisibility.currentSessionKey())
                    SearchMessageVisibility.filterKnown(_messageSearchResults.value) else emptyList()
            }
        }
        viewModelScope.launch {
            combine(state.messageResults, SearchMessageVisibility.revision) { results, _ ->
                SearchMessageVisibility.filter(results, messageQuerySession)
            }.collectLatest {
                _messageSearchResults.value = if (ownerSession == SearchMessageVisibility.currentSessionKey()) it else emptyList()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        if (ownerSession != SearchMessageVisibility.currentSessionKey()) { clearSearch(); return }
        if (query.isBlank()) {
            clearSearch()
            return
        }
        if (query == lastSubmittedQuery && (_isSearching.value || _messageSearchResults.value.isNotEmpty())) return

        lastSubmittedQuery = query
        messageQuerySession = SearchMessageVisibility.currentSessionKey()
        _isSearching.value = true
        _searchError.value = null
        searchStore.search(
            listOf(query),
            SearchOption(searchScope = listOf(SearchType.MESSAGE)),
            searchCompletion(_isSearching, _searchError)
        )
    }

    fun searchMore() {
        if (ownerSession != SearchMessageVisibility.currentSessionKey()) { clearSearch(); return }
        if (_isSearching.value || _isLoadingMore.value || !state.hasMoreMessageResults.value) return

        _isLoadingMore.value = true
        _searchError.value = null
        searchStore.searchMore(SearchType.MESSAGE, searchCompletion(_isLoadingMore, _searchError))
    }

    private fun clearSearch() {
        messageQuerySession = null
        lastSubmittedQuery = ""
        _isSearching.value = false
        _isLoadingMore.value = false
        _searchError.value = null
        _messageSearchResults.value = emptyList()
    }
}
