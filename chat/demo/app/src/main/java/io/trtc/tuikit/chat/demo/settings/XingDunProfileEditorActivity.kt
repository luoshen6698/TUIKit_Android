package io.trtc.tuikit.chat.demo.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** iOS-aligned child editor used by the profile page. It returns a value to the parent for saving. */
class XingDunProfileEditorActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var back: ImageView
    private lateinit var more: ImageView
    private lateinit var badge: FrameLayout
    private lateinit var left: LinearLayout
    private lateinit var divider: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var card: LinearLayout
    private lateinit var saveButton: TextView
    private var counter: TextView? = null
    private var editor: EditText? = null
    private var genderGroup: RadioGroup? = null
    private var datePicker: DatePicker? = null
    private var birthdayIsSet = true

    private val mode: String by lazy { intent.getStringExtra(EXTRA_MODE).orEmpty() }
    private val initialValue: String by lazy { intent.getStringExtra(EXTRA_VALUE).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        setContentView(R.layout.xingdun_activity_profile_editor)
        bindViews()
        configureHeader()
        buildContent()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) }
        }
    }

    override fun onDestroy() {
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.xingdun_profileEditorRoot)
        header = findViewById(R.id.demo_chatHeaderContainer)
        headerTitle = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        more = findViewById(R.id.demo_btnMore)
        badge = findViewById(R.id.demo_badgeContainer)
        left = findViewById(R.id.demo_leftContainer)
        divider = findViewById(R.id.demo_headerDivider)
        scroll = findViewById(R.id.xingdun_profileEditorScroll)
        content = findViewById(R.id.xingdun_profileEditorContent)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        headerTitle.setText(titleForMode())
        left.setOnClickListener { finish() }
        more.visibility = View.GONE
        badge.visibility = View.GONE
    }

    private fun buildContent() {
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
        }
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        when (mode) {
            MODE_NICKNAME -> buildTextEditor(limit = 64, multiline = false)
            MODE_ACCOUNT -> buildTextEditor(limit = 32, multiline = false)
            MODE_SIGNATURE -> buildTextEditor(limit = 255, multiline = true)
            MODE_GENDER -> buildGenderEditor()
            MODE_BIRTHDAY -> buildBirthdayEditor()
            else -> finish()
        }

        saveButton = TextView(this).apply {
            setText(R.string.xingdun_profile_save)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setOnClickListener { complete() }
        }
        content.addView(
            saveButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply { topMargin = 20.dp() },
        )
    }

    private fun buildTextEditor(limit: Int, multiline: Boolean) {
        editor = EditText(this).apply {
            setText(initialValue)
            setSelection(text.length)
            background = null
            filters = arrayOf(InputFilter.LengthFilter(limit))
            gravity = if (multiline) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
            minHeight = if (multiline) 150.dp() else 58.dp()
            inputType = when (mode) {
                MODE_ACCOUNT -> InputType.TYPE_CLASS_TEXT
                MODE_SIGNATURE -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            if (multiline) setPadding(0, 14.dp(), 0, 14.dp())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    counter?.text = getString(R.string.xingdun_profile_character_count, s?.length ?: 0, limit)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        card.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        counter = TextView(this).apply {
            text = getString(R.string.xingdun_profile_character_count, initialValue.length, limit)
            gravity = Gravity.END
            setPadding(0, 6.dp(), 0, 6.dp())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        card.addView(counter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildGenderEditor() {
        genderGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val values = listOf(
            "1" to R.string.demo_settings_self_detail_gender_male,
            "2" to R.string.demo_settings_self_detail_gender_female,
            "0" to R.string.xingdun_not_set,
        )
        values.forEach { (value, textRes) ->
            val option = RadioButton(this).apply {
                tag = value
                setText(textRes)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 56.dp()
                isChecked = value == initialValue.ifBlank { "0" }
            }
            genderGroup?.addView(option, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        card.addView(genderGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildBirthdayEditor() {
        val calendar = Calendar.getInstance()
        birthdayIsSet = initialValue.isNotBlank()
        runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(initialValue)
            if (parsed != null) calendar.time = parsed
        }
        datePicker = DatePicker(this).apply {
            maxDate = System.currentTimeMillis()
            init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)) { _, _, _, _ ->
                birthdayIsSet = true
            }
        }
        card.addView(datePicker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(TextView(this).apply {
            setText(R.string.xingdun_profile_clear_birthday)
            gravity = Gravity.CENTER
            setPadding(0, 14.dp(), 0, 14.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                birthdayIsSet = false
                Toast.makeText(this@XingDunProfileEditorActivity, R.string.xingdun_profile_birthday_cleared, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun complete() {
        val value = when (mode) {
            MODE_NICKNAME -> editor?.text?.toString()?.trim().orEmpty().also {
                if (it.isEmpty()) return showInvalid(R.string.xingdun_profile_nickname_required)
            }
            MODE_ACCOUNT -> editor?.text?.toString()?.trim().orEmpty().also {
                if (!it.matches(Regex("^[A-Za-z0-9_]{3,32}$"))) return showInvalid(R.string.xingdun_custom_id_invalid)
            }
            MODE_SIGNATURE -> editor?.text?.toString()?.trim().orEmpty()
            MODE_GENDER -> genderGroup?.findViewById<RadioButton>(genderGroup?.checkedRadioButtonId ?: View.NO_ID)?.tag?.toString() ?: "0"
            MODE_BIRTHDAY -> if (!birthdayIsSet) "" else datePicker?.let {
                "%04d-%02d-%02d".format(Locale.US, it.year, it.month + 1, it.dayOfMonth)
            }.orEmpty()
            else -> ""
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_MODE, mode).putExtra(EXTRA_VALUE, value))
        finish()
    }

    private fun showInvalid(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private fun titleForMode(): Int = when (mode) {
        MODE_NICKNAME -> R.string.demo_settings_self_detail_nickname
        MODE_ACCOUNT -> R.string.demo_settings_self_detail_account
        MODE_SIGNATURE -> R.string.demo_settings_self_detail_status
        MODE_GENDER -> R.string.demo_settings_self_detail_gender
        MODE_BIRTHDAY -> R.string.demo_settings_self_detail_birthday
        else -> R.string.demo_settings_self_detail_title
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        headerTitle.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        card.background = rounded(colors.bgColorOperate, 18f)
        editor?.setTextColor(colors.textColorPrimary)
        editor?.setHintTextColor(colors.textColorTertiary)
        counter?.setTextColor(colors.textColorTertiary)
        genderGroup?.let { group ->
            for (index in 0 until group.childCount) {
                (group.getChildAt(index) as? RadioButton)?.apply {
                    setTextColor(colors.textColorPrimary)
                    buttonTintList = ColorStateList.valueOf(BRAND)
                }
            }
        }
        saveButton.background = rounded(BRAND, 16f)
        saveButton.setTextColor(android.graphics.Color.WHITE)
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val MODE_NICKNAME = "nickname"
        const val MODE_ACCOUNT = "account"
        const val MODE_SIGNATURE = "signature"
        const val MODE_GENDER = "gender"
        const val MODE_BIRTHDAY = "birthday"
        const val EXTRA_MODE = "mode"
        const val EXTRA_VALUE = "value"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_profile_editor_preview"
        private const val BRAND = 0xFF23B39C.toInt()

        fun intent(context: Context, mode: String, value: String): Intent =
            Intent(context, XingDunProfileEditorActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_VALUE, value)
    }
}
