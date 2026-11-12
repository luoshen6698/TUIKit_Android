package io.trtc.tuikit.chat.demo.xingdun.push

import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunDeferredSdkInitializers

class XingDunVivoPushInitializer : com.tencent.timpush.vivo.TIMPushVIVOService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(8) { super.onCreate() }
        return true
    }
}
