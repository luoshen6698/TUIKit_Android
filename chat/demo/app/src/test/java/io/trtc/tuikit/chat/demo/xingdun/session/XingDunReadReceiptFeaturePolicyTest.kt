package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunReadReceiptFeaturePolicyTest {
    @Test
    fun `enterprise feature overrides a stale disabled preference`() {
        val configuration = XingDunBootstrapConfiguration(
            features = XingDunFeatures(readReceipt = true)
        )

        assertTrue(XingDunReadReceiptFeaturePolicy.resolve(configuration, persistedFallback = false))
    }

    @Test
    fun `enterprise feature overrides a stale enabled preference`() {
        val configuration = XingDunBootstrapConfiguration(
            features = XingDunFeatures(readReceipt = false)
        )

        assertFalse(XingDunReadReceiptFeaturePolicy.resolve(configuration, persistedFallback = true))
    }

    @Test
    fun `cold start without an enterprise keeps the persisted fallback`() {
        assertTrue(XingDunReadReceiptFeaturePolicy.resolve(configuration = null, persistedFallback = true))
        assertFalse(XingDunReadReceiptFeaturePolicy.resolve(configuration = null, persistedFallback = false))
    }
}
