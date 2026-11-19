package io.trtc.tuikit.chat.demo.xingdun.session

import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunUserProfile
import kotlinx.coroutines.CancellationException
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig

internal object XingDunReadReceiptFeaturePolicy {
    fun resolve(
        configuration: XingDunBootstrapConfiguration?,
        persistedFallback: Boolean
    ): Boolean = configuration?.features?.readReceipt ?: persistedFallback
}

internal object XingDunReadReceiptFeatureSynchronizer {
    private var internalAccountScope: String? = null

    suspend fun refreshGroupViewer() {
        val owner = XingDunSessionManager.currentUiSession() ?: return
        val session = XingDunSessionManager.currentSession()?.takeIf { it.timUserId == owner.timUserId } ?: return
        val profile = try {
            XingDunSessionManager.apiClient().get<XingDunUserProfile>(
                session, "user/profile", emptyMap(), XingDunUserProfile::class.java
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (XingDunSessionManager.currentUiSession()?.scopeKey == owner.scopeKey) {
            internalAccountScope = owner.scopeKey.takeIf {
                profile?.timUserId == owner.timUserId && profile?.accountType == "internal"
            }
        }
    }

    fun apply(configuration: XingDunBootstrapConfiguration?) {
        val preferences = MMKV.defaultMMKV()
        val enabled = XingDunReadReceiptFeaturePolicy.resolve(
            configuration = configuration,
            persistedFallback = preferences.decodeBool(AppConstants.KEY_ENABLE_READ_RECEIPT, false)
        )
        AppBuilderConfig.enableReadReceipt = enabled
        AppBuilderConfig.canViewGroupReadReceipts = {
            val scope = XingDunSessionManager.currentUiSession()
            internalAccountScope != null && internalAccountScope == scope?.scopeKey
                && scope?.features?.readReceipt == true
        }
        preferences.encode(AppConstants.KEY_ENABLE_READ_RECEIPT, enabled)
    }

    fun reset() {
        internalAccountScope = null
        AppBuilderConfig.enableReadReceipt = false
        MMKV.defaultMMKV().encode(AppConstants.KEY_ENABLE_READ_RECEIPT, false)
    }
}
