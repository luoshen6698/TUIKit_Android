package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

/** Field validation and floating feedback shared by the two reference auth forms. */
internal class TravelAuthForm(private val activity: Activity) {
    private val root = activity.findViewById<ViewGroup>(android.R.id.content)
    private val fields = mutableListOf<EditText>()

    init {
        fun collect(view: View) {
            if (view is EditText) fields += view
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        collect(root)
        fields.forEach { input ->
            input.textSize = 15f
            container(input)?.apply {
                setErrorIconDrawable(null)
                setErrorTextColor(ColorStateList.valueOf(ERROR))
                boxStrokeErrorColor = ColorStateList.valueOf(ERROR)
                setErrorTextAppearance(io.trtc.tuikit.chat.app.R.style.TravelAuthFieldError)
            }
            input.doAfterTextChanged { clearError(input) }
        }
    }

    fun clearErrors() = fields.forEach(::clearError)

    fun error(input: EditText, message: Int) {
        container(input)?.apply {
            error = activity.getString(message)
            findViewById<TextView>(com.google.android.material.R.id.textinput_error)?.maxLines = 3
        }
    }

    fun reveal(input: EditText) {
        input.requestFocus()
        container(input)?.post {
            container(input)?.let { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), true) }
        }
    }

    fun hideKeyboard() {
        activity.currentFocus?.let {
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    fun message(text: String, isError: Boolean = false) = Companion.message(activity, text, isError)

    private fun clearError(input: EditText) { container(input)?.apply { error = null; isErrorEnabled = false } }

    companion object {
        fun message(activity: Activity, text: String, isError: Boolean = false) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
            if (activity.isFinishing || activity.isDestroyed || text.isBlank()) return
            val color = if (isError) 0xFFEF4444.toInt() else 0xFF22C55E.toInt()
            Snackbar.make(root, text, Snackbar.LENGTH_LONG).apply {
                view.background = GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat() }
                (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.leftMargin = dp(16); params.rightMargin = dp(16); params.bottomMargin += dp(16)
                    view.layoutParams = params
                }
                view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
                    textSize = 12f; maxLines = 4; setTextColor(Color.WHITE)
                }
                show()
            }
        }


        private const val ERROR = 0xFFB3261E.toInt()
        fun container(input: EditText): TextInputLayout? {
            var parent = input.parent
            while (parent is View) {
                if (parent is TextInputLayout) return parent
                parent = (parent as View).parent
            }
            return null
        }
    }
}
