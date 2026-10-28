package io.trtc.tuikit.chat.demo.search

import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupDeletionReadScope
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionReadStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Activity-owned non-secret scope. Call lifecycle/publication methods on the Activity dispatcher. */
internal class XingDunHistoryScopeGuard(
    initial: XingDunGroupDeletionReadScope,
    private val currentStamp: () -> XingDunSessionReadStamp?,
    private val loadScope: () -> XingDunGroupDeletionReadScope?,
    private val authorize: suspend () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    enum class Result { READY, REPLACED, DENIED, UNAVAILABLE, STALE, RETRY }
    private val owner = initial.display
    private val generation = initial.stamp.authenticationGeneration
    var scope = initial
        private set
    var pageEpoch = 0L
        private set
    private var request = 0L
    private var active = false
    private var usable = false
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    fun activate() { active = true; usable = false; pageEpoch++; request++; while (refreshRequests.tryReceive().isSuccess) { } }
    fun pause() { active = false; usable = false; pageEpoch++; request++ }
    fun isActive(epoch: Long) = active && epoch == pageEpoch
    fun isCurrent() = active && usable && scope.stamp == currentStamp()

    fun acceptsRead(ticket: Long, currentTicket: Long) = ticket == currentTicket && isCurrent()

    // The retained C2C viewer lives in another Activity; pausing this page must not hide deletion events.
    fun isBoundToCurrentStamp() = scope.stamp == currentStamp()

    fun requestRefresh() { if (active) refreshRequests.trySend(Unit) }

    /** Sole history cycle. Fast guards wake this same serial job, never a second authorizer. */
    suspend fun observe(onInvalidated: () -> Unit, onResult: (Result) -> Unit, intervalMillis: Long = 10_000L) {
        val epoch = pageEpoch
        while (isActive(epoch)) {
            while (refreshRequests.tryReceive().isSuccess) { }
            val previousStamp = scope.stamp
            if (!isCurrent()) onInvalidated()
            val result = refresh()
            if (!isActive(epoch)) return
            if (result == Result.READY && previousStamp != scope.stamp) onInvalidated()
            onResult(result)
            if (!isActive(epoch) || result in listOf(Result.REPLACED, Result.DENIED, Result.UNAVAILABLE, Result.STALE)) return
            // A post-IO RETRY stays hidden and is retried by this same bounded periodic cycle.
            withTimeoutOrNull(intervalMillis) { refreshRequests.receive() }
        }
    }

    suspend fun refresh(): Result {
        if (!active) return Result.STALE
        val epoch = pageEpoch
        val ticket = ++request
        val candidate = withContext(io) {
            repeat(3) {
                val before = waitForStamp(currentStamp) ?: return@withContext Result.UNAVAILABLE to null
                if (before.authenticationGeneration != generation) return@withContext Result.REPLACED to null
                val fresh = try { loadScope() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                if (fresh == null) {
                    if (before != currentStamp()) return@repeat
                    return@withContext Result.REPLACED to null
                }
                if (fresh.display != owner || fresh.stamp.authenticationGeneration != generation) {
                    return@withContext Result.REPLACED to null
                }
                val allowed = try { authorize() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                val latest = currentStamp()
                if (latest != null && latest.authenticationGeneration != generation) {
                    return@withContext Result.REPLACED to null
                }
                if (fresh.stamp != latest) return@repeat
                return@withContext (if (allowed) Result.READY else Result.DENIED) to fresh
            }
            Result.UNAVAILABLE to null
        }
        if (!isActive(epoch) || ticket != request) return Result.STALE
        val fresh = candidate.second
        val latest = currentStamp()
        if (fresh != null && fresh.stamp != latest) {
            usable = false
            return if (latest != null && latest.authenticationGeneration != generation) Result.REPLACED else Result.RETRY
        }
        if (candidate.first == Result.READY && fresh != null) scope = fresh
        usable = candidate.first == Result.READY
        return candidate.first
    }

    companion object {
        private suspend fun waitForStamp(currentStamp: () -> XingDunSessionReadStamp?) = withTimeoutOrNull(2_000L) {
            var value = currentStamp()
            while (value == null) { delay(50L); value = currentStamp() }
            value
        }

        suspend fun readInitialScope(
            currentStamp: () -> XingDunSessionReadStamp?,
            loadScope: () -> XingDunGroupDeletionReadScope?,
            io: CoroutineDispatcher = Dispatchers.IO,
        ): XingDunGroupDeletionReadScope? = withContext(io) {
            repeat(3) {
                val before = waitForStamp(currentStamp) ?: return@withContext null
                val fresh = loadScope()
                val latest = currentStamp()
                if (before != latest) return@repeat
                return@withContext fresh?.takeIf { it.stamp == latest }
            }
            null
        }
    }

}
