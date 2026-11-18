package io.trtc.tuikit.chat.demo.xingdun.push

import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunDeferredSdkInitializers
import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunPrivacyConsentStore

class XingDunHonorPushInitializer : com.tencent.timpush.honor.TIMPushHonorService() {
    override fun onCreate(): Boolean {
        XingDunDeferredSdkInitializers.register(5) { super.onCreate() }
        return true
    }
}

class XingDunHonorPushMessageService : com.tencent.timpush.honor.TIMPushHonorMessageService() {
    override fun onNewToken(token: String?) {
        if (XingDunPrivacyConsentStore(noBackupFilesDir).hasConsent() &&
            com.tencent.timpush.honor.TIMPushHonorService.getInstance() != null) {
            super.onNewToken(token)
        }
    }
}
