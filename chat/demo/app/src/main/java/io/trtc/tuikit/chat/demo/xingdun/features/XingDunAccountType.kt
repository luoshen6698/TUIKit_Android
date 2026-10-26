package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.app.R

/** Read-only business classification. Never use it for role or customer-service authorization. */
internal object XingDunAccountType {
    fun label(value: String?): Int = when (value) {
        "normal" -> R.string.xingdun_account_type_normal
        "internal" -> R.string.xingdun_account_type_internal
        else -> R.string.xingdun_account_type_unknown
    }
}
