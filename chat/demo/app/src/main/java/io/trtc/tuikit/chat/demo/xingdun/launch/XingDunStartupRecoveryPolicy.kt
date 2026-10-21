package io.trtc.tuikit.chat.demo.xingdun.launch

/** Foreground retry is bounded in frequency, never an implicit password login or session takeover. */
internal object XingDunStartupRecoveryPolicy {
    fun retryDelayMillis(failedAttempts: Int): Long = when (failedAttempts.coerceAtLeast(0)) {
        0 -> 2_000L
        1 -> 5_000L
        2 -> 10_000L
        else -> 30_000L
    }
}
