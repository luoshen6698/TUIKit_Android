package io.trtc.tuikit.chat.demo.xingdun.push

import android.content.Context
import com.tencent.timpush.honor.TIMPushHonorService
import com.tencent.timpush.huawei.TIMPushHuaWeiService
import com.tencent.timpush.xiaomi.TIMPushXiaoMiService
import com.xiaomi.mipush.sdk.MiPushCommandMessage
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

class XingDunHonorPushMessageService : com.tencent.timpush.honor.TIMPushHonorMessageService() {
    override fun onNewToken(token: String?) {
        if (hasPushConsent() && TIMPushHonorService.getInstance() != null) super.onNewToken(token)
    }
}

class XingDunXiaomiPushMessageReceiver : com.tencent.timpush.xiaomi.TIMPushXiaoMiMsgReceiver() {
    override fun onReceiveRegisterResult(context: Context, message: MiPushCommandMessage?) {
        if (context.hasPushConsent() && TIMPushXiaoMiService.getInstance() != null) {
            super.onReceiveRegisterResult(context, message)
        }
    }
}
