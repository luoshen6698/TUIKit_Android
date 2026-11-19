package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.trtc.tuikit.chat.demo.main.MainActivity

/** Routes the desktop icon without presenting a window over the existing task. */
class XingDunLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            val target = MainActivity.resumeFromLauncher(this)
                ?: if (isTaskRoot) Intent(this, XingDunStartupActivity::class.java) else null
            if (target != null) {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
