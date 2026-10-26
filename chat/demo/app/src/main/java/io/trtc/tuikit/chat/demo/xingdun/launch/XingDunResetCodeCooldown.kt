package io.trtc.tuikit.chat.demo.xingdun.launch

/** Deadlines survive Activity recreation; a response never changes a different target's timer. */
internal class XingDunResetCodeCooldown(private val deadlines: MutableMap<String, Long> = mutableMapOf()) {
    fun key(company: String, channel: String, target: String): String =
        listOf(company.trim().lowercase(), channel, target.trim().let { if (channel == "email") it.lowercase() else it }).joinToString("\u0000")

    fun begin(key: String, now: Long) { deadlines[key] = now + 60_000L }
    fun remaining(key: String, now: Long): Long = ((deadlines[key] ?: 0L) - now).coerceAtLeast(0L)
    fun snapshot(now: Long): Map<String, Long> = deadlines.filterValues { it > now && it <= now + 60_000L }
}
