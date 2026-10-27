package io.trtc.tuikit.chat.uikit.components.imageviewer
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.imageviewer.ui.ImageViewerActivity
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface EventHandler {
    fun onEvent(eventData: Map<String, Any>, callback: (Any?) -> Unit)
}

data class ImageElement(
    val data: Any?,
    val type: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val videoData: Any? = null,
    val stableId: String? = null
) {
    constructor(
        data: Any?,
        type: Int = 0,
        width: Int = 0,
        height: Int = 0,
        videoData: Any? = null
    ) : this(
        data = data,
        type = type,
        width = width,
        height = height,
        videoData = videoData,
        stableId = null
    )
}

/** Only opted-in app sources use this lifecycle; ordinary previews retain their existing behavior. */
internal class ImageViewerSourceLifecycle(
    parentScope: CoroutineScope,
    private val ownerIsCurrent: () -> Boolean,
    private val revalidate: suspend () -> Boolean,
    private val mask: () -> Unit,
    private val reveal: () -> Unit,
    private val closeSession: () -> Unit,
    private val intervalMillis: Long = 10_000,
    private val budgetMillis: Long = 10_000,
) {
    enum class State { PENDING, MASKED, VISIBLE, CLOSED }
    val state = MutableStateFlow(State.PENDING)
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val validationMutex = Mutex()
    private var revision = 0L
    private var resumed = false
    var hasStarted = false
        private set
    private var validationJob: Job? = null

    private fun owns(ticket: Long) = state.value != State.CLOSED && ticket == revision
    private fun ownerMatches() = try { ownerIsCurrent() } catch (_: Exception) { false }
    fun parentPaused() { if (!ownerMatches()) close() }
    fun parentStopped() { if (!hasStarted) close() }
    fun viewerStarted() {
        if (state.value == State.CLOSED) return
        hasStarted = true
        conceal()
    }
    fun viewerResumed() {
        if (state.value == State.CLOSED) return
        resumed = true
        restart()
    }
    fun viewerPaused() {
        if (state.value == State.CLOSED) return
        resumed = false
        revision++
        validationJob?.cancel()
        validationJob = null
        conceal()
    }
    fun viewerStopped() = close()
    fun invalidate() {
        if (state.value == State.CLOSED) return
        if (!ownerMatches()) { close(); return }
        if (resumed) restart() else conceal()
    }
    private fun conceal() {
        mask()
        if (state.value != State.CLOSED) state.value = State.MASKED
    }
    private suspend fun validateWithinBudget(): Boolean = try {
        withTimeout(budgetMillis) {
            validationMutex.withLock { ownerMatches() && revalidate() && ownerMatches() }
        }
    } catch (_: TimeoutCancellationException) { false }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { false }

    private fun restart() {
        revision++
        validationJob?.cancel()
        conceal()
        if (!ownerMatches()) { close(); return }
        val ticket = revision
        validationJob = scope.launch {
            // One loop owns each foreground generation. Periodic checks never cancel a slow check.
            while (owns(ticket) && resumed) {
                val allowed = validateWithinBudget()
                if (!owns(ticket) || !resumed) return@launch
                if (!allowed || !ownerMatches()) { close(); return@launch }
                state.value = State.VISIBLE
                reveal()
                // Keep a confirmed frame during the next bounded periodic read, without rebuilding it.
                delay(intervalMillis)
            }
        }
    }

    /** Permission callbacks may arrive before onResume. Wait for its fresh grant, then authorize this action. */
    suspend fun authorizeAction(): Boolean {
        if (state.value != State.VISIBLE) state.first { it == State.VISIBLE || it == State.CLOSED }
        if (state.value == State.CLOSED || !resumed) return false
        val ticket = revision
        val allowed = try { scope.async { validateWithinBudget() }.await() }
            catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                return false
            }
        if (!owns(ticket) || !resumed) return false
        if (!allowed || !ownerMatches()) { close(); return false }
        return true
    }

    fun close() {
        if (state.value == State.CLOSED) return
        revision++
        resumed = false
        state.value = State.CLOSED
        mask()
        scope.cancel()
        closeSession()
    }
}

object ImageViewer {
    private const val TAG = "ImageViewer"
    const val EXTRA_SESSION_ID = "io.trtc.tuikit.chat.uikit.components.imageviewer.SESSION_ID"
    const val EVENT_IMAGE_TAP = "onImageTap"
    const val EVENT_LOAD_MORE_OLDER = "onLoadMoreOlder"
    const val EVENT_LOAD_MORE_NEWER = "onLoadMoreNewer"
    const val EVENT_DOWNLOAD_VIDEO = "onDownloadVideo"
    const val EVENT_SAVE_MEDIA = "onSaveMedia"

    class SourcePolicy(val ownerIsCurrent: () -> Boolean, val revalidate: suspend () -> Boolean)

    class Session internal constructor(
        val id: Long,
        internal val eventHandler: EventHandler?,
        internal val initDataInternal: ImageElement?,
        internal val mediaListFlow: MutableStateFlow<List<ImageElement>>,
        internal val legacyGlobalSessionId: Long? = null
    ) {
        private val closeListeners = mutableSetOf<() -> Unit>()
        internal val downloadingKeys = mutableSetOf<String>()
        internal val savingKeys = mutableSetOf<String>()
        internal val videoOverrides = mutableMapOf<String, Any>()
        internal var isClosed: Boolean = false
            private set
        internal var isCloseRequested: Boolean = false
            private set
        internal var isVisible: Boolean = false
        private var sourceLifecycle: ImageViewerSourceLifecycle? = null
        private var sourceMedia = emptyList<ImageElement>()
        val isSourceBound: Boolean get() = sourceLifecycle != null
        internal val sourceState get() = sourceLifecycle?.state
        internal val isSourceAuthorized get() = sourceLifecycle?.state?.value.let {
            it == null || it == ImageViewerSourceLifecycle.State.VISIBLE
        } && !isClosed && !isCloseRequested

        internal fun bindSource(policy: SourcePolicy, scope: CoroutineScope, intervalMillis: Long, budgetMillis: Long) {
            sourceMedia = mediaListFlow.value.toList()
            sourceLifecycle = ImageViewerSourceLifecycle(scope, policy.ownerIsCurrent, policy.revalidate,
                mask = { publish(emptyList()) }, reveal = { publish(sourceMedia) },
                closeSession = { closeInternal() }, intervalMillis = intervalMillis, budgetMillis = budgetMillis)
            // Never expose a source-bound frame before the viewer itself has checked it.
            publish(emptyList())
        }
        fun onSourceParentPaused() { sourceLifecycle?.parentPaused() ?: dismiss() }
        fun onSourceParentStopped() { sourceLifecycle?.parentStopped() }
        fun invalidateSource() { sourceLifecycle?.invalidate() }
        internal fun onViewerStarted() { sourceLifecycle?.viewerStarted() }
        internal fun onViewerResumed() { sourceLifecycle?.viewerResumed() }
        internal fun onViewerPaused() { sourceLifecycle?.viewerPaused() }
        internal fun onViewerStopped() { sourceLifecycle?.viewerStopped() }
        internal suspend fun authorizeSourceAction(element: ImageElement): Boolean {
            if (sourceLifecycle == null) return true
            if (sourceLifecycle?.authorizeAction() != true) return false
            return isSourceAuthorized && sourceMedia.any { ImageViewerMediaIdentity.sameMedia(it, element) }
        }

        fun dismiss() { requestClose() }
        internal fun requestClose() {
            if (isClosed) return
            sourceLifecycle?.close()
            closeInternal()
        }
        private fun closeInternal() {
            if (isClosed || isCloseRequested) return
            isCloseRequested = true
            sourceMedia = emptyList()
            publish(emptyList())
            if (isSourceBound) releaseSession(id)
        }
        private fun publish(list: List<ImageElement>) {
            if (isClosed) return
            mediaListFlow.value = list
            if (isSourceBound && latestGlobalSessionId == id) {
                // Protected content never enters the unguarded no-SessionID legacy channel.
                mediaList.value = emptyList()
                ImageViewer.initDataInternal = null
            }
        }
        fun submitMediaList(list: List<ImageElement>) {
            if (isClosed || isCloseRequested) return
            if (isSourceBound) {
                // App-scoped previews have a fixed, checked source snapshot; callbacks cannot unmask it.
                return
            }
            mediaListFlow.value = list
        }
        fun addOnClosedListener(listener: () -> Unit): () -> Unit {
            synchronized(closeListeners) { closeListeners.add(listener) }
            return { synchronized(closeListeners) { closeListeners.remove(listener) } }
        }
        internal fun markClosed() {
            if (isClosed) return
            if (isSourceBound) mediaListFlow.value = emptyList()
            isClosed = true
            sourceLifecycle?.close()
            sourceMedia = emptyList()
            val listeners = synchronized(closeListeners) { closeListeners.toList().also { closeListeners.clear() } }
            listeners.forEach { it.invoke() }
        }
    }

    private val sessionIdGenerator = AtomicLong(0L)
    private val sessions = mutableMapOf<Long, Session>()
    private var latestGlobalSessionId: Long? = null
    var eventHandler: EventHandler? = null
        private set
    val mediaList = MutableStateFlow<List<ImageElement>>(emptyList())
    var initDataInternal: ImageElement? = null
        private set

    @JvmStatic fun view(imageElements: List<ImageElement>, initialIndex: Int = 0, onEventTriggered: EventHandler? = null): Session? =
        launch(imageElements, initialIndex, onEventTriggered, null)

    fun viewFromSource(imageElements: List<ImageElement>, initialIndex: Int, policy: SourcePolicy): Session? =
        launch(imageElements, initialIndex, null, policy)

    private fun launch(imageElements: List<ImageElement>, initialIndex: Int, handler: EventHandler?, policy: SourcePolicy?): Session? {
        val context = ContextProvider.getApplicationContext() ?: return null
        val session = createSession(imageElements, initialIndex, handler, policy)
        val intent = Intent(context, ImageViewerActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, session.id)
            if (context is Application) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                releaseSession(session.id)
                Log.w(TAG, "No activity can handle ImageViewer intent")
                null
            } else {
                context.startActivity(intent)
                session
            }
        } catch (failure: RuntimeException) {
            releaseSession(session.id)
            Log.e(TAG, "Failed to start ImageViewerActivity", failure)
            null
        }
    }

    internal fun createSession(imageElements: List<ImageElement>, initialIndex: Int = 0, handler: EventHandler? = null,
        policy: SourcePolicy? = null, scope: CoroutineScope? = null, intervalMillis: Long = 10_000, budgetMillis: Long = 10_000): Session {
        val sessionId = sessionIdGenerator.incrementAndGet()
        val initial = imageElements.getOrNull(ImageViewerSessionState.coerceInitialIndex(initialIndex, imageElements.size))
        val session = Session(sessionId, handler, initial, MutableStateFlow(imageElements))
        synchronized(sessions) { sessions[sessionId] = session }
        latestGlobalSessionId = sessionId
        eventHandler = handler
        initDataInternal = if (policy == null) initial else null
        mediaList.value = if (policy == null) imageElements else emptyList()
        if (policy != null) session.bindSource(policy, scope ?: CoroutineScope(Dispatchers.Main.immediate), intervalMillis, budgetMillis)
        return session
    }
    internal fun acquireSession(sessionId: Long): Session? = synchronized(sessions) {
        sessions[sessionId]?.takeUnless { it.isClosed || it.isCloseRequested }
    }
    internal fun sessionForLaunch(explicitId: Long?): Session? = if (explicitId != null) acquireSession(explicitId) else
        Session(-1L, eventHandler, initDataInternal, mediaList, legacyGlobalSessionId = latestGlobalSessionId)

    internal fun releaseSession(sessionId: Long) {
        val session = synchronized(sessions) { sessions.remove(sessionId) }
        session?.markClosed()
        if (latestGlobalSessionId == sessionId) clearRuntimeState()
    }
    internal fun releaseLegacySession(session: Session) {
        if (session.id < 0 && latestGlobalSessionId == session.legacyGlobalSessionId) clearRuntimeState()
    }

    internal fun clearRuntimeState() {
        eventHandler = null
        initDataInternal = null
        mediaList.value = emptyList()
        latestGlobalSessionId = null
    }
}
