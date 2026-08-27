package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants
import java.util.Locale

private fun englishContext(base: Context): Context {
    val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.US) }
    return base.createConfigurationContext(configuration)
}

private fun applyEnglishAppLocale() {
    MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, "en")
    val locales = LocaleListCompat.forLanguageTags("en")
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

class XingDunGroupManagementEnglishPreviewActivity : XingDunGroupManagementActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(englishContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        applyEnglishAppLocale()
        intent.putExtra("group_id", "@TGS#debug-management-en")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}

class XingDunGroupAdministratorsEnglishPreviewActivity : XingDunGroupAdministratorsActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(englishContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        applyEnglishAppLocale()
        intent.putExtra("group_id", "@TGS#debug-administrators-en")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
