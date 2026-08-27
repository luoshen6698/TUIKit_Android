package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants
import java.util.Locale

class XingDunGroupMembersPreviewActivity : XingDunGroupMembersActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyGroupChineseAppLocale()
        intent.putExtra("group_id", "@TGS#debug-members")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}

class XingDunGroupTransferOwnerPreviewActivity : XingDunGroupTransferOwnerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyGroupChineseAppLocale()
        intent.putExtra("group_id", "@TGS#debug-transfer-owner")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}

private fun groupEnglishContext(base: Context): Context {
    val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.US) }
    return base.createConfigurationContext(configuration)
}

private fun applyGroupEnglishAppLocale() {
    MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, "en")
    val locales = LocaleListCompat.forLanguageTags("en")
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

private fun applyGroupChineseAppLocale() {
    MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, "zh-Hans")
    val locales = LocaleListCompat.forLanguageTags("zh-Hans")
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

class XingDunGroupMembersEnglishPreviewActivity : XingDunGroupMembersActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(groupEnglishContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        applyGroupEnglishAppLocale()
        intent.putExtra("group_id", "@TGS#debug-members-en")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}

class XingDunGroupTransferOwnerEnglishPreviewActivity : XingDunGroupTransferOwnerActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(groupEnglishContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        applyGroupEnglishAppLocale()
        intent.putExtra("group_id", "@TGS#debug-transfer-owner-en")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
