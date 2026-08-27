package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants
import java.util.Locale

class XingDunPinnedMessagesPreviewActivity : XingDunPinnedMessagesActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyPinnedLocale("zh-Hans")
        intent.putExtra("conversation_id", "group_debug-pinned")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}

private fun pinnedEnglishContext(base: Context): Context {
    val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.US) }
    return base.createConfigurationContext(configuration)
}

private fun applyPinnedLocale(tag: String) {
    MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
    val locales = LocaleListCompat.forLanguageTags(tag)
    if (AppCompatDelegate.getApplicationLocales() != locales) AppCompatDelegate.setApplicationLocales(locales)
}

class XingDunPinnedMessagesEnglishPreviewActivity : XingDunPinnedMessagesActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(pinnedEnglishContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        applyPinnedLocale("en")
        intent.putExtra("conversation_id", "group_debug-pinned")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
