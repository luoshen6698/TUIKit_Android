package io.trtc.tuikit.chat.demo.xingdun.push

import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunDeferredSdkInitializers

/** Version-bound to TIMPush 9.0.7652; providers remain attached with the SDK's authorities. */
class XingDunAGConnectInitializer : com.huawei.agconnect.core.provider.AGConnectInitializeProvider() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(3) { super.onCreate() }
        return true
    }
}

class XingDunHuaweiPushInitializer : com.tencent.timpush.huawei.TIMPushHuaWeiService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(4) { super.onCreate() }
        return true
    }
}

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

class XingDunTIMPushInitializer : com.tencent.qcloud.tim.push.TIMPushEntry() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(9) { super.onCreate() }
        return true
    }
}

class XingDunHuaweiAAIDInitializer : com.huawei.hms.aaid.InitProvider() {
    override fun onCreate(): Boolean {
        // Auto-init can deliver a token immediately: its bridge must already be available.
        XingDunDeferredSdkInitializers.register(10) { super.onCreate() }
        return true
    }
}
