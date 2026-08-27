package io.trtc.tuikit.chat.demo.xingdun.features

internal object XingDunAutoDeletePolicy {
    val DEFAULT_TTL_SECONDS = listOf(0, 120, 3_600, 86_400, 604_800, 2_592_000, 7_776_000, 31_536_000)

    fun normalizedOptions(values: List<Int>): List<Int> {
        val supported = values.filter { it in DEFAULT_TTL_SECONDS }.distinct()
        return supported.ifEmpty { DEFAULT_TTL_SECONDS }
    }

    fun shouldApplyRemote(currentVersion: Int?, incomingVersion: Int): Boolean =
        incomingVersion > 0 && (currentVersion == null || incomingVersion >= currentVersion)
}
