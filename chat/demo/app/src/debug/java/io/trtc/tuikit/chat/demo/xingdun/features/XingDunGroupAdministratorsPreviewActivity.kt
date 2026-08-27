package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Bundle

class XingDunGroupAdministratorsPreviewActivity : XingDunGroupAdministratorsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra("group_id", "@TGS#debug-administrators")
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
