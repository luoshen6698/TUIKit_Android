package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import java.util.concurrent.atomic.AtomicBoolean

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Constants.REJECT_CALL_ACTION && intent?.action != Constants.HANGUP_CALL_ACTION) return
        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)
        val finish = {
            if (finished.compareAndSet(false, true)) pendingResult.finish()
        }
        try {
            if (!IncomingNotificationBanner.handleNotificationAction(context, intent, finish)) finish()
        } catch (_: Exception) {
            finish()
        }
    }
}
