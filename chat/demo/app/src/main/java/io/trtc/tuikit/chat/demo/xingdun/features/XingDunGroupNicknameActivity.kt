package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GetMemberInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Independent group-name-card editor aligned with the active iOS page. */
open class XingDunGroupNicknameActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !isDebugPreview

    private val groupID by lazy { intent.getStringExtra(EXTRA_GROUP_ID).orEmpty() }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private val themeStore by lazy { ThemeStore.shared(this) }
    private val memberStore by lazy { GroupMemberStore.create(groupID) }
    private var activityScope: CoroutineScope? = null

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: ImageView
    private lateinit var more: ImageView
    private lateinit var badge: FrameLayout
    private lateinit var left: LinearLayout
    private lateinit var divider: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var save: TextView
    private lateinit var card: LinearLayout
    private lateinit var editor: EditText
    private lateinit var byteCounter: TextView
    private lateinit var hint: TextView
    private lateinit var status: TextView
    private lateinit var retry: Button
    private lateinit var progress: ProgressBar

    private var isLoading = false
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (groupID.isBlank()) {
            Toast.makeText(this, R.string.xingdun_invalid_group, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.xingdun_activity_profile_editor)
        bindViews()
        configureHeader()
        buildContent()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) }
        }
        if (isDebugPreview) {
            showEditor(getString(R.string.xingdun_group_nickname_preview_value))
        } else {
            load()
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
        title = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        more = findViewById(R.id.demo_btnMore)
        badge = findViewById(R.id.demo_badgeContainer)
        left = findViewById(R.id.demo_leftContainer)
        divider = findViewById(R.id.demo_headerDivider)
        scroll = findViewById(R.id.xingdun_profileEditorScroll)
        content = findViewById(R.id.xingdun_profileEditorContent)
        findViewById<View>(R.id.xingdun_profileEditorBottomNavigation).visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        title.setText(R.string.xingdun_group_nickname_title)
        left.setOnClickListener { if (!isSaving) finish() }
        badge.visibility = View.GONE
        more.visibility = View.GONE
        save = TextView(this).apply {
            setText(R.string.xingdun_group_nickname_save)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(12.dp(), 0, 0, 0)
            setOnClickListener { save() }
        }
        (more.parent as FrameLayout).addView(
            save,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END),
        )
    }

    private fun buildContent() {
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 12.dp())
        }
        card.addView(TextView(this).apply {
            setText(R.string.xingdun_group_nickname_section)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            tag = TAG_LABEL
        })
        editor = EditText(this).apply {
            setHint(R.string.xingdun_group_nickname_not_set)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(0, 10.dp(), 0, 10.dp())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE && canSubmit()) {
                    save()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateCounter()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        card.addView(editor, matchWrap())
        card.addView(View(this).apply { tag = TAG_FIELD_DIVIDER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        val metadata = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp(), 0, 0)
        }
        hint = TextView(this).apply {
            setText(R.string.xingdun_group_nickname_byte_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        byteCounter = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.END
        }
        metadata.addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metadata.addView(byteCounter)
        card.addView(metadata, matchWrap())
        content.addView(card, matchWrap())

        progress = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(progress, LinearLayout.LayoutParams(36.dp(), 36.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 24.dp()
        })
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(12.dp(), 16.dp(), 12.dp(), 10.dp())
            visibility = View.GONE
        }
        content.addView(status, matchWrap())
        retry = Button(this).apply {
            setText(R.string.xingdun_group_nickname_retry)
            visibility = View.GONE
            setOnClickListener { load() }
        }
        content.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        applyColors(colors())
        updateCounter()
    }

    private fun load() {
        if (isLoading || isSaving) return
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (userID.isBlank()) {
            showLoadError()
            return
        }
        isLoading = true
        renderState()
        memberStore.getMemberInfo(listOf(userID), object : GetMemberInfoCompletionHandler {
            override fun onSuccess(memberInfoList: List<GroupMember>) {
                runOnUiThread {
                    isLoading = false
                    showEditor(memberInfoList.firstOrNull()?.nameCard.orEmpty())
                }
            }

            override fun onFailure(code: Int, desc: String) {
                runOnUiThread {
                    isLoading = false
                    showLoadError()
                }
            }
        })
    }

    private fun showEditor(value: String) {
        editor.setText(value)
        editor.setSelection(editor.text.length)
        status.visibility = View.GONE
        retry.visibility = View.GONE
        renderState()
    }

    private fun showLoadError() {
        status.setText(R.string.xingdun_group_nickname_load_failed)
        status.visibility = View.VISIBLE
        retry.visibility = View.VISIBLE
        renderState()
    }

    private fun save() {
        if (!canSubmit()) return
        val value = XingDunGroupNicknamePolicy.normalized(editor.text.toString())
        if (isDebugPreview) {
            Toast.makeText(this, R.string.xingdun_group_nickname_saved, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        isSaving = true
        status.setText(R.string.xingdun_group_nickname_saving)
        status.visibility = View.VISIBLE
        retry.visibility = View.GONE
        renderState()
        memberStore.setSelfNameCard(value, object : CompletionHandler {
            override fun onSuccess() {
                runOnUiThread {
                    isSaving = false
                    Toast.makeText(this@XingDunGroupNicknameActivity, R.string.xingdun_group_nickname_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                runOnUiThread {
                    isSaving = false
                    status.setText(R.string.xingdun_group_nickname_save_failed)
                    status.visibility = View.VISIBLE
                    renderState()
                }
            }
        })
    }

    private fun updateCounter() {
        if (!::byteCounter.isInitialized) return
        val count = XingDunGroupNicknamePolicy.utf8ByteCount(editor.text.toString())
        byteCounter.text = getString(R.string.xingdun_group_nickname_byte_count, count)
        byteCounter.setTextColor(if (count > XingDunGroupNicknamePolicy.MAX_UTF8_BYTES) DANGER else colors().textColorSecondary)
        renderState()
    }

    private fun canSubmit(): Boolean = !isLoading && !isSaving && XingDunGroupNicknamePolicy.canSave(editor.text.toString())

    private fun renderState() {
        if (!::editor.isInitialized) return
        editor.isEnabled = !isLoading && !isSaving
        progress.visibility = if (isLoading || isSaving) View.VISIBLE else View.GONE
        save.isEnabled = canSubmit()
        save.alpha = if (save.isEnabled) 1f else 0.35f
        back.alpha = if (isSaving) 0.35f else 1f
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        title.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        save.setTextColor(BRAND)
        card.background = rounded(colors.bgColorOperate, 18f)
        editor.setTextColor(colors.textColorPrimary)
        editor.setHintTextColor(colors.textColorTertiary)
        hint.setTextColor(colors.textColorSecondary)
        status.setTextColor(WARNING)
        (card.findViewWithTag<View>(TAG_LABEL) as? TextView)?.setTextColor(colors.textColorSecondary)
        card.findViewWithTag<View>(TAG_FIELD_DIVIDER)?.setBackgroundColor(colors.strokeColorPrimary)
        updateCounter()
    }

    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_nickname_preview"
        private const val TAG_LABEL = "group_nickname_label"
        private const val TAG_FIELD_DIVIDER = "group_nickname_divider"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val DANGER = 0xFFE34D59.toInt()
        private const val WARNING = 0xFFB36A00.toInt()

        fun intent(context: Context, groupID: String, debugPreview: Boolean = false): Intent =
            Intent(context, XingDunGroupNicknameActivity::class.java)
                .putExtra(EXTRA_GROUP_ID, groupID)
                .putExtra(EXTRA_DEBUG_PREVIEW, debugPreview)

        fun start(context: Context, groupID: String) {
            context.startActivity(intent(context, groupID))
        }
    }
}
