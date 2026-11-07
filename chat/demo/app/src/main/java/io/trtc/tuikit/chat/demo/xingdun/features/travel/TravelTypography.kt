package io.trtc.tuikit.chat.demo.xingdun.features.travel

/** Mirrors the reference Android AppTextScaler; system accessibility scaling still applies. */
internal object TravelTypography {
    fun size(original: Float): Float = when {
        original == 0f -> 0f
        original <= 10f -> 10f
        original <= 16f -> 12f
        original <= 20f -> 18f
        original <= 24f -> 22f
        else -> original
    }
}
