package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunFeatures

internal data class XingDunChatFeatureAvailability(
    val redpacket: Boolean,
    val audioCall: Boolean,
    val videoCall: Boolean,
    val groupCall: Boolean,
)

internal object XingDunRuntimeFeaturePolicy {
    fun redpacketEnabled(features: XingDunFeatures?): Boolean = features?.redpacket == true

    fun chatAvailability(
        features: XingDunFeatures?,
        isDirectConversation: Boolean,
        isGroupConversation: Boolean,
    ): XingDunChatFeatureAvailability {
        val resolved = features ?: XingDunFeatures()
        val groupCallEnabled = isGroupConversation && resolved.groupCall
        return XingDunChatFeatureAvailability(
            redpacket = (isDirectConversation || isGroupConversation) && redpacketEnabled(resolved),
            audioCall = (isDirectConversation && resolved.audioCall) || groupCallEnabled,
            videoCall = (isDirectConversation && resolved.videoCall) || groupCallEnabled,
            groupCall = groupCallEnabled,
        )
    }
}
