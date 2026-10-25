package io.trtc.tuikit.chat.demo.xingdun.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns at most one scan; reconnects queue a read, never a detached background traversal. */
internal class XingDunForegroundDeletionScan(private val scope: CoroutineScope) {
    private var foreground = false
    private var pending: (suspend () -> Unit)? = null
    private var running: (suspend () -> Unit)? = null
    private var ownedJob: Job? = null

    @Synchronized fun request(scan: suspend () -> Unit) {
        pending = scan
        startIfReady()
    }

    @Synchronized fun setForeground(active: Boolean) {
        foreground = active
        if (!active) {
            if (pending == null) pending = running
            ownedJob?.cancel()
        } else startIfReady()
    }

    @Synchronized private fun startIfReady() {
        if (!foreground || ownedJob?.isActive == true) return
        val scan = pending ?: return
        pending = null
        running = scan
        val job = scope.launch(start = CoroutineStart.LAZY) { scan() }
        ownedJob = job
        job.invokeOnCompletion {
            synchronized(this) {
                // Completion of a canceled old scan cannot clear or restart a newer owner.
                if (ownedJob === job) {
                    ownedJob = null
                    running = null
                    startIfReady()
                }
            }
        }
        job.start()
    }
}
