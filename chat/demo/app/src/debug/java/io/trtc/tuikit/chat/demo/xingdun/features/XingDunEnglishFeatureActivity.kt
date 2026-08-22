package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Debug-only English context used for real-device localization checks. */
class XingDunEnglishFeatureActivity : XingDunFeatureActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.US)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
}
