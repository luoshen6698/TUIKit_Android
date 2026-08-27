package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessage

internal object XingDunPinnedMessagePolicy {
    const val MODE_EVERYONE = 1

    fun canManage(
        role: String,
        isAssignedCustomerService: Boolean,
        pinMessageMode: Int,
    ): Boolean = pinMessageMode == MODE_EVERYONE ||
        role == "owner" ||
        role == "administrator" ||
        isAssignedCustomerService

    fun shouldApply(currentVersion: Int?, incomingVersion: Int): Boolean =
        incomingVersion > 0 && (currentVersion == null || incomingVersion >= currentVersion)

    fun visiblePins(items: List<XingDunPinnedMessage>): List<XingDunPinnedMessage> =
        items.filter { it.isPinned && it.messageId.isNotBlank() }

    fun readToken(pin: XingDunPinnedMessage): String = "${pin.messageId}#${pin.version}"

    fun summaryType(messageType: String?): SummaryType = when (messageType?.uppercase()) {
        "PICTURE", "IMAGE" -> SummaryType.IMAGE
        "AUDIO", "SOUND" -> SummaryType.AUDIO
        "VIDEO" -> SummaryType.VIDEO
        "FILE" -> SummaryType.FILE
        "CUSTOM" -> SummaryType.CUSTOM
        else -> SummaryType.MESSAGE
    }

    enum class SummaryType { IMAGE, AUDIO, VIDEO, FILE, CUSTOM, MESSAGE }
}
