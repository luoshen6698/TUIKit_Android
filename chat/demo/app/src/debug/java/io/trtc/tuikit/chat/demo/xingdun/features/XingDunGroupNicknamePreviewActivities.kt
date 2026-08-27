package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants
import java.util.Locale

class XingDunGroupNicknamePreviewActivity : XingDunGroupNicknameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyNicknameLocale("zh-Hans")
        intent.putExtra("group_id", "@TGS#debug-nickname")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}

private fun nicknameEnglishContext(base: Context): Context {
    val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.US) }
    return base.createConfigurationContext(configuration)
}

private fun applyNicknameLocale(tag: String) {
    MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
    val locales = LocaleListCompat.forLanguageTags(tag)
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

class XingDunGroupNicknameEnglishPreviewActivity : XingDunGroupNicknameActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(nicknameEnglishContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        applyNicknameLocale("en")
        intent.putExtra("group_id", "@TGS#debug-nickname-en")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
