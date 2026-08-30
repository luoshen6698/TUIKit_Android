package io.trtc.tuikit.chat.demo.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunChildBottomNavigation
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
    private lateinit var bottomNavigation: XingDunChildBottomNavigation
    private lateinit var card: LinearLayout
    private var counter: TextView? = null
    private var editor: EditText? = null
    private var editorLimit: Int = 0
    private var selectedGenderValue = "0"
    private val genderOptionTitles = mutableListOf<TextView>()
    private val genderOptionChecks = mutableListOf<TextView>()
    private val genderOptionDividers = mutableListOf<View>()
    private var datePicker: DatePicker? = null
    private var birthdayIsSet = true
    private var isCompleting = false

    private val mode: String by lazy { intent.getStringExtra(EXTRA_MODE).orEmpty() }
    private val initialValue: String by lazy { intent.getStringExtra(EXTRA_VALUE).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        setContentView(R.layout.xingdun_activity_profile_editor)
        bindViews()
        configureHeader()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = complete()
        })
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
        bottomNavigation = findViewById(R.id.xingdun_profileEditorBottomNavigation)
        bottomNavigation.bind(this, MainActivity.TAB_PROFILE)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            root.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        headerTitle.setText(titleForMode())
        left.setOnClickListener { complete() }
        more.visibility = View.GONE
        badge.visibility = View.GONE
    }

    private fun buildContent() {
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 0, 16.dp(), 0)
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

    }

    private fun buildTextEditor(limit: Int, multiline: Boolean) {
        editorLimit = limit
        editor = EditText(this).apply {
            setText(initialValue)
            setSelection(text.length)
            background = null
            filters = if (mode == MODE_NICKNAME) emptyArray() else arrayOf(InputFilter.LengthFilter(limit))
            gravity = if (multiline) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
            minHeight = if (multiline) 130.dp() else 48.dp()
            inputType = when (mode) {
                MODE_ACCOUNT -> InputType.TYPE_CLASS_TEXT
                MODE_SIGNATURE -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            if (!multiline) {
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        complete()
                        true
                    } else {
                        false
                    }
                }
            }
            if (multiline) setPadding(0, 14.dp(), 0, 14.dp())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateCounter(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        if (mode == MODE_ACCOUNT) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(editor, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.xingdun_ic_copy)
                imageTintList = ColorStateList.valueOf(BRAND)
                contentDescription = getString(R.string.xingdun_profile_copy_account)
                setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val value = editor?.text?.toString().orEmpty()
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                        ClipData.newPlainText(getString(R.string.demo_settings_self_detail_account), value)
                    )
                    Toast.makeText(
                        this@XingDunProfileEditorActivity,
                        R.string.xingdun_profile_account_copied,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }, LinearLayout.LayoutParams(44.dp(), 44.dp()))
            card.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        } else {
            card.addView(
                editor,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        if (mode != MODE_ACCOUNT) {
            val counterView = TextView(this).apply {
                text = getString(R.string.xingdun_profile_character_count, characterCount(initialValue), limit)
                gravity = Gravity.END
                setPadding(0, 6.dp(), 4.dp(), 6.dp())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            counter = counterView
            content.addView(
                counterView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        if (mode == MODE_ACCOUNT) {
            content.addView(TextView(this).apply {
                setText(R.string.xingdun_profile_account_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(12.dp(), 8.dp(), 12.dp(), 6.dp())
                setTextColor(themeStore.themeState.value.currentTheme.tokens.color.textColorSecondary)
            })
        }
    }

    private fun buildGenderEditor() {
        selectedGenderValue = initialValue.ifBlank { "0" }
        val values = listOf(
            "0" to R.string.xingdun_not_set,
            "1" to R.string.demo_settings_self_detail_gender_male,
            "2" to R.string.demo_settings_self_detail_gender_female,
        )
        values.forEachIndexed { index, (value, textRes) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = 48.dp()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedGenderValue = value
                    complete()
                }
            }
            val title = TextView(this).apply {
                setText(textRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER_VERTICAL
            }
            val check = TextView(this).apply {
                text = "✓"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                visibility = if (value == selectedGenderValue) View.VISIBLE else View.INVISIBLE
            }
            genderOptionTitles += title
            genderOptionChecks += check
            row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            row.addView(check, LinearLayout.LayoutParams(32.dp(), ViewGroup.LayoutParams.MATCH_PARENT))
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
            if (index < values.lastIndex) {
                val dividerView = View(this)
                genderOptionDividers += dividerView
                card.addView(dividerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()))
            }
        }
    }

    private fun buildBirthdayEditor() {
        val calendar = Calendar.getInstance().apply {
            set(1990, Calendar.JANUARY, 1)
        }
        birthdayIsSet = initialValue.isNotBlank()
        runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(initialValue)
            if (parsed != null) calendar.time = parsed
        }
        datePicker = DatePicker(ContextThemeWrapper(this, R.style.XingDunDatePickerTheme)).apply {
            maxDate = System.currentTimeMillis()
            visibility = if (birthdayIsSet) View.VISIBLE else View.GONE
            init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)) { _, _, _, _ ->
                birthdayIsSet = true
            }
        }
        card.addView(Switch(this).apply {
            setText(R.string.xingdun_profile_set_birthday)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = 56.dp()
            isChecked = birthdayIsSet
            applyXingDunSwitchTint()
            setOnCheckedChangeListener { _, checked ->
                birthdayIsSet = checked
                datePicker?.visibility = if (checked) View.VISIBLE else View.GONE
            }
        })
        card.addView(datePicker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun complete() {
        if (isCompleting) return
        val value = when (mode) {
            MODE_NICKNAME -> editor?.text?.toString()?.trim().orEmpty().also {
                if (it.isEmpty()) return showInvalid(R.string.xingdun_profile_nickname_required)
                if (characterCount(it) > 64) return showInvalid(R.string.xingdun_nickname_too_long)
            }
            MODE_ACCOUNT -> editor?.text?.toString()?.trim().orEmpty().also {
                if (!it.matches(Regex("^[A-Za-z0-9_]{3,32}$"))) return showInvalid(R.string.xingdun_custom_id_invalid)
            }
            MODE_SIGNATURE -> editor?.text?.toString()?.trim().orEmpty()
            MODE_GENDER -> selectedGenderValue
            MODE_BIRTHDAY -> if (!birthdayIsSet) "" else datePicker?.let {
                "%04d-%02d-%02d".format(Locale.US, it.year, it.month + 1, it.dayOfMonth)
            }.orEmpty()
            else -> ""
        }
        isCompleting = true
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
        updateCounter(editor?.text?.toString().orEmpty(), colors)
        genderOptionTitles.forEach { it.setTextColor(colors.textColorPrimary) }
        genderOptionChecks.forEach { it.setTextColor(BRAND) }
        genderOptionDividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun updateCounter(value: String, colors: ColorTokens = themeStore.themeState.value.currentTheme.tokens.color) {
        val count = characterCount(value)
        counter?.text = getString(R.string.xingdun_profile_character_count, count, editorLimit)
        counter?.setTextColor(
            if (mode == MODE_NICKNAME && count > editorLimit) colors.textColorError else colors.textColorTertiary,
        )
    }

    private fun characterCount(value: String): Int = value.codePointCount(0, value.length)

    private fun Switch.applyXingDunSwitchTint() {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        )
        thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
        trackTintList = ColorStateList(states, intArrayOf(BRAND, 0xFFBDBDC2.toInt()))
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
