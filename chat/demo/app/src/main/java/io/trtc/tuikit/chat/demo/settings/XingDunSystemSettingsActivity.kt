package io.trtc.tuikit.chat.demo.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.chat.demo.common.BaseActivity

/** Hosts the existing settings controls below the iOS-aligned My root page. */
class XingDunSystemSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        val page = SettingsPageView(this).apply {
            showAsSystemSettings(::finish)
        }
        setContentView(page)
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, XingDunSystemSettingsActivity::class.java))
        }
    }
}
