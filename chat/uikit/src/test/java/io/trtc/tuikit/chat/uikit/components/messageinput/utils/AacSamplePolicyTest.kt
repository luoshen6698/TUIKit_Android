package io.trtc.tuikit.chat.uikit.components.messageinput.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AacSamplePolicyTest {
    @Test fun `only exact configuration sample is removed`() {
        val config = byteArrayOf(0x12, 0x10)
        assertTrue(AacSamplePolicy.isConfigurationSample(config, config.copyOf()))
        assertFalse(AacSamplePolicy.isConfigurationSample(config, byteArrayOf(0x21, 0x11, 0x45)))
        assertFalse(AacSamplePolicy.isConfigurationSample(config, byteArrayOf(0x12, 0x10, 0x00)))
        assertFalse(AacSamplePolicy.isConfigurationSample(byteArrayOf(), byteArrayOf()))
    }
}
