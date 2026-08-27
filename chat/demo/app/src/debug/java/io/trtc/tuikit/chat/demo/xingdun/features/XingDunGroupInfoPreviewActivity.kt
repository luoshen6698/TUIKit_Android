package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Bundle

/** Debug-only screenshot entry. It never loads or mutates a real group. */
class XingDunGroupInfoPreviewActivity : XingDunGroupInfoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        intent.putExtra("group_id", "@TGS#debug-private-id")
        super.onCreate(savedInstanceState)
    }
}
