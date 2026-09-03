package io.trtc.tuikit.chat.demo.xingdun.session

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunRuntimeFeaturePolicyTest {
    @Test
    fun `enabled bootstrap value enables redpacket`() {
        val features = XingDunFeatures(redpacket = true, groupCall = true)

        assertTrue(XingDunRuntimeFeaturePolicy.redpacketEnabled(features))
    }

    @Test
    fun `missing bootstrap values default closed`() {
        val bootstrap = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
            .fromJson("""{"features":{}}""", XingDunBootstrapConfiguration::class.java)

        assertFalse(bootstrap.features.redpacket)
        assertFalse(bootstrap.features.groupCall)
    }

    @Test
    fun `direct call switches do not enable group calls`() {
        val availability = XingDunRuntimeFeaturePolicy.chatAvailability(
            features = XingDunFeatures(audioCall = true, videoCall = true, groupCall = false),
            isDirectConversation = false,
            isGroupConversation = true,
        )

        assertFalse(availability.audioCall)
        assertFalse(availability.videoCall)
        assertFalse(availability.groupCall)
    }

    @Test
    fun `group call switch enables group call entries independently`() {
        val availability = XingDunRuntimeFeaturePolicy.chatAvailability(
            features = XingDunFeatures(audioCall = false, videoCall = false, groupCall = true),
            isDirectConversation = false,
            isGroupConversation = true,
        )

        assertTrue(availability.audioCall)
        assertTrue(availability.videoCall)
        assertTrue(availability.groupCall)
    }

    @Test
    fun `redpacket entry follows bootstrap in supported conversations`() {
        assertTrue(
            XingDunRuntimeFeaturePolicy.chatAvailability(
                XingDunFeatures(redpacket = true),
                isDirectConversation = true,
                isGroupConversation = false,
            ).redpacket,
        )
        assertFalse(
            XingDunRuntimeFeaturePolicy.chatAvailability(
                XingDunFeatures(redpacket = false),
                isDirectConversation = true,
                isGroupConversation = false,
            ).redpacket,
        )
    }
}
