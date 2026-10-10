package io.trtc.tuikit.chat.demo.main

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

/** Clears real SDK state, never optimistically hides badges or changes message history. */
internal class XingDunReadAllCoordinator(
    private val currentAccount: () -> String?,
    private val findMarkedUnread: suspend () -> List<String>,
    private val clearUnread: suspend () -> Unit,
    private val clearMarks: suspend (List<String>) -> Unit,
) {
    private val mutex = Mutex()
    val isRunning: Boolean get() = mutex.isLocked

    // False means another invocation is still running, not that the SDK succeeded.
    suspend fun execute(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            withTimeout(20_000L) {
                val account = currentAccount()?.takeIf { it.isNotBlank() }
                    ?: error("IM is not logged in")
                fun checkAccount() = check(currentAccount() == account) { "IM account changed" }

                // Collect every page before removing marks, so pagination cannot skip items.
                val marked = findMarkedUnread().filter(String::isNotBlank).distinct()
                checkAccount()
                clearUnread()
                marked.chunked(100).forEach {
                    checkAccount()
                    clearMarks(it)
                }
                checkAccount()
            }
            return true
        } finally {
            mutex.unlock()
        }
    }
}
