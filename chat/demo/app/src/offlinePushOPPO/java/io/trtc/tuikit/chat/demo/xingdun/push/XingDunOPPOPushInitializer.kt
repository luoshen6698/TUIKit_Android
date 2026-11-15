package io.trtc.tuikit.chat.demo.xingdun.push

import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunDeferredSdkInitializers

class XingDunOPPOPushInitializer : com.tencent.timpush.oppo.TIMPushOPPOService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(7) { super.onCreate() }
        return true
    }
}
