package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.search.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** A search owns its SDK store, policy replies and publication until its page/request is replaced. */
internal class SearchMessageHistoryQuery(
    parentScope: CoroutineScope,
    private val createStore: () -> SearchStoreGateway,
    private val searchTypes: List<SearchType>,
    private val pollIntervalMillis: Long = 10_000L,
    private val verificationBudgetMillis: Long = 10_000L,
    private val sourceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val owner = SearchMessageVisibility.currentSessionKey()
    private var active = true
    private var revision = 0L
    private var actionRevision = 0L
    private var publicationRevision = 0L
    private var checkingPublication: Long? = null
    private var query = ""
    private var conversation = ""
    private var store: SearchStoreGateway? = null
    private var request: Job? = null
    private var more: Job? = null
    private val refresh = MutableStateFlow(0L)
    val storeState = MutableStateFlow<SearchState?>(null)
    val results = MutableStateFlow<List<MessageSearchResultItem>>(emptyList())
    val isSearching = MutableStateFlow(false)
    val isLoadingMore = MutableStateFlow(false)
    val error = MutableStateFlow<SearchError?>(null)

    init { require(pollIntervalMillis > 0 && verificationBudgetMillis > 0) }

    private fun current(ticket: Long): Boolean {
        if (!active || ticket != revision || !scope.isActive) return false
        if (owner == SearchMessageVisibility.currentSessionKey()) return true
        // Only this request may conceal its rows. An old reply never mutates a replacement request.
        results.value = emptyList()
        storeState.value = null
        return false
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) stop() else if (query.isNotBlank()) submit(conversation, query, force = true)
    }

    fun submit(conversationID: String, keyword: String, force: Boolean = false) {
        if (owner != SearchMessageVisibility.currentSessionKey()) { stop(); return }
        if (!force && conversationID == conversation && keyword == query &&
            (isSearching.value || results.value.isNotEmpty())) return
        conversation = conversationID
        query = keyword
        stop()
        if (!active || keyword.isBlank() || owner != SearchMessageVisibility.currentSessionKey()) return
        val ticket = revision
        isSearching.value = true
        error.value = null
        request = scope.launch {
            if (!authorizeWithinBudget(conversationID, ticket)) {
                fail(ticket); return@launch
            }
            if (!current(ticket)) return@launch
            val source = createStore()
            store = source
            storeState.value = source.state
            launch {
                combine(source.state.messageResults, SearchMessageVisibility.revision, refresh) { rows, _, _ -> rows }
                    .collectLatest { rows ->
                        val publicationJob = currentCoroutineContext()[Job]
                        val action = actionRevision
                        val publication = ++publicationRevision
                        checkingPublication = publication
                        fun canPublish() = current(ticket) && publicationJob?.isActive == true && action == actionRevision && publication == publicationRevision
                        fun canConceal() = current(ticket) && publication == publicationRevision
                        try {
                            if (!canPublish()) return@collectLatest
                            // The app display mapper takes one scope perconversation, never identity permessage.
                            results.value = SearchMessageVisibility.filterKnown(results.value)
                            val candidates = if (conversationID.isBlank()) rows else rows.filter { it.conversationID == conversationID }
                            verifyBatch(candidates, ::canPublish, ::canConceal)
                        } finally {
                            if (checkingPublication == publication) checkingPublication = null
                        }
                    }
            }
            launch timer@ {
                while (isActive) {
                    delay(pollIntervalMillis)
                    if (!current(ticket)) { if (ticket == revision) stop(); return@timer }
                    if (checkingPublication == null && source.state.messageResults.value.any { it.conversationID.startsWith("group_") }) refresh.value++
                }
            }
            source.search(listOf(keyword), SearchOption(searchScope = searchTypes,
                messageFilter = if (conversationID.isBlank()) null else MessageSearchFilter(conversationID = conversationID)),
                completion(ticket, isSearching))
        }
    }

    fun searchMore() {
        val source = store ?: return
        val ticket = revision
        if (!current(ticket) || isSearching.value || isLoadingMore.value || !source.state.hasMoreMessageResults.value) return
        isLoadingMore.value = true
        error.value = null
        actionRevision++
        more = scope.launch {
            if (!authorizeWithinBudget(conversation, ticket)) {
                fail(ticket); return@launch
            }
            if (current(ticket)) source.searchMore(SearchType.MESSAGE, completion(ticket, isLoadingMore, source.state.messageResults.value))
        }
    }

    private suspend fun authorizeWithinBudget(conversationID: String, ticket: Long): Boolean {
        if (!current(ticket)) return false
        if (!conversationID.startsWith("group_")) return true
        val allowed = withTimeoutOrNull(verificationBudgetMillis) {
            withContext(sourceDispatcher) { SearchGroupHistoryAccess.allows(conversationID, owner) }
        } == true
        return current(ticket) && allowed
    }

    private suspend fun verifyBatch(
        rows: List<MessageSearchResultItem>,
        canPublish: () -> Boolean,
        canConceal: () -> Boolean,
    ) {
        if (rows.none { it.conversationID.startsWith("group_") }) {
            // Independent direct-onlysearch keeps its existing deletion contract and has nogroup deadline/ACL.
            val visible = try { withContext(sourceDispatcher) { SearchMessageVisibility.filter(rows, owner) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            if (canPublish()) results.value = SearchMessageVisibility.filterKnown(visible)
            return
        }
        val units = rows.groupBy { it.conversationID }
        val prior = results.value.groupBy { it.conversationID }
        val settled = mutableMapOf<String, List<MessageSearchResultItem>>()
        fun composed(retainPendingGroups: Boolean): List<MessageSearchResultItem> = units.flatMap { (id, _) ->
            settled[id] ?: if (retainPendingGroups || !id.startsWith("group_")) prior[id].orEmpty() else emptyList()
        }
        try {
            // One wholecooperative budget covers deletion, policy and queueing, not one freshbudget pergroup.
            withTimeoutOrNull(verificationBudgetMillis) {
                coroutineScope {
                    val groupSlots = kotlinx.coroutines.sync.Semaphore(4)
                    val directSlot = kotlinx.coroutines.sync.Semaphore(1)
                    units.map { (id, sourceRows) ->
                        async {
                            val unitJob = currentCoroutineContext()[Job]
                            fun canSettle() = canPublish() && unitJob?.isActive == true
                            val slots = if (id.startsWith("group_")) groupSlots else directSlot
                            slots.acquire()
                            try {
                                if (!canSettle()) return@async
                                val checked = try {
                                    val deleted = withContext(sourceDispatcher) { SearchMessageVisibility.filterHistoryUnit(sourceRows, owner) }
                                    if (!canSettle()) return@async
                                    val allowed = withContext(sourceDispatcher) { SearchGroupHistoryAccess.allows(id, owner) }
                                    if (!canSettle()) return@async
                                    if (allowed) deleted else emptyList()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                  catch (_: Exception) { emptyList() }
                                if (!canSettle()) return@async
                                // Filter only this completedunit; recomposing does not repeatidentity reads over allrows.
                                settled[id] = SearchMessageVisibility.filterKnown(checked)
                                results.value = composed(retainPendingGroups = true)
                            } finally { slots.release() }
                        }
                    }.awaitAll()
                }
            }
            if (canPublish()) {
                results.value = SearchMessageVisibility.filterKnown(composed(retainPendingGroups = false))
                val failed = try { units.keys.any { it !in settled } || withContext(sourceDispatcher) { SearchMessageVisibility.batchVerificationFailed(rows) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { true }
                if (canPublish()) SearchMessageVisibility.publishVerificationFailure(failed)
            }
        } finally {
            // Cancellation/deadline neverretains unchecked oldgroup rows. Anoldquery/owner cannot mask anewrequest.
            if (canConceal()) {
                val unresolved = units.keys.filter { it.startsWith("group_") && it !in settled }.toSet()
                results.value = results.value.filterNot { it.conversationID in unresolved }
            }
        }
    }

    private fun completion(ticket: Long, loading: MutableStateFlow<Boolean>, unchangedRows: List<MessageSearchResultItem>? = null) = object : CompletionHandler {
        override fun onSuccess() {
            scope.launch {
                if (current(ticket)) {
                    loading.value = false
                    if (unchangedRows != null && unchangedRows == store?.state?.messageResults?.value) refresh.value++
                }
            }
        }
        override fun onFailure(code: Int, desc: String) {
            scope.launch {
                if (current(ticket)) { loading.value = false; error.value = SearchError(code, desc) }
            }
        }
    }

    private fun fail(ticket: Long) {
        if (!current(ticket)) return
        actionRevision++
        results.value = emptyList()
        isSearching.value = false
        isLoadingMore.value = false
        error.value = SearchError(-1, "")
    }

    private fun stop() {
        revision++
        checkingPublication = null
        request?.cancel(); request = null
        more?.cancel(); more = null
        store = null
        storeState.value = null
        results.value = emptyList()
        isSearching.value = false
        isLoadingMore.value = false
        error.value = null
    }

    fun close() { active = false; stop(); scope.cancel() }
}
