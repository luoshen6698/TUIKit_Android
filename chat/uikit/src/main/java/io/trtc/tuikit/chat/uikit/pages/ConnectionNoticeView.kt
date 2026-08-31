package io.trtc.tuikit.chat.uikit.pages

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal class ConnectionNoticeView(context: Context) : AppCompatTextView(context) {
    private var warning = false

    init {
        gravity = Gravity.CENTER_VERTICAL
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val density = resources.displayMetrics.density
        setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
        visibility = GONE
    }

    fun setNotice(message: CharSequence?, warning: Boolean, colors: ColorTokens) {
        this.warning = warning
        text = message
        visibility = if (message.isNullOrBlank()) GONE else VISIBLE
        applyColors(colors)
    }

    fun applyColors(colors: ColorTokens) {
        setTextColor(if (warning) colors.textColorWarning else colors.textColorPrimary)
        setBackgroundColor(if (warning) colors.toastColorWarning else colors.toastColorDefault)
    }
}
