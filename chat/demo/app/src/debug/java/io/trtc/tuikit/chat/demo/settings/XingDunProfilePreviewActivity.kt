package io.trtc.tuikit.chat.demo.settings

import android.os.Bundle

/** Debug-only profile fixture used for screenshot and basic navigation checks without a live session. */
open class XingDunProfilePreviewActivity : SelfDetailActivity() {
    override val requiresLogin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(EXTRA_DEBUG_PREVIEW, true)
        super.onCreate(savedInstanceState)
    }
}
