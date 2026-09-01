package io.trtc.tuikit.chat.demo.xingdun.features

internal object XingDunReportFormPolicy {
    private val supportedTargetTypes = setOf("user", "team", "message")

    fun initialTargetType(targetType: String, hasFixedTarget: Boolean): String {
        return targetType.takeIf { hasFixedTarget && it in supportedTargetTypes } ?: "user"
    }
}
