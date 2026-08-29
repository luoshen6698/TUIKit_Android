package io.trtc.tuikit.chat.demo.search

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.common.AppConstants

class XingDunConversationSearchPreviewActivity : ConversationSearchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, "zh-Hans")
        val locales = LocaleListCompat.forLanguageTags("zh-Hans")
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
        intent.putExtra("conversation_id", "group_debug-history")
        intent.putExtra("display_name", "项目群")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
