package io.trtc.tuikit.chat.demo.chat

import android.os.Bundle

class XingDunC2CChatSettingPreviewActivity : ChatSettingActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra("user_id", "x001")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
