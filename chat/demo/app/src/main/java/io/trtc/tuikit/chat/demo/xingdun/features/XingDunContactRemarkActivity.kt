package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult

/** Full-screen counterpart of iOS `XingDunContactRemarkEditor`. */
class XingDunContactRemarkActivity : BaseActivity() {
    private lateinit var input: EditText
    private lateinit var save: TextView
    private lateinit var helper: TextView
    private var isSaving = false

    private val timUserID: String by lazy { intent.getStringExtra(EXTRA_USER_ID).orEmpty().trim() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (timUserID.isEmpty()) {
            finish()
            return
        }
        buildPage()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BACKGROUND)
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp()))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 10.dp(), 16.dp(), 12.dp())
            background = rounded(Color.WHITE, 16f)
        }
        input = EditText(this).apply {
            setText(intent.getStringExtra(EXTRA_REMARK).orEmpty())
            hint = getString(R.string.xingdun_contact_detail_remark_hint)
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_TERTIARY)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            backgroundTintList = ColorStateList.valueOf(DIVIDER)
            setSelection(text.length)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateLimitState()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        card.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
        helper = TextView(this).apply {
            setText(R.string.xingdun_contact_detail_remark_help)
            textSize = 12f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, 8.dp(), 0, 0)
        }
        card.addView(helper)
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = 16.dp()
            marginEnd = 16.dp()
            topMargin = 16.dp()
        })

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        updateLimitState()
    }

    private fun header(): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(Color.WHITE)
        setPadding(12.dp(), 0, 12.dp(), 0)
        addView(navButton(R.string.xingdun_cancel) { finish() }, FrameLayout.LayoutParams(72.dp(), 42.dp(), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(TextView(context).apply {
            setText(R.string.xingdun_contact_detail_set_remark)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginStart = 78.dp()
            marginEnd = 78.dp()
        })
        save = navButton(R.string.xingdun_contact_detail_save) { saveRemark() }
        addView(save, FrameLayout.LayoutParams(72.dp(), 42.dp(), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    private fun navButton(label: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        setText(label)
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(BRAND)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun updateLimitState() {
        if (!::save.isInitialized || !::helper.isInitialized) return
        val valid = input.text.toString().toByteArray(Charsets.UTF_8).size <= MAX_REMARK_BYTES
        save.isEnabled = valid && !isSaving
        save.alpha = if (save.isEnabled) 1f else 0.45f
        helper.setText(if (valid) R.string.xingdun_contact_detail_remark_help else R.string.xingdun_contact_detail_remark_too_long)
        helper.setTextColor(if (valid) TEXT_SECONDARY else ERROR)
    }

    private fun saveRemark() {
        if (isSaving) return
        val remark = input.text.toString()
        if (remark.toByteArray(Charsets.UTF_8).size > MAX_REMARK_BYTES) return
        setSaving(true)
        val success = {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_REMARK, remark))
            finish()
        }
        val failure = { _: Int, description: String ->
            setSaving(false)
            Toast.makeText(
                this,
                description.trim().ifEmpty { getString(R.string.xingdun_contact_detail_remark_update_failed) },
                Toast.LENGTH_SHORT,
            ).show()
        }
        val dispatched = BusinessActionRegistry.dispatch(
            BusinessAction.SetFriendRemark(timUserID, remark),
            object : BusinessActionCompletion {
                override fun onSuccess(result: BusinessActionResult) = success()
                override fun onFailure(code: Int, description: String) = failure(code, description)
            },
        )
        if (!dispatched) {
            ContactStore.shared.setFriendRemark(timUserID, remark, object : CompletionHandler {
                override fun onSuccess() = success()
                override fun onFailure(code: Int, desc: String) = failure(code, desc)
            })
        }
    }

    private fun setSaving(saving: Boolean) {
        isSaving = saving
        input.isEnabled = !saving
        save.setText(if (saving) R.string.xingdun_profile_saving else R.string.xingdun_contact_detail_save)
        updateLimitState()
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp().toFloat()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_RESULT_REMARK = "remark_result"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_REMARK = "remark"
        private const val MAX_REMARK_BYTES = 96
        private const val PAGE_BACKGROUND = 0xFFF5F4F8.toInt()
        private const val BRAND = 0xFF23B39C.toInt()
        private const val TEXT_PRIMARY = 0xFF111827.toInt()
        private const val TEXT_SECONDARY = 0xFF8B9098.toInt()
        private const val TEXT_TERTIARY = 0xFFB6BBC2.toInt()
        private const val DIVIDER = 0xFFE5E7EB.toInt()
        private const val ERROR = 0xFFE34D59.toInt()

        fun intent(context: Context, timUserID: String, remark: String): Intent =
            Intent(context, XingDunContactRemarkActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, timUserID)
                putExtra(EXTRA_REMARK, remark)
            }
    }
}
