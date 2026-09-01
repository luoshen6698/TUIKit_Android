package io.trtc.tuikit.chat.demo.xingdun.session

import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig

internal object XingDunReadReceiptFeaturePolicy {
    fun resolve(
        configuration: XingDunBootstrapConfiguration?,
        persistedFallback: Boolean
    ): Boolean = configuration?.features?.readReceipt ?: persistedFallback
}

internal object XingDunReadReceiptFeatureSynchronizer {
    fun apply(configuration: XingDunBootstrapConfiguration?) {
        val preferences = MMKV.defaultMMKV()
        val enabled = XingDunReadReceiptFeaturePolicy.resolve(
            configuration = configuration,
            persistedFallback = preferences.decodeBool(AppConstants.KEY_ENABLE_READ_RECEIPT, false)
        )
        AppBuilderConfig.enableReadReceipt = enabled
        preferences.encode(AppConstants.KEY_ENABLE_READ_RECEIPT, enabled)
    }

    fun reset() {
        AppBuilderConfig.enableReadReceipt = false
        MMKV.defaultMMKV().encode(AppConstants.KEY_ENABLE_READ_RECEIPT, false)
    }
}
