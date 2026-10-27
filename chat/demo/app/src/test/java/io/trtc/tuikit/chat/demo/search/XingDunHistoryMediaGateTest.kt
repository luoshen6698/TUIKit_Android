package io.trtc.tuikit.chat.demo.search

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Test
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchGroupHistoryAccess
import org.junit.Assert.*

class XingDunHistoryMediaGateTest {
    private val rows = listOf(MessageInfo(msgID = "selected"))

    @After fun restoreSourceProviders() {
        SearchMessageVisibility.setDisplayProvider(null)
        SearchMessageVisibility.setSessionProvider { null }
        SearchMessageVisibility.setProvider { _, value -> value }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        SearchMessageVisibility.setVerificationFailureProvider { false }
        SearchGroupHistoryAccess.setProvider(null)
    }
    private fun sourceSetup() {
        SearchMessageVisibility.setSessionProvider { "source-owner" }
        SearchMessageVisibility.setProvider { _, value -> value }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        SearchMessageVisibility.setVerificationFailureProvider { false }
        SearchGroupHistoryAccess.setProvider { true }
    }
    @Test fun `actual detached viewer source checks batch deletion then fresh history without parent active state`() = runBlocking {
        sourceSetup(); val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        SearchMessageVisibility.setDisplayProvider { _, value, mapper -> events += "snapshot"; mapper(value) }
        SearchMessageVisibility.setProvider { _, value -> events += "delete"; value }
        SearchGroupHistoryAccess.setProvider { events += "history"; true }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        assertTrue(policy.ownerIsCurrent())
        assertTrue(policy.revalidate())
        assertEquals(listOf("snapshot", "delete", "history"), events)
    }
    @Test fun `actual detached viewer known deleted or fresh denied source cannot survive revalidation`() = runBlocking {
        sourceSetup(); var historyCalls = 0
        SearchGroupHistoryAccess.setProvider { historyCalls++; false }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        SearchMessageVisibility.setDeletedProvider { _, _ -> true }
        assertFalse(policy.revalidate()); assertEquals(0, historyCalls)
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        assertFalse(policy.revalidate()); assertEquals(1, historyCalls)
        SearchGroupHistoryAccess.setProvider { true }
        SearchMessageVisibility.setProvider { _, _ -> emptyList() }
        assertFalse(policy.revalidate())
    }
    @Test fun `actual detached viewer owner replacement stops before source reads and late deletion cannot become a grant`() = runBlocking {
        sourceSetup(); var owner = "source-owner"; var reads = 0
        SearchMessageVisibility.setSessionProvider { owner }
        SearchMessageVisibility.setProvider { _, value -> reads++; owner = "new-generation"; value }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        try { policy.revalidate(); fail("Changed source must be unavailable") } catch (_: IllegalStateException) { }
        assertFalse(policy.ownerIsCurrent())
        assertFalse(policy.revalidate())
        assertEquals(1, reads)
    }
    @Test fun `actual detached viewer fresh deletion failure and missing owner never open retained source`() = runBlocking {
        sourceSetup(); SearchMessageVisibility.setVerificationFailureProvider { true }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        try { policy.revalidate(); fail("Failed deletion verification must not grant") } catch (_: IllegalStateException) { }
        val missing = XingDunHistoryMediaGate.sourcePolicy("group_target", null, rows, setOf("selected"))
        assertFalse(missing.ownerIsCurrent()); assertFalse(missing.revalidate())
    }

    @Test fun `group media requires fresh history after deletion and calls current rejection on deny`() = runBlocking {
        val events = mutableListOf<String>()
        var rejected = 0
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { events += "delete"; rows }, { events += "history"; false }, { rejected++ })
        assertNull(value)
        assertEquals(listOf("delete", "history"), events)
        assertEquals(1, rejected)
    }
    @Test fun `unknown group authority rejects rather than opening the deletion checked snapshot`() = runBlocking {
        var rejected = 0
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { rows }, { error("synthetic unknown") }, { rejected++ })
        assertNull(value)
        assertEquals(1, rejected)
    }
    @Test fun `allowed group source preserves selected snapshot after both checks`() = runBlocking {
        val events = mutableListOf<String>()
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { events += "delete"; rows }, { events += "history"; true }, { fail("No deny on allow") })
        assertEquals(rows, value)
        assertEquals(listOf("delete", "history"), events)
    }
    @Test fun `direct media keeps deletion check without introducing group policy`() = runBlocking {
        var deleted = false
        val value = XingDunHistoryMediaGate.checked("c2c_friend", "selected", { true },
            { deleted = true; rows }, { fail("No group ACL for direct media"); false }, { fail("No group rejection") })
        assertTrue(deleted)
        assertEquals(rows, value)
    }
    @Test fun `deleted selected source never opens media or requests another grant`() = runBlocking {
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { emptyList() }, { fail("Deleted selection is not actionable"); true }, { fail("No current policy denial") })
        assertNull(value)
    }
    @Test fun `late allow cannot open or reject after page media account or lifecycle replacement`() = runBlocking {
        for (replacement in listOf("new-page", "new-media", "new-account", "paused")) {
            var current = "original"
            val allow = CompletableDeferred<Boolean>()
            var rejected = 0
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                XingDunHistoryMediaGate.checked("group_target", "selected", { current == "original" },
                    { rows }, { withContext(NonCancellable) { allow.await() } }, { rejected++ })
            }
            assertFalse("Fresh history must finish before media can open", request.isCompleted)
            current = replacement
            allow.complete(true)
            assertNull(request.await())
            assertEquals(0, rejected)
        }
    }
    @Test fun `already replaced source never reads or opens anything`() = runBlocking {
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { false },
            { fail("No stale source read"); rows }, { fail("No stale policy call"); true }, { fail("No new-page denial") })
        assertNull(value)
    }
    @Test fun `source change during deletion stops before history and never rejects a replacement`() = runBlocking {
        var current = true
        val deletion = CompletableDeferred<List<MessageInfo>>()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            XingDunHistoryMediaGate.checked("group_target", "selected", { current },
                { withContext(NonCancellable) { deletion.await() } },
                { fail("No stale authorization"); true }, { fail("No new-page denial") })
        }
        current = false
        deletion.complete(rows)
        assertNull(request.await())
    }

    @Test fun `unavailable group deletion authority rejects the current source without opening cached media`() = runBlocking {
        var rejected = 0
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { error("synthetic deletion verification unavailable") }, { fail("No grant after failed source read"); true }, { rejected++ })
        assertNull(value)
        assertEquals(1, rejected)
    }

    @Test fun `authority cancellation propagates without denying a replacement page`() = runBlocking {
        var rejected = 0
        try {
            XingDunHistoryMediaGate.checked("group_target", "selected", { true },
                { rows }, { throw CancellationException("synthetic cancellation") }, { rejected++ })
            fail("Cancellation must not return an openable snapshot")
        } catch (_: CancellationException) { }
        assertEquals(0, rejected)
    }
}
