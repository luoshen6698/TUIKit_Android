package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi

/** Native counterpart of the reference EditBottomSheet.showText(lockWhileSaving: true). */
internal class XingDunCompanyCodeSheet(baseContext: Context, initialValue: String) : BottomSheetDialog(baseContext, R.style.TravelCompanySheet) {
    var onConfirm: ((String) -> Unit)? = null
    private val ui = TravelUi(context)
    private val primary = 0xFF4C6EF5.toInt()
    private var initialKeyboardRequested = false
    private val input = TextInputEditText(context).apply {
        id = R.id.travel_company_input
        hint = context.getString(R.string.ta_code_prompt)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(InputFilter.LengthFilter(20))
        background = null
        textSize = 12f
        setTextColor(0xFF1A1A2E.toInt())
        setHintTextColor(0xFF9CA3AF.toInt())
        setPadding(ui.d(16), ui.d(12), ui.d(16), ui.d(12))
        minHeight = ui.d(48)
        setText(initialValue)
        setSelection(text?.length ?: 0)
    }
    private val field = TextInputLayout(context).apply {
        id = R.id.travel_company_field
        isHintEnabled = false
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxBackgroundColor = 0xFFF1F3F9.toInt()
        boxStrokeWidth = 0
        boxStrokeWidthFocused = maxOf(1, (resources.displayMetrics.density * 1.5f + .5f).toInt())
        boxStrokeColor = primary
        setBoxCornerRadii(ui.d(12).toFloat(), ui.d(12).toFloat(), ui.d(12).toFloat(), ui.d(12).toFloat())
        endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        setEndIconTintList(ColorStateList.valueOf(0xFF9CA3AF.toInt()))
        setEndIconContentDescription(R.string.ta_company_clear)
        setErrorIconDrawable(null)
        setErrorTextColor(ColorStateList.valueOf(0xFFEF4444.toInt()))
        boxStrokeErrorColor = ColorStateList.valueOf(0xFFEF4444.toInt())
        isCounterEnabled = true
        counterMaxLength = 20
        counterTextColor = ColorStateList.valueOf(0xFF9CA3AF.toInt())
        addView(input, LinearLayout.LayoutParams(-1, -2))
    }
    private fun action(text: Int, color: Int) = Button(context).apply {
        setText(text)
        textSize = 12f
        isAllCaps = false
        setTextColor(color)
        setBackgroundColor(Color.TRANSPARENT)
        gravity = Gravity.CENTER
        minimumWidth = 0
        minimumHeight = 0
        minWidth = 0
        minHeight = 0
        setPadding(ui.d(8), 0, ui.d(8), 0)
    }
    private val cancel = action(R.string.ta_company_cancel, 0xFF6B7280.toInt()).apply {
        id = R.id.travel_company_cancel
        setOnClickListener { dismiss() }
    }
    private val confirm = action(R.string.ta_company_confirm, primary).apply {
        id = R.id.travel_company_confirm
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(typeface, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setOnClickListener { onConfirm?.invoke(input.text.toString()) }
    }
    private val progress = ProgressBar(context).apply {
        id = R.id.travel_company_progress
        indeterminateTintList = ColorStateList.valueOf(primary)
        contentDescription = context.getString(R.string.xingdun_enterprise_connecting)
        visibility = View.GONE
    }

    init {
        setCanceledOnTouchOutside(false)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(ui.d(20).toFloat(), ui.d(20).toFloat(), ui.d(20).toFloat(), ui.d(20).toFloat(), 0f, 0f, 0f, 0f)
            }
            clipToOutline = true
        }
        panel.addView(FrameLayout(context).apply {
            addView(View(context).apply {
                background = GradientDrawable().apply { setColor(0xFFE5E7EB.toInt()); cornerRadius = ui.d(2).toFloat() }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(ui.d(36), ui.d(4), Gravity.CENTER))
        }, LinearLayout.LayoutParams(-1, ui.d(48)))
        panel.addView(FrameLayout(context).apply {
            setPadding(ui.d(8), ui.d(4), ui.d(8), ui.d(8))
            addView(TextView(context).apply {
                id = R.id.travel_company_title
                setText(R.string.ta_select_company)
                textSize = 12f
                setTextColor(0xFF1A1A2E.toInt())
                typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(typeface, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, ui.d(48)).apply { marginStart = ui.d(64); marginEnd = ui.d(64) })
            addView(cancel, FrameLayout.LayoutParams(ui.d(64), ui.d(48), Gravity.START or Gravity.TOP))
            addView(FrameLayout(context).apply {
                addView(confirm, FrameLayout.LayoutParams(-1, -1))
                addView(progress, FrameLayout.LayoutParams(ui.d(20), ui.d(20), Gravity.CENTER))
            }, FrameLayout.LayoutParams(ui.d(64), ui.d(48), Gravity.END or Gravity.TOP))
        }, LinearLayout.LayoutParams(-1, ui.d(60)))
        panel.addView(View(context).apply { setBackgroundColor(0xFFF0F1F5.toInt()) }, LinearLayout.LayoutParams(-1, maxOf(1, (context.resources.displayMetrics.density * .5f + .5f).toInt())))
        panel.addView(field, LinearLayout.LayoutParams(-1, -2).apply {
            marginStart = ui.d(20); marginEnd = ui.d(20); topMargin = ui.d(16); bottomMargin = ui.d(8)
        })
        input.doAfterTextChanged { field.error = null }
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val bottom = if (insets.isVisible(WindowInsetsCompat.Type.ime())) 0 else insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, 0, 0, bottom + ui.d(8))
            insets
        }
        setContentView(ScrollView(context).apply { isFillViewport = false; addView(panel) })
        setOnShowListener {
            findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)
            behavior.isDraggable = false
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            window?.apply {
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                navigationBarColor = Color.WHITE
            }
            input.requestFocus()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isShowing && !initialKeyboardRequested) {
            initialKeyboardRequested = true
            input.requestFocus()
            window?.let { WindowCompat.getInsetsController(it, input).show(WindowInsetsCompat.Type.ime()) }
        }
    }

    fun showError(message: Int) { field.error = context.getString(message) }

    fun setSaving(saving: Boolean) {
        input.isEnabled = !saving
        cancel.isEnabled = !saving
        confirm.isEnabled = !saving
        confirm.visibility = if (saving) View.INVISIBLE else View.VISIBLE
        progress.visibility = if (saving) View.VISIBLE else View.GONE
        field.isEndIconVisible = !saving && !input.text.isNullOrEmpty()
        setCancelable(!saving)
        field.error = null
    }
}
