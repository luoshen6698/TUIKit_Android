package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SearchHistoryAuthorizationViewModelTest {
    private lateinit var scope: CoroutineScope
    @Before fun prepare() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        SearchMessageVisibility.setDisplayProvider(null)
        SearchMessageVisibility.publishVerificationFailure(false)
        SearchMessageVisibility.setSessionProvider { "app:account:generation" }
        SearchMessageVisibility.setProvider { _, rows -> rows }
        SearchMessageVisibility.setVerificationFailureProvider { false }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        SearchGroupHistoryAccess.setProvider { false }
    }
    @After fun restore() {
        scope.cancel()
        SearchMessageVisibility.setDisplayProvider(null)
        SearchGroupHistoryAccess.setProvider(null)
        SearchMessageVisibility.setSessionProvider { null }
    }
    private fun row(conversation: String) = MessageSearchResultItem(conversationID = conversation,
        messageCount = 1, messageList = listOf(MessageInfo(msgID = conversation)))

    @Test fun `All removes denied group history while preserving direct messages and directory categories`() {
        val store = FakeStore(listOf(row("group_denied"), row("c2c_friend")))
        val vm = SearchAllViewModel(store, scope)
        vm.updateSearchQuery("synthetic")
        val message = vm.searchCategories.value.single { it.type == SearchType.MESSAGE }
        assertEquals(listOf("c2c_friend"), message.results.filterIsInstance<MessageSearchResultItem>().map { it.conversationID })
        assertEquals(1, vm.searchCategories.value.single { it.type == SearchType.FRIEND }.results.size)
        assertEquals(1, vm.searchCategories.value.single { it.type == SearchType.GROUP }.results.size)
    }
    @Test fun `Messages denies group history without changing direct messages`() {
        val store = FakeStore(listOf(row("group_denied"), row("c2c_friend")))
        val vm = SearchMessageViewModel(store, scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(listOf("c2c_friend"), vm.messageSearchResults.value.map { it.conversationID })
    }
    @Test fun `Dedicated denied query never invokes SDK and cannot publish cached group rows`() {
        val store = FakeStore(listOf(row("group_denied")))
        val vm = SearchMessageInConversationViewModel(store, scope)
        vm.updateSearchQuery("group_denied", "synthetic")
        assertEquals(0, store.searchCalls)
        assertTrue(vm.messagesInConversation.value.isEmpty())
    }

    @Test fun `history is checked once per group batch and an unavailable group does not hide allowed groups or direct messages`() {
        val calls = mutableListOf<String>()
        SearchGroupHistoryAccess.setProvider { group -> calls += group; if (group == "group_error") error("synthetic unavailable"); group == "group_allowed" }
        val store = FakeStore(listOf(row("group_allowed"), row("group_allowed").copy(messageCount = 2), row("group_error"), row("group_denied"), row("c2c_friend")))
        val vm = SearchMessageViewModel(store, scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(listOf("group_allowed", "group_allowed", "c2c_friend"), vm.messageSearchResults.value.map { it.conversationID })
        assertEquals(listOf("group_allowed", "group_error", "group_denied"), calls)
    }
    @Test fun `missing provider fails closed only for group history`() {
        SearchGroupHistoryAccess.setProvider(null)
        val vm = SearchMessageViewModel(FakeStore(listOf(row("group_denied"), row("c2c_friend"))), scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(listOf("c2c_friend"), vm.messageSearchResults.value.map { it.conversationID })
    }
    @Test fun `allow before dedicated SDK query cannot authorize publication after withdrawal`() {
        var reads = 0
        SearchGroupHistoryAccess.setProvider { ++reads == 1 }
        val store = FakeStore(listOf(row("group_target")))
        val vm = SearchMessageInConversationViewModel(store, scope)
        vm.updateSearchQuery("group_target", "synthetic")
        assertEquals(1, store.searchCalls)
        assertEquals(2, reads)
        assertTrue(vm.messagesInConversation.value.isEmpty())
    }
    @Test fun `dedicated more freshly denies before SDK call and clears previously authorized rows`() {
        var allowed = true
        SearchGroupHistoryAccess.setProvider { allowed }
        val store = FakeStore(listOf(row("group_target")))
        val vm = SearchMessageInConversationViewModel(store, scope)
        vm.updateSearchQuery("group_target", "synthetic")
        assertEquals(1, vm.messagesInConversation.value.size)
        allowed = false
        vm.searchMore()
        assertEquals(0, store.moreCalls)
        assertTrue(vm.messagesInConversation.value.isEmpty())
    }
    @Test fun `policy revision refresh removes previously visible group and keeps direct history`() {
        var allowed = true
        SearchGroupHistoryAccess.setProvider { allowed }
        val vm = SearchMessageViewModel(FakeStore(listOf(row("group_target"), row("c2c_friend"))), scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(2, vm.messageSearchResults.value.size)
        allowed = false
        SearchMessageVisibility.invalidate()
        assertEquals(listOf("c2c_friend"), vm.messageSearchResults.value.map { it.conversationID })
    }
    @Test fun `late history allow cannot publish after app account or authentication instance replacement`() {
        for (replacement in listOf("other-app:account:generation", "app:other-account:generation", "app:account:new-generation")) {
            var session = "app:account:generation"
            SearchMessageVisibility.setSessionProvider { session }
            val allow = CompletableDeferred<Boolean>()
            SearchGroupHistoryAccess.setProvider { allow.await() }
            val vm = SearchMessageViewModel(FakeStore(listOf(row("group_target"))), scope)
            vm.updateSearchQuery("synthetic")
            session = replacement
            allow.complete(true)
            assertTrue(vm.messageSearchResults.value.isEmpty())
        }
    }
    @Test fun `replaced query late allow never starts old SDK query or clears new results`() {
        val oldAllow = CompletableDeferred<Boolean>()
        var first = true
        SearchGroupHistoryAccess.setProvider { if (first) { first = false; withContext(NonCancellable) { oldAllow.await() } } else true }
        val newStore = FakeStore(listOf(row("group_new")))
        var created = 0
        // The old precheck is suspended before any Store is created; only the current request may create one.
        val vm = SearchMessageInConversationViewModel({ created++; newStore }, scope, sourceDispatcher = Dispatchers.Unconfined)
        vm.updateSearchQuery("group_old", "old")
        vm.updateSearchQuery("group_new", "new")
        oldAllow.complete(true)
        assertEquals(1, created)
        assertEquals(1, newStore.searchCalls)
        assertEquals(listOf("group_new"), vm.messagesInConversation.value.map { it.msgID })
    }
    @Test fun `late old SDK rows and completions cannot overwrite replacement query`() {
        SearchGroupHistoryAccess.setProvider { true }
        val oldStore = FakeStore(listOf(row("group_old")), autoPublish = false)
        val newStore = FakeStore(listOf(row("group_new")))
        val stores = ArrayDeque(listOf(oldStore, newStore))
        val vm = SearchMessageViewModel({ stores.removeFirst() }, scope, sourceDispatcher = Dispatchers.Unconfined)
        vm.updateSearchQuery("old")
        vm.updateSearchQuery("new")
        oldStore.publish()
        assertEquals(listOf("group_new"), vm.messageSearchResults.value.map { it.conversationID })
        assertFalse(vm.isSearching.value)
    }
    @Test fun `pause discards pending allow and resume starts a new scoped request`() {
        val allow = CompletableDeferred<Boolean>()
        var first = true
        SearchGroupHistoryAccess.setProvider { if (first) { first = false; allow.await() } else true }
        val store = FakeStore(listOf(row("group_target")))
        val vm = SearchMessageInConversationViewModel(store, scope)
        vm.updateSearchQuery("group_target", "synthetic")
        vm.setHistoryActive(false)
        allow.complete(true)
        assertEquals(0, store.searchCalls)
        assertTrue(vm.messagesInConversation.value.isEmpty())
        vm.setHistoryActive(true)
        assertEquals(1, store.searchCalls)
        assertEquals(1, vm.messagesInConversation.value.size)
    }
    @Test fun `pause rejects SDK result and blank query cannot revive a retained SDK snapshot`() {
        SearchGroupHistoryAccess.setProvider { true }
        val store = FakeStore(listOf(row("group_target")), autoPublish = false)
        val vm = SearchMessageViewModel(store, scope)
        vm.updateSearchQuery("synthetic")
        vm.setHistoryActive(false)
        store.publish()
        assertTrue(vm.messageSearchResults.value.isEmpty())
        vm.setHistoryActive(true)
        vm.updateSearchQuery("")
        store.messages.value = listOf(row("group_late"))
        assertTrue(vm.messageSearchResults.value.isEmpty())
    }
    @Test fun `history authorization follows deletion and rejects context lost during deletion verification`() {
        var session = "app:account:generation"
        SearchMessageVisibility.setSessionProvider { session }
        var calls = 0
        SearchGroupHistoryAccess.setProvider { calls++; true }
        SearchMessageVisibility.setProvider { _, rows -> session = "app:account:replaced"; rows }
        val vm = SearchMessageViewModel(FakeStore(listOf(row("group_target"))), scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(0, calls)
        assertTrue(vm.messageSearchResults.value.isEmpty())
    }
    @Test fun `deletion hint removes displayed rows before awaited revalidation finishes`() {
        SearchGroupHistoryAccess.setProvider { true }
        val vm = SearchMessageViewModel(FakeStore(listOf(row("group_target"))), scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(1, vm.messageSearchResults.value.size)
        val verification = CompletableDeferred<Unit>()
        SearchMessageVisibility.setProvider { _, rows -> verification.await(); rows }
        SearchMessageVisibility.setDeletedProvider { _, _ -> true }
        SearchMessageVisibility.invalidate()
        assertTrue(vm.messageSearchResults.value.isEmpty())
        verification.complete(Unit)
        assertTrue(vm.messageSearchResults.value.isEmpty())
    }

    @Test fun `late publication allow cannot reopen after a newer more denial in the same query`() {
        val pendingPublication = CompletableDeferred<Boolean>()
        var reads = 0
        SearchGroupHistoryAccess.setProvider {
            when (++reads) {
                1 -> true
                2 -> withContext(NonCancellable) { pendingPublication.await() }
                else -> false
            }
        }
        val store = FakeStore(listOf(row("group_target")))
        val vm = SearchMessageInConversationViewModel(store, scope)
        vm.updateSearchQuery("group_target", "synthetic")
        vm.searchMore()
        assertEquals(0, store.moreCalls)
        pendingPublication.complete(true)
        assertTrue(vm.messagesInConversation.value.isEmpty())
    }

    @Test fun `a canceled same-query result batch cannot publish its late allow`() {
        val oldAllow = CompletableDeferred<Boolean>()
        var first = true
        SearchGroupHistoryAccess.setProvider { if (first) { first = false; withContext(NonCancellable) { oldAllow.await() } } else false }
        val store = FakeStore(listOf(row("group_old")))
        val vm = SearchMessageViewModel(store, scope)
        vm.updateSearchQuery("synthetic")
        store.messages.value = listOf(row("group_new"))
        oldAllow.complete(true)
        assertTrue(vm.messageSearchResults.value.isEmpty())
    }
    @Test fun `same query resubmission after owner replacement conceals immediately rather than taking duplicate shortcut`() {
        var session = "app:account:generation"
        SearchMessageVisibility.setSessionProvider { session }
        SearchGroupHistoryAccess.setProvider { true }
        val vm = SearchMessageViewModel(FakeStore(listOf(row("group_target"))), scope)
        vm.updateSearchQuery("synthetic")
        assertEquals(1, vm.messageSearchResults.value.size)
        session = "app:other-account:generation"
        vm.updateSearchQuery("synthetic")
        assertTrue(vm.messageSearchResults.value.isEmpty())
    }

    @Test fun `SDK completion reads owner and updates loading only on the ViewModel dispatcher`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { work -> Thread(work, "history-vm-owner") }.asCoroutineDispatcher()
        val ownedScope = CoroutineScope(SupervisorJob() + dispatcher)
        val observed = java.util.concurrent.ConcurrentLinkedQueue<String>()
        SearchMessageVisibility.setSessionProvider { observed += Thread.currentThread().name; "app:account:generation" }
        try {
            val store = FakeStore(listOf(row("c2c_friend")), autoPublish = false)
            lateinit var vm: SearchMessageViewModel
            withContext(dispatcher) { vm = SearchMessageViewModel(store, ownedScope); vm.updateSearchQuery("synthetic") }
            withContext(dispatcher) { }
            val callback = Thread({ store.complete() }, "synthetic-sdk-callback")
            callback.start(); callback.join()
            withContext(dispatcher) { }
            assertFalse("No owner/ticket read on an arbitrary SDK callback thread", observed.contains("synthetic-sdk-callback"))
            assertFalse(vm.isSearching.value)
        } finally { ownedScope.cancel(); dispatcher.close() }
    }
    @Test fun `nil app owner keeps existing direct-only SDK compatibility but never authorizes group history`() {
        SearchMessageVisibility.setSessionProvider { null }
        val direct = FakeStore(listOf(row("c2c_friend")))
        val directVM = SearchMessageViewModel(direct, scope)
        directVM.updateSearchQuery("synthetic")
        assertEquals(1, direct.searchCalls)
        assertEquals(listOf("c2c_friend"), directVM.messageSearchResults.value.map { it.conversationID })
        val group = FakeStore(listOf(row("group_target")))
        val groupVM = SearchMessageInConversationViewModel(group, scope)
        groupVM.updateSearchQuery("group_target", "synthetic")
        assertEquals(0, group.searchCalls)
        assertTrue(groupVM.messagesInConversation.value.isEmpty())
    }

    @Test fun `periodic stalled group eventually closes after multiple ticks while direct directory and allowed groups remain`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-stall-owner") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            withContext(dispatcher) {
                var stall = false
                val started = CompletableDeferred<Unit>()
                SearchGroupHistoryAccess.setProvider { group ->
                    if (group == "group_stalled" && stall) { started.complete(Unit); awaitCancellation() }
                    true
                }
                val store = FakeStore(listOf(row("group_stalled"), row("group_allowed"), row("c2c_friend")))
                val vm = SearchAllViewModel({ store }, owned, pollIntervalMillis = 20, verificationBudgetMillis = 100)
                vm.updateSearchQuery("synthetic")
                withTimeout(1_000) { vm.searchCategories.first { cats -> cats.any { it.type == SearchType.MESSAGE && it.results.size == 3 } } }
                stall = true
                withTimeout(1_000) { started.await() }
                delay(45) // At least two20ms periodic opportunities while the authority remains suspended.
                withTimeout(350) { vm.searchCategories.first { cats ->
                    cats.singleOrNull { it.type == SearchType.MESSAGE }?.results?.filterIsInstance<MessageSearchResultItem>()?.map { it.conversationID } == listOf("group_allowed", "c2c_friend")
                } }
                assertEquals(1, vm.searchCategories.value.single { it.type == SearchType.FRIEND }.results.size)
                assertEquals(1, vm.searchCategories.value.single { it.type == SearchType.GROUP }.results.size)
            }
        } finally { owned.cancel(); dispatcher.close() }
    }

    @Test fun `old stalled timeout cannot conceal a successfully replaced query`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-new-query") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            var stall = false
            val started = CompletableDeferred<Unit>()
            SearchGroupHistoryAccess.setProvider { group ->
                if (group == "group_old" && stall) {
                    started.complete(Unit)
                    withContext(NonCancellable) { delay(220); true }
                } else true
            }
            val stores = ArrayDeque(listOf(FakeStore(listOf(row("group_old"))), FakeStore(listOf(row("group_new")))))
            val vm = SearchMessageViewModel({ stores.removeFirst() }, owned, pollIntervalMillis = 20, verificationBudgetMillis = 100)
            vm.updateSearchQuery("old")
            withTimeout(1_000) { vm.messageSearchResults.first { it.any { r -> r.conversationID == "group_old" } } }
            stall = true
            withTimeout(1_000) { started.await() }
            vm.updateSearchQuery("new")
            withTimeout(1_000) { vm.messageSearchResults.first { it.map { r -> r.conversationID } == listOf("group_new") } }
            delay(260)
            assertEquals(listOf("group_new"), vm.messageSearchResults.value.map { it.conversationID })
        } } finally { owned.cancel(); dispatcher.close() }
    }
    @Test fun `old owner timeout cannot mask the new account generation results`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-new-owner") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            var session = "app:account:generation"
            SearchMessageVisibility.setSessionProvider { session }
            var stall = false
            val started = CompletableDeferred<Unit>()
            SearchGroupHistoryAccess.setProvider { group ->
                if (group == "group_old" && stall) { started.complete(Unit); withContext(NonCancellable) { delay(220); true } } else true
            }
            val oldVM = SearchMessageViewModel({ FakeStore(listOf(row("group_old"))) }, owned, pollIntervalMillis = 20, verificationBudgetMillis = 100)
            oldVM.updateSearchQuery("synthetic")
            withTimeout(1_000) { oldVM.messageSearchResults.first { it.isNotEmpty() } }
            stall = true
            withTimeout(1_000) { started.await() }
            session = "another-app:another-account:new-generation"
            val newVM = SearchMessageViewModel({ FakeStore(listOf(row("group_new"), row("c2c_friend"))) }, owned, pollIntervalMillis = 20, verificationBudgetMillis = 100)
            newVM.updateSearchQuery("synthetic")
            withTimeout(1_000) { newVM.messageSearchResults.first { it.size == 2 } }
            delay(260)
            assertTrue(oldVM.messageSearchResults.value.isEmpty())
            assertEquals(listOf("group_new", "c2c_friend"), newVM.messageSearchResults.value.map { it.conversationID })
        } } finally { owned.cancel(); dispatcher.close() }
    }
    @Test fun `stalled dedicated more cancels its authority and conceals rather than issuing SDK more`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-more-budget") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            var stall = false
            var canceled = 0
            SearchGroupHistoryAccess.setProvider {
                if (stall) try { awaitCancellation() } finally { canceled++ }
                true
            }
            val store = FakeStore(listOf(row("group_target")))
            val vm = SearchMessageInConversationViewModel({ store }, owned, pollIntervalMillis = 1_000, verificationBudgetMillis = 70)
            vm.updateSearchQuery("group_target", "synthetic")
            withTimeout(1_000) { vm.messagesInConversation.first { it.isNotEmpty() } }
            stall = true
            vm.searchMore()
            withTimeout(500) { vm.messagesInConversation.first { it.isEmpty() } }
            assertEquals(0, store.moreCalls)
            assertTrue(canceled >= 1)
            assertFalse(vm.isLoadingMore.value)
            stall = false
            vm.updateSearchQuery("group_target", "retry")
            withTimeout(1_000) { vm.messagesInConversation.first { it.isNotEmpty() } }
            delay(100)
            assertEquals(1, vm.messagesInConversation.value.size)
        } } finally { owned.cancel(); dispatcher.close() }
    }
    @Test fun `verification uses IO and known display filtering is constant per conversation not per message`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-display-owner") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            SearchGroupHistoryAccess.setProvider { true }
            val observedThreads = java.util.concurrent.ConcurrentLinkedQueue<String>()
            SearchMessageVisibility.setProvider { _, rows -> observedThreads += Thread.currentThread().name; rows }
            var displayCalls = 0
            SearchMessageVisibility.setDisplayProvider { _, rows, mapper -> displayCalls++; mapper(rows) }
            suspend fun counts(size: Int): Int {
                displayCalls = 0
                val result = row("group_target").copy(messageCount = size, messageList = (1..size).map { MessageInfo(msgID = "synthetic-$it") })
                val vm = SearchMessageViewModel({ FakeStore(listOf(result)) }, owned)
                vm.updateSearchQuery("synthetic")
                withTimeout(1_000) { vm.messageSearchResults.first { it.singleOrNull()?.messageList?.size == size } }
                // Finish the batch's final display check before measuring.
                delay(10)
                return displayCalls
            }
            val small = counts(1)
            val large = counts(200)
            assertEquals(small, large)
            assertTrue(large in 1..3)
            assertTrue(observedThreads.isNotEmpty())
            assertTrue(observedThreads.none { it == "history-display-owner" })
        } } finally { owned.cancel(); dispatcher.close() }
    }
    @Test fun `deletion retry aggregates failed groups even when a healthy group settles last then clears after success`() {
        var failed = true
        SearchMessageVisibility.setVerificationFailureProvider { failed && it == "group_failed" }
        SearchGroupHistoryAccess.setProvider { true }
        val vm = SearchMessageViewModel(FakeStore(listOf(row("group_failed"), row("group_healthy"))), scope)
        vm.updateSearchQuery("synthetic")
        assertTrue(SearchMessageVisibility.searchVerificationFailed.value)
        failed = false
        SearchMessageVisibility.invalidate()
        assertFalse(SearchMessageVisibility.searchVerificationFailed.value)
        assertEquals(2, vm.messageSearchResults.value.size)
    }

    @Test fun `mixed direct deletion stall cannot prevent group withdrawal or restoration`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-direct-stall") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            var directStalled = false
            var groupAllowed = true
            val entered = CompletableDeferred<Unit>()
            SearchMessageVisibility.setProvider { id, rows ->
                if (id == "c2c_friend" && directStalled) { entered.complete(Unit); awaitCancellation() }
                rows
            }
            SearchGroupHistoryAccess.setProvider { groupAllowed }
            val vm = SearchMessageViewModel({ FakeStore(listOf(row("group_target"), row("c2c_friend"))) }, owned,
                pollIntervalMillis = 20, verificationBudgetMillis = 100)
            vm.updateSearchQuery("synthetic")
            withTimeout(1_000) { vm.messageSearchResults.first { it.size == 2 } }
            directStalled = true
            SearchMessageVisibility.invalidate()
            withTimeout(1_000) { entered.await() }
            groupAllowed = false
            SearchMessageVisibility.invalidate()
            withTimeout(500) { vm.messageSearchResults.first { it.map { r -> r.conversationID } == listOf("c2c_friend") } }
            groupAllowed = true
            SearchMessageVisibility.invalidate()
            withTimeout(500) { vm.messageSearchResults.first { it.map { r -> r.conversationID } == listOf("group_target", "c2c_friend") } }
            assertTrue(directStalled)
            groupAllowed = false
            SearchMessageVisibility.invalidate()
            withTimeout(500) { vm.messageSearchResults.first { it.map { r -> r.conversationID } == listOf("c2c_friend") } }
            directStalled = false
            SearchMessageVisibility.invalidate()
            delay(130)
            assertEquals(listOf("c2c_friend"), vm.messageSearchResults.value.map { it.conversationID })
        } } finally { owned.cancel(); dispatcher.close() }
    }
    @Test fun `timeout deletion recheck exposes retry without a repository failure flag then clears after success`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-timeout-retry") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            var stall = false
            SearchMessageVisibility.setProvider { _, rows -> if (stall) awaitCancellation(); rows }
            SearchMessageVisibility.setVerificationFailureProvider { false }
            SearchGroupHistoryAccess.setProvider { true }
            val vm = SearchMessageViewModel({ FakeStore(listOf(row("group_target"))) }, owned,
                pollIntervalMillis = 20, verificationBudgetMillis = 100)
            vm.updateSearchQuery("synthetic")
            withTimeout(1_000) { vm.messageSearchResults.first { it.isNotEmpty() } }
            stall = true
            SearchMessageVisibility.invalidate()
            withTimeout(500) { vm.messageSearchResults.first { it.isEmpty() } }
            withTimeout(350) { SearchMessageVisibility.searchVerificationFailed.first { it } }
            stall = false
            SearchMessageVisibility.invalidate()
            withTimeout(1_000) { vm.messageSearchResults.first { it.isNotEmpty() } }
            withTimeout(1_000) { SearchMessageVisibility.searchVerificationFailed.first { !it } }
            assertFalse(SearchMessageVisibility.searchVerificationFailed.value)
        } } finally { owned.cancel(); dispatcher.close() }
    }
    @Test fun `old query timeout cannot clear a newer unknown batch retry flag`() = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "history-retry-owner") }.asCoroutineDispatcher()
        val owned = CoroutineScope(SupervisorJob() + dispatcher)
        try { withContext(dispatcher) {
            var oldStalled = false
            var newStalled = true
            val oldEntered = CompletableDeferred<Unit>()
            SearchMessageVisibility.setVerificationFailureProvider { false }
            SearchMessageVisibility.setProvider { id, rows ->
                if (id == "group_old" && oldStalled) { oldEntered.complete(Unit); withContext(NonCancellable) { delay(240) } }
                if (id == "group_new" && newStalled) awaitCancellation()
                rows
            }
            SearchGroupHistoryAccess.setProvider { true }
            val stores = ArrayDeque(listOf(FakeStore(listOf(row("group_old"))), FakeStore(listOf(row("group_new")))))
            val vm = SearchMessageViewModel({ stores.removeFirst() }, owned, pollIntervalMillis = 20, verificationBudgetMillis = 80)
            vm.updateSearchQuery("old")
            withTimeout(1_000) { vm.messageSearchResults.first { it.isNotEmpty() } }
            oldStalled = true
            SearchMessageVisibility.invalidate()
            withTimeout(1_000) { oldEntered.await() }
            vm.updateSearchQuery("new")
            withTimeout(500) { SearchMessageVisibility.searchVerificationFailed.first { it } }
            delay(270)
            assertTrue(SearchMessageVisibility.searchVerificationFailed.value)
            assertTrue(vm.messageSearchResults.value.isEmpty())
            newStalled = false
            SearchMessageVisibility.invalidate()
            withTimeout(1_000) { vm.messageSearchResults.first { it.map { r -> r.conversationID } == listOf("group_new") } }
            withTimeout(1_000) { SearchMessageVisibility.searchVerificationFailed.first { !it } }
            assertFalse(SearchMessageVisibility.searchVerificationFailed.value)
        } } finally { owned.cancel(); dispatcher.close() }
    }

    @Test fun `empty group identity and provider replacement cannot authorize retained history`() = runBlocking {
        var calls = 0
        SearchGroupHistoryAccess.setProvider { calls++; true }
        assertFalse(SearchGroupHistoryAccess.allows("group_", "app:account:generation"))
        assertEquals(0, calls)
        SearchGroupHistoryAccess.setProvider { SearchGroupHistoryAccess.setProvider { true }; true }
        assertFalse(SearchGroupHistoryAccess.allows("group_target", "app:account:generation"))
    }

    private class FakeStore(private val rows: List<MessageSearchResultItem>, private val autoPublish: Boolean = true) : SearchStoreGateway {
        val messages = MutableStateFlow<List<MessageSearchResultItem>>(emptyList())
        var searchCalls = 0
        var moreCalls = 0
        private var completion: CompletionHandler? = null
        fun publish() { messages.value = rows; complete() }
        fun complete() { completion?.onSuccess() }
        override val state = SearchState(
            MutableStateFlow(emptyList()), MutableStateFlow(0), MutableStateFlow(false),
            MutableStateFlow(listOf(FriendSearchInfo(userID = "friend"))), MutableStateFlow(1), MutableStateFlow(false),
            MutableStateFlow(listOf(GroupSearchInfo(groupID = "directory"))), MutableStateFlow(1), MutableStateFlow(false),
            MutableStateFlow(emptyMap()), MutableStateFlow(0), MutableStateFlow(false),
            messages, MutableStateFlow(rows.size), MutableStateFlow(true))
        override fun search(keywordList: List<String>, option: SearchOption, completion: CompletionHandler?) {
            searchCalls++
            this.completion = completion
            if (autoPublish) publish()
        }
        override fun searchMore(searchType: SearchType, completion: CompletionHandler?) { moreCalls++; completion?.onSuccess() }
    }
}
