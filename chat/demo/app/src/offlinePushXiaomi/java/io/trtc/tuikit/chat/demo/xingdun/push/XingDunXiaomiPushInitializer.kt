package io.trtc.tuikit.chat.demo.xingdun.push

import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunDeferredSdkInitializers

class XingDunXiaomiPushInitializer : com.tencent.timpush.xiaomi.TIMPushXiaoMiService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(6) { super.onCreate() }
        return true
    }
}
