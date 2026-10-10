package io.trtc.tuikit.chat.demo.main

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class XingDunReadAllCoordinatorTest {
    private class Fixture {
        var account: String? = "test-account"
        var marked = listOf("c2c_friend", "group_test")
        val calls = mutableListOf<String>()
        val batches = mutableListOf<List<String>>()
        var failureAt: String? = null
        var clearGate: CompletableDeferred<Unit>? = null
        var changeAccountAfterLookup = false
        val coordinator = XingDunReadAllCoordinator(
            currentAccount = { account },
            findMarkedUnread = {
                calls += "lookup"
                if (failureAt == "lookup") error("lookup failure")
                if (changeAccountAfterLookup) account = "different-account"
                marked
            },
            clearUnread = {
                calls += "clear"
                clearGate?.await()
                if (failureAt == "clear") error("SDK failure")
            },
            clearMarks = {
                calls += "unmark"
                batches += it
                if (failureAt == "unmark") error("partial batch failure")
            },
        )
    }

    @Test fun clearsCountsAndExplicitUnreadMarks() = runBlocking {
        val f = Fixture()
        assertTrue(f.coordinator.execute())
        assertEquals(listOf("lookup", "clear", "unmark"), f.calls)
        assertEquals(listOf(listOf("c2c_friend", "group_test")), f.batches)
        assertFalse(f.coordinator.isRunning)
    }

    @Test fun noMarkedConversationsStillClearsAllMessageCounts() = runBlocking {
        val f = Fixture().apply { marked = emptyList() }
        assertTrue(f.coordinator.execute())
        assertEquals(listOf("lookup", "clear"), f.calls)
    }

    @Test fun doesNotReportSuccessBeforeSdkCompletionAndIgnoresRepeatedClicks() = runBlocking {
        val f = Fixture().apply { clearGate = CompletableDeferred() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { f.coordinator.execute() }
        assertFalse(first.isCompleted)
        assertTrue(f.coordinator.isRunning)
        assertFalse(f.coordinator.execute())
        assertEquals(1, f.calls.count { it == "clear" })
        f.clearGate!!.complete(Unit)
        assertTrue(first.await())
    }

    @Test fun lookupFailureDoesNotClearAnyCounts() = runBlocking {
        val f = Fixture().apply { failureAt = "lookup" }
        expectFailure { f.coordinator.execute() }
        assertEquals(listOf("lookup"), f.calls)
        assertFalse(f.coordinator.isRunning)
    }

    @Test fun sdkClearFailureDoesNotUnmarkAndAllowsRetry() = runBlocking {
        val f = Fixture().apply { failureAt = "clear" }
        expectFailure { f.coordinator.execute() }
        assertTrue(f.batches.isEmpty())
        assertFalse(f.coordinator.isRunning)
        f.failureAt = null
        assertTrue(f.coordinator.execute())
    }

    @Test fun partialMarkFailureIsNotReportedAsSuccess() = runBlocking {
        val f = Fixture().apply { failureAt = "unmark" }
        expectFailure { f.coordinator.execute() }
        assertFalse(f.coordinator.isRunning)
        f.failureAt = null
        assertTrue(f.coordinator.execute())
    }

    @Test fun allPagesAreBatchedIncludingItemsNotVisibleInUi() = runBlocking {
        val f = Fixture().apply { marked = (1..205).map { "c2c_$it" } }
        assertTrue(f.coordinator.execute())
        assertEquals(listOf(100, 100, 5), f.batches.map { it.size })
        assertEquals(f.marked, f.batches.flatten())
    }

    @Test fun duplicateAndEmptyIdsAreNotSubmitted() = runBlocking {
        val f = Fixture().apply { marked = listOf("c2c_friend", "", " ", "c2c_friend") }
        assertTrue(f.coordinator.execute())
        assertEquals(listOf(listOf("c2c_friend")), f.batches)
    }

    @Test fun accountSwitchDuringLookupStopsBeforeMutation() = runBlocking {
        val f = Fixture().apply { changeAccountAfterLookup = true }
        expectFailure { f.coordinator.execute() }
        assertEquals(listOf("lookup"), f.calls)
    }

    @Test fun loggedOutStateNeverCallsSdk() = runBlocking {
        val f = Fixture().apply { account = "" }
        expectFailure { f.coordinator.execute() }
        assertTrue(f.calls.isEmpty())
    }

    @Test fun leavingPageCancelsFollowupOperationsAndReleasesGuard() = runBlocking {
        val f = Fixture().apply { clearGate = CompletableDeferred() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.coordinator.execute() }
        job.cancelAndJoin()
        assertTrue(f.batches.isEmpty())
        assertFalse(f.coordinator.isRunning)
        f.clearGate!!.complete(Unit)
        assertTrue(f.coordinator.execute())
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Should not report success on failure")
        } catch (_: IllegalStateException) {
            // Expected SDK/account failure.
        }
    }
}
