package io.trtc.tuikit.chat.demo.xingdun.legal

import android.content.Context
import com.tencent.qcloud.tuicore.ServiceInitializer

/** Keep the SDK providers attached normally, but defer their original onCreate until consent. */
object XingDunDeferredSdkInitializers {
    private val initializers = sortedMapOf<Int, () -> Unit>()

    internal fun register(order: Int, initialize: () -> Unit) {
        initializers[order] = initialize
    }

    fun initialize(context: Context) {
        check(XingDunPrivacyConsentStore(context.noBackupFilesDir).hasConsent())
        initializers.values.forEach { it() }
        initializers.clear()
    }
}

class XingDunCommonInitializer : com.trtc.tuikit.common.system.ContextProvider() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(0) { super.onCreate() }
        return true
    }
}

class XingDunEngineInitializer : com.tencent.cloud.tuikit.engine.common.ContextProvider() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(1) { super.onCreate() }
        return true
    }
}

class XingDunCoreInitializer : ServiceInitializer() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(2) { super.onCreate() }
        return true
    }
}
