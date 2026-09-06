package io.trtc.tuikit.chat.demo.xingdun.main

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunStartupPreloadGateTest {

    @Test
    fun successfulGroupLoadReleasesStartupImmediately() = runBlocking {
        var finishedSuccessfully = false

        val outcome = XingDunStartupPreloadGate(1_500L).awaitJoinedGroups(
            startLoad = { it.onSuccess() },
            onFinished = { finishedSuccessfully = it },
        )

        assertEquals(XingDunStartupPreloadOutcome.SUCCEEDED, outcome)
        assertTrue(finishedSuccessfully)
    }

    @Test
    fun failedGroupLoadDoesNotWaitForTimeout() = runBlocking {
        var finishedSuccessfully = true

        val outcome = XingDunStartupPreloadGate(1_500L).awaitJoinedGroups(
            startLoad = { it.onFailure(1, "failed") },
            onFinished = { finishedSuccessfully = it },
        )

        assertEquals(XingDunStartupPreloadOutcome.FAILED, outcome)
        assertFalse(finishedSuccessfully)
    }

    @Test
    fun timedOutStartupKeepsLateGroupCompletion() = runBlocking {
        var completion: CompletionHandler? = null
        var lateCompletionObserved = false

        val outcome = XingDunStartupPreloadGate(20L).awaitJoinedGroups(
            startLoad = { completion = it },
            onFinished = { lateCompletionObserved = it },
        )

        assertEquals(XingDunStartupPreloadOutcome.TIMED_OUT, outcome)
        assertFalse(lateCompletionObserved)
        completion?.onSuccess()
        assertTrue(lateCompletionObserved)
    }

    @Test
    fun joinedGroupTrackerDistinguishesPendingFailureAndConfirmedZero() {
        val tracker = XingDunJoinedGroupLoadTracker()
        val firstAccount = XingDunJoinedGroupLoadScope(1, "user-a", "xc")
        val secondAccount = XingDunJoinedGroupLoadScope(1, "user-b", "xc")

        assertEquals(XingDunJoinedGroupLoadStatus.NOT_STARTED, tracker.status(firstAccount))
        tracker.begin(firstAccount)
        assertEquals(XingDunJoinedGroupLoadStatus.LOADING, tracker.status(firstAccount))
        tracker.finish(firstAccount, false)
        assertEquals(XingDunJoinedGroupLoadStatus.FAILED, tracker.status(firstAccount))
        tracker.begin(firstAccount)
        tracker.finish(firstAccount, true)
        assertEquals(XingDunJoinedGroupLoadStatus.SUCCEEDED, tracker.status(firstAccount))
        assertEquals(XingDunJoinedGroupLoadStatus.NOT_STARTED, tracker.status(secondAccount))
    }

    @Test
    fun backgroundRefreshKeepsConfirmedCacheWhenRefreshFails() {
        val tracker = XingDunJoinedGroupLoadTracker()
        val account = XingDunJoinedGroupLoadScope(1, "user-a", "xc")

        tracker.begin(account)
        tracker.finish(account, true)
        tracker.begin(account, preserveLoaded = true)
        tracker.finish(account, false)

        assertEquals(XingDunJoinedGroupLoadStatus.SUCCEEDED, tracker.status(account))
    }

    @Test
    fun lateCompletionFromPreviousAccountCannotResolveCurrentAccount() {
        val tracker = XingDunJoinedGroupLoadTracker()
        val previousAccount = XingDunJoinedGroupLoadScope(1, "user-a", "xc")
        val currentAccount = XingDunJoinedGroupLoadScope(1, "user-b", "xc")

        tracker.begin(previousAccount)
        tracker.begin(currentAccount)
        tracker.finish(previousAccount, true)

        assertEquals(XingDunJoinedGroupLoadStatus.LOADING, tracker.status(currentAccount))
        assertEquals(XingDunJoinedGroupLoadStatus.NOT_STARTED, tracker.status(previousAccount))
    }

    @Test
    fun productionStartupWaitIsBoundedToOnePointFiveSeconds() {
        assertEquals(1_500L, XingDunMessageFirstFramePreloader.GROUP_PRELOAD_TIMEOUT_MILLIS)
    }
}
