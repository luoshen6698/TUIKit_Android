package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Bundle

/** Debug-only fixture entry used for visual comparison without mutating a real group. */
class XingDunGroupManagementPreviewActivity : XingDunGroupManagementActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra("group_id", "@TGS#debug-management")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
