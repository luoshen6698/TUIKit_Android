package io.trtc.tuikit.chat.uikit.components.imageviewer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ImageViewerSourceLifecycleTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val sessions = mutableListOf<ImageViewer.Session>()
    private val rows = listOf(ImageElement("synthetic", stableId = "media"))
    private fun source(owner: () -> Boolean = { true }, interval: Long = 1_000, budget: Long = 200,
        validate: suspend () -> Boolean = { true }): ImageViewer.Session =
        ImageViewer.createSession(rows, policy = ImageViewer.SourcePolicy(owner, validate), scope = scope,
            intervalMillis = interval, budgetMillis = budget).also { sessions += it }
    private fun legacy() = ImageViewer.createSession(rows).also { sessions += it }
    private fun start(s: ImageViewer.Session) { s.onViewerStarted(); s.onViewerResumed() }
    private suspend fun state(s: ImageViewer.Session, target: ImageViewerSourceLifecycle.State) {
        withTimeout(2_000) { s.sourceState!!.first { it == target } }
    }
    @After fun cleanup() {
        sessions.forEach { ImageViewer.releaseSession(it.id) }
        scope.cancel()
        ImageViewer.clearRuntimeState()
    }

    @Test fun `protected source creation never emits even a transient frame through the legacy channel`() {
        val observed = mutableListOf<List<ImageElement>>()
        val observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ImageViewer.mediaList.collect { observed += it.toList() }
        }
        try {
            val session = source(); start(session)
            assertEquals(rows, session.mediaListFlow.value)
            assertTrue("All legacy emissions must remain empty for a protected source", observed.all { it.isEmpty() })
        } finally { observer.cancel() }
    }
    @Test fun `legacy lookup without id never inherits a protected group source frame`() {
        val protected = source(); start(protected)
        assertEquals(rows, protected.mediaListFlow.value)
        val fallback = ImageViewer.sessionForLaunch(null)!!
        assertFalse(fallback.isSourceBound)
        assertTrue("No unguarded copy of the protected source", fallback.mediaListFlow.value.isEmpty())
    }
    @Test fun `old legacy fallback cleanup cannot clear a newer exact session global state`() {
        val old = source(); start(old)
        val closeOldOnPublication = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ImageViewer.mediaList.collect { if (it.isNotEmpty()) old.dismiss() }
        }
        val fallback = ImageViewer.sessionForLaunch(null)!!
        val latest = legacy()
        closeOldOnPublication.cancel()
        assertTrue(old.isClosed)
        ImageViewer.releaseLegacySession(fallback)
        assertEquals(rows, ImageViewer.mediaList.value)
        assertSame(latest, ImageViewer.acquireSession(latest.id))
        val sameGeneration = ImageViewer.sessionForLaunch(null)!!
        ImageViewer.releaseLegacySession(sameGeneration)
        assertTrue(ImageViewer.mediaList.value.isEmpty())
    }
    @Test fun `parent pause must not synchronously close its exact authorized internal viewer`() {
        val session = source()
        session.onSourceParentPaused()
        assertFalse("The fresh internal viewer must survive its parent Activity pause", session.isCloseRequested)
        assertTrue("It remains masked until the viewer itself checks", session.mediaListFlow.value.isEmpty())
        start(session)
        assertEquals(rows, session.mediaListFlow.value)
        session.onSourceParentStopped()
        assertFalse(session.isCloseRequested)
    }
    @Test fun `legacy direct viewer keeps its original unguarded source and stop semantics`() {
        val session = legacy()
        session.onViewerStarted(); session.onViewerResumed(); session.onViewerPaused(); session.onViewerStopped()
        assertFalse(session.isCloseRequested)
        assertEquals(rows, session.mediaListFlow.value)
        assertTrue(session.isSourceAuthorized)
    }
    @Test fun `explicit dismiss still clears the exact preview`() {
        val session = source(); start(session); session.dismiss()
        assertTrue(session.isCloseRequested)
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
    @Test fun `parent stopped before viewer started releases pending exact session`() {
        val session = source(); session.onSourceParentPaused(); session.onSourceParentStopped()
        assertTrue(session.isClosed)
        assertNull(ImageViewer.acquireSession(session.id))
        assertTrue(ImageViewer.mediaList.value.isEmpty())
    }
    @Test fun `failed launch release cannot later acquire or reveal its explicit id`() {
        val session = source(); ImageViewer.releaseSession(session.id)
        assertNull(ImageViewer.sessionForLaunch(session.id))
        session.onViewerStarted(); session.onViewerResumed()
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
    @Test fun `missing or closed explicit session never falls back to latest global media`() {
        val latest = legacy()
        assertNull(ImageViewer.sessionForLaunch(Long.MAX_VALUE))
        assertNull(ImageViewer.sessionForLaunch(-1))
        assertEquals(rows, ImageViewer.sessionForLaunch(null)!!.mediaListFlow.value)
        latest.dismiss()
        assertNull(ImageViewer.sessionForLaunch(latest.id))
    }
    @Test fun `modal pause masks immediately and resume waits for a new grant`() = runBlocking {
        var grant = CompletableDeferred<Boolean>().also { it.complete(true) }
        var calls = 0
        val session = source { calls++; grant.await() }; start(session)
        assertEquals(rows, session.mediaListFlow.value)
        session.onViewerPaused()
        assertTrue(session.mediaListFlow.value.isEmpty())
        assertTrue(ImageViewer.mediaList.value.isEmpty())
        grant = CompletableDeferred(); session.onViewerResumed()
        assertEquals(2, calls)
        assertFalse(session.isSourceAuthorized)
        grant.complete(true); state(session, ImageViewerSourceLifecycle.State.VISIBLE)
        assertEquals(rows, session.mediaListFlow.value)
    }
    @Test fun `real viewer stop closes rather than retaining a background group frame`() {
        var closed = 0
        val session = source(); session.addOnClosedListener { closed++ }
        start(session); session.onViewerPaused(); session.onViewerStopped()
        assertTrue(session.isClosed)
        assertNull(ImageViewer.acquireSession(session.id))
        assertEquals(1, closed)
        assertTrue(session.isCloseRequested)
        assertTrue(session.mediaListFlow.value.isEmpty())
        session.onViewerResumed()
        assertFalse(session.isSourceAuthorized)
    }
    @Test fun `revoked or unavailable viewer source closes without restoring cached media`() {
        for (fails in listOf(false, true)) {
            val session = source { if (fails) error("synthetic unknown") else false }
            start(session)
            assertTrue(session.isCloseRequested)
            assertTrue(session.mediaListFlow.value.isEmpty())
        }
    }
    @Test fun `source hint conceals immediately and only its fresh recheck may reveal`() = runBlocking {
        var grant = CompletableDeferred<Boolean>().also { it.complete(true) }
        val session = source { grant.await() }; start(session)
        grant = CompletableDeferred(); session.invalidateSource()
        assertFalse(session.isSourceAuthorized)
        assertTrue(session.mediaListFlow.value.isEmpty())
        grant.complete(false); state(session, ImageViewerSourceLifecycle.State.CLOSED)
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
    @Test fun `owner replacement during a check closes instead of applying a late allow`() = runBlocking {
        var owner = true
        val grant = CompletableDeferred<Boolean>()
        val session = source(owner = { owner }) { grant.await() }; start(session)
        owner = false; grant.complete(true)
        state(session, ImageViewerSourceLifecycle.State.CLOSED)
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
    @Test fun `owner hint while parent is paused closes an already visible exact viewer`() {
        var owner = true
        val session = source(owner = { owner }); start(session); session.onSourceParentPaused()
        owner = false; session.invalidateSource()
        assertTrue(session.isCloseRequested)
    }
    @Test fun `canceled earlier resume allow cannot unmask a later pending generation`() = runBlocking {
        val first = CompletableDeferred<Boolean>(); val second = CompletableDeferred<Boolean>(); var calls = 0
        val session = source {
            val grant = if (++calls == 1) first else second
            withContext(NonCancellable) { grant.await() }
        }
        start(session); session.onViewerPaused(); session.onViewerResumed()
        first.complete(true); yield()
        assertFalse(session.isSourceAuthorized)
        assertTrue(session.mediaListFlow.value.isEmpty())
        second.complete(true); state(session, ImageViewerSourceLifecycle.State.VISIBLE)
        assertEquals(rows, session.mediaListFlow.value)
    }
    @Test fun `older close and mask never clear the newer global session`() {
        val old = source(); start(old)
        val latest = legacy()
        old.onViewerPaused(); old.dismiss(); ImageViewer.releaseSession(old.id)
        assertEquals(rows, ImageViewer.mediaList.value)
        assertSame(latest, ImageViewer.sessionForLaunch(latest.id))
        assertEquals(rows, latest.mediaListFlow.value)
    }
    @Test fun `source callbacks cannot submit unverified content while masked`() {
        val session = source(); session.submitMediaList(rows)
        assertTrue(session.mediaListFlow.value.isEmpty())
        start(session); session.onViewerPaused(); session.submitMediaList(rows)
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
    @Test fun `periodic stall has one active check and cooperative timeout closes and cancels it`() = runBlocking {
        var calls = 0; var inflight = 0; var maximum = 0; var cancelled = false
        val session = source(interval = 15, budget = 65) {
            calls++; inflight++; maximum = maxOf(maximum, inflight)
            try { if (calls == 1) true else awaitCancellation() }
            finally { inflight--; if (calls > 1) cancelled = true }
        }
        start(session)
        state(session, ImageViewerSourceLifecycle.State.CLOSED)
        assertEquals(2, calls)
        assertEquals(1, maximum)
        assertTrue(cancelled)
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
    @Test fun `healthy periodic confirmation does not replace the already displayed source list`() = runBlocking {
        var calls = 0
        val session = source(interval = 15) { calls++; true }; start(session)
        val shown = session.mediaListFlow.value
        withTimeout(2_000) { while (calls < 3) delay(5) }
        assertSame(shown, session.mediaListFlow.value)
        assertFalse(session.isCloseRequested)
    }
    @Test fun `permission callback waits for fresh resume and then fresh action authority`() = runBlocking {
        var grant = CompletableDeferred<Boolean>().also { it.complete(true) }; var calls = 0
        val session = source { calls++; grant.await() }; start(session); session.onViewerPaused()
        val action = async { session.authorizeSourceAction(rows.first()) }; yield()
        assertFalse(action.isCompleted)
        grant = CompletableDeferred(); session.onViewerResumed(); assertFalse(action.isCompleted)
        grant.complete(true)
        assertTrue(withTimeout(2_000) { action.await() })
        assertEquals(3, calls)
    }
    @Test fun `fresh export action denial closes even after a visible grant and foreign media is never authorized`() = runBlocking {
        var allowed = true
        val session = source { allowed }; start(session)
        assertFalse(session.authorizeSourceAction(ImageElement("other", stableId = "foreign")))
        allowed = false
        assertFalse(session.authorizeSourceAction(rows.first()))
        assertTrue(session.isCloseRequested)
    }
    @Test fun `parent destroy dismiss releases listeners and never revives after pending allow`() = runBlocking {
        val grant = CompletableDeferred<Boolean>(); var closed = 0
        val session = source { withContext(NonCancellable) { grant.await() } }
        session.addOnClosedListener { closed++ }; start(session); session.dismiss(); ImageViewer.releaseSession(session.id)
        grant.complete(true); yield()
        assertEquals(1, closed)
        assertNull(ImageViewer.acquireSession(session.id))
        assertTrue(session.mediaListFlow.value.isEmpty())
    }
}
