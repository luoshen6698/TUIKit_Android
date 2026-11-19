package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunNotificationIntentReader

/** Routes desktop launches separately from Android restoring the app in Recents. */
class XingDunLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (XingDunNotificationIntentReader.hasPayloadExtras(intent)) {
            // Existing local PendingIntents and vendor "open application" actions can still target this component.
            startActivity(Intent(this, XingDunStartupActivity::class.java).apply {
                // Android compares action but not extras when it decides to just resume a task.
                action = "$packageName.FORWARDED_NOTIFICATION.${SystemClock.elapsedRealtimeNanos()}"
                putExtras(intent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
        } else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            val main = MainActivity.reuseMainIntent(this, MainActivity.TAB_HOME)
            val previousTask = if (main == null) existingAppTask() else null
            if (previousTask != null) {
                previousTask.moveToFront()
            } else {
                val target = main ?: Intent(this, XingDunStartupActivity::class.java)
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun existingAppTask(): ActivityManager.AppTask? =
        getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull {
            val info = it.taskInfo
            info.id != taskId && info.baseIntent.component?.packageName == packageName
        }
}
