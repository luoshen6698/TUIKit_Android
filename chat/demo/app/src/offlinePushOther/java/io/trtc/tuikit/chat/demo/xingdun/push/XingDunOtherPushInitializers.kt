package io.trtc.tuikit.chat.demo.xingdun.push

import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunDeferredSdkInitializers

class XingDunHonorPushInitializer : com.tencent.timpush.honor.TIMPushHonorService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(5) { super.onCreate() }
        return true
    }
}

class XingDunXiaomiPushInitializer : com.tencent.timpush.xiaomi.TIMPushXiaoMiService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(6) { super.onCreate() }
        return true
    }
}

class XingDunOPPOPushInitializer : com.tencent.timpush.oppo.TIMPushOPPOService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(7) { super.onCreate() }
        return true
    }
}

class XingDunVivoPushInitializer : com.tencent.timpush.vivo.TIMPushVIVOService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(8) { super.onCreate() }
        return true
    }
}
