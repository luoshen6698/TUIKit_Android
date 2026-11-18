package io.trtc.tuikit.chat.demo.xingdun.push

import android.content.Context
import com.tencent.timpush.huawei.TIMPushHuaWeiService
import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunPrivacyConsentStore

private fun Context.hasPushConsent(): Boolean =
    XingDunPrivacyConsentStore(noBackupFilesDir).hasConsent()

class XingDunHuaweiPushMessageService : com.tencent.timpush.huawei.TIMPushHmsMessageService() {
    override fun onNewToken(token: String?) {
        if (hasPushConsent() && TIMPushHuaWeiService.getInstance() != null) super.onNewToken(token)
    }

    override fun onTokenError(error: Exception?) {
        if (hasPushConsent() && TIMPushHuaWeiService.getInstance() != null) super.onTokenError(error)
    }
}
