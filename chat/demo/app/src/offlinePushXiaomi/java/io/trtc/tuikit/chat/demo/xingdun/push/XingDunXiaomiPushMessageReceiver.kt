package io.trtc.tuikit.chat.demo.xingdun.push

import android.content.Context
import com.tencent.timpush.xiaomi.TIMPushXiaoMiService
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunPrivacyConsentStore

private fun Context.hasPushConsent(): Boolean =
    XingDunPrivacyConsentStore(noBackupFilesDir).hasConsent()

class XingDunXiaomiPushMessageReceiver : com.tencent.timpush.xiaomi.TIMPushXiaoMiMsgReceiver() {
    override fun onReceiveRegisterResult(context: Context, message: MiPushCommandMessage?) {
        if (context.hasPushConsent() && TIMPushXiaoMiService.getInstance() != null) {
            super.onReceiveRegisterResult(context, message)
        }
    }
}
