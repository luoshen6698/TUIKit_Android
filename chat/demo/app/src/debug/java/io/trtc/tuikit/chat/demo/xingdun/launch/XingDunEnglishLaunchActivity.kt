package io.trtc.tuikit.chat.demo.xingdun.launch

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Debug-only English context used for real-device login-screen localization checks. */
class XingDunEnglishLaunchActivity : XingDunLaunchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val languageTag = intent.getStringExtra(EXTRA_LANGUAGE_TAG)?.takeIf(String::isNotBlank) ?: "en"
        val targetLocales = LocaleListCompat.forLanguageTags(languageTag)
        if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        private const val EXTRA_LANGUAGE_TAG = "language_tag"
    }
}
