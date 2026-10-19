package io.trtc.tuikit.chat.demo.xingdun.routing

import android.app.PendingIntent
import android.content.Context
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator

/** Local notifications are immutable and tied to the account that received the event. */
internal object XingDunLocalNotificationRoutes {
    const val EXTRA_ACCOUNT = "xingdun.local_notification.account"

    fun accountKey(): String? = if (XingDunTenantSessionCoordinator.isLeaving()) null else XingDunRouter.currentAccountKey()

    fun isAuthenticatedAccount(expected: String): Boolean {
        val session = XingDunSessionManager.currentSession() ?: return false
        return expected == accountKey() && V2TIMManager.getInstance().loginStatus == V2TIMManager.V2TIM_STATUS_LOGINED &&
            session.sdkAppId == LoginStore.shared.sdkAppID && session.timUserId == TUILogin.getLoginUser()
    }

    fun contentIntent(context: Context, tag: String, conversationID: String, accountKey: String): PendingIntent? {
        if (accountKey != this.accountKey()) return null
        val destination = XingDunNotificationDestination.Conversation(conversationID)
        val payload = XingDunNotificationPayloadParser.encode(destination)
        if (XingDunNotificationPayloadParser.parse(payload) != destination) return null
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        // PendingIntent uniqueness must not use data: startup reserves data URIs for invitations.
        intent.action = context.packageName + ".LOCAL_NOTIFICATION." + tag
        intent.putExtra("ext", payload)
        intent.putExtra(EXTRA_ACCOUNT, accountKey)
        return PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
