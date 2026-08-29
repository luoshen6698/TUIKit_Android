package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAutoDeleteConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Shared message auto-delete configuration page aligned with the active iOS flow. */
open class XingDunAutoDeleteActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !isDebugPreview

    private val conversationID by lazy { intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty() }
    private val previewCanUpdate by lazy { intent.getBooleanExtra(EXTRA_CAN_UPDATE, false) }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: ImageView
    private lateinit var divider: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var optionsCard: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var retry: Button

    private var configuration: XingDunAutoDeleteConfiguration? = null
    private var canUpdate = false
    private var isLoading = false
    private var isUpdating = false

    private val configurationListener: (String, XingDunAutoDeleteConfiguration) -> Unit = { changedID, value ->
        if (changedID == conversationID) runOnUiThread {
            configuration = value
            isLoading = false
            isUpdating = false
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (conversationID.isBlank()) {
            Toast.makeText(this, R.string.xingdun_auto_delete_invalid_conversation, Toast.LENGTH_LONG).show()
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
        XingDunAutoDeleteRepository.addListener(configurationListener)
        if (isDebugPreview) {
            canUpdate = previewCanUpdate
            configuration = previewConfiguration()
            render()
        } else {
            load()
        }
    }

    override fun onDestroy() {
        XingDunAutoDeleteRepository.removeListener(configurationListener)
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.xingdun_profileEditorRoot)
        header = findViewById(R.id.demo_chatHeaderContainer)
        title = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        divider = findViewById(R.id.demo_headerDivider)
        scroll = findViewById(R.id.xingdun_profileEditorScroll)
        content = findViewById(R.id.xingdun_profileEditorContent)
        findViewById<ImageView>(R.id.demo_btnMore).visibility = View.GONE
        findViewById<FrameLayout>(R.id.demo_badgeContainer).visibility = View.GONE
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener { if (!isUpdating) finish() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        title.setText(R.string.xingdun_auto_delete_title)
        val language = resources.configuration.locales[0]?.language
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (language == "en") 14f else 18f)
    }

    private fun buildContent() {
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_auto_delete_section)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(8.dp(), 0, 8.dp(), 8.dp())
            tag = TAG_SECTION
        })
        optionsCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(optionsCard, matchWrap())
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
            setText(R.string.xingdun_auto_delete_retry)
            visibility = View.GONE
            setOnClickListener { load() }
        }
        content.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        applyColors(colors())
    }

    private fun load() {
        if (isLoading || isUpdating) return
        XingDunAutoDeleteRepository.cached(conversationID)?.let {
            configuration = it
            render()
        }
        isLoading = configuration == null
        status.visibility = View.GONE
        retry.visibility = View.GONE
        renderState()
        activityScope?.launch {
            canUpdate = resolveUpdatePermission()
            runCatching { XingDunAutoDeleteRepository.load(conversationID, force = true) }
                .onSuccess {
                    configuration = it
                    isLoading = false
                    render()
                }
                .onFailure {
                    isLoading = false
                    if (configuration == null) showError(R.string.xingdun_auto_delete_load_failed)
                    else renderState()
                }
        }
    }

    /**
     * Mirrors the iOS page-level permission check instead of trusting the caller. A failed
     * tenant-scoped group lookup is deliberately read-only.
     */
    private suspend fun resolveUpdatePermission(): Boolean {
        return when {
            conversationID.startsWith(C2C_PREFIX) -> true
            conversationID.startsWith(GROUP_PREFIX) -> {
                val groupID = conversationID.removePrefix(GROUP_PREFIX).takeIf { it.isNotBlank() }
                    ?: return false
                val session = XingDunSessionManager.currentSession() ?: return false
                runCatching {
                    XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                        session,
                        "team/detail",
                        mapOf("team_id" to groupID),
                        XingDunGroupDetail::class.java,
                    )
                }.getOrNull()?.let { detail ->
                    detail.currentUserIsAssignedCs ||
                        detail.currentUserRole == ROLE_OWNER ||
                        detail.currentUserRole == ROLE_ADMINISTRATOR
                } == true
            }
            else -> false
        }
    }

    private fun select(ttlSeconds: Int) {
        val current = configuration ?: return
        if (!canUpdate || isUpdating || current.ttlSeconds == ttlSeconds) return
        if (isDebugPreview) {
            configuration = current.copy(ttlSeconds = ttlSeconds, enabled = ttlSeconds > 0, version = current.version + 1)
            render()
            return
        }
        isUpdating = true
        status.setText(R.string.xingdun_auto_delete_updating)
        status.visibility = View.VISIBLE
        retry.visibility = View.GONE
        render()
        activityScope?.launch {
            runCatching { XingDunAutoDeleteRepository.update(conversationID, ttlSeconds) }
                .onSuccess {
                    configuration = it
                    isUpdating = false
                    status.visibility = View.GONE
                    Toast.makeText(
                        this@XingDunAutoDeleteActivity,
                        if (it.enabled) R.string.xingdun_auto_delete_updated else R.string.xingdun_auto_delete_disabled,
                        Toast.LENGTH_SHORT
                    ).show()
                    render()
                }
                .onFailure {
                    isUpdating = false
                    showError(R.string.xingdun_auto_delete_update_failed)
                }
        }
    }

    private fun render() {
        val value = configuration
        optionsCard.removeAllViews()
        if (value != null) {
            val options = XingDunAutoDeletePolicy.normalizedOptions(value.allowedTtlSeconds)
            options.forEachIndexed { index, seconds ->
                optionsCard.addView(optionRow(seconds, value.ttlSeconds == seconds), matchWrap())
                if (index != options.lastIndex) optionsCard.addView(dividerView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }
        }
        applyColors(colors())
        renderState()
    }

    private fun optionRow(seconds: Int, selected: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 14.dp(), 0)
            minimumHeight = 52.dp()
            isEnabled = canUpdate && !isUpdating
            alpha = if (isEnabled || selected) 1f else 0.55f
            setOnClickListener { select(seconds) }
            addView(TextView(this@XingDunAutoDeleteActivity).apply {
                text = optionTitle(seconds)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors().textColorPrimary)
                tag = TAG_OPTION_TEXT
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@XingDunAutoDeleteActivity).apply {
                text = if (selected) "✓" else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(BRAND)
                contentDescription = if (selected) getString(R.string.xingdun_auto_delete_selected) else null
            })
        }
    }

    private fun optionTitle(seconds: Int): String = when (seconds) {
        0 -> getString(R.string.xingdun_auto_delete_off)
        120 -> getString(R.string.xingdun_auto_delete_minutes_2)
        3_600 -> getString(R.string.xingdun_auto_delete_hour_1)
        86_400 -> getString(R.string.xingdun_auto_delete_day_1)
        604_800 -> getString(R.string.xingdun_auto_delete_days_7)
        2_592_000 -> getString(R.string.xingdun_auto_delete_days_30)
        7_776_000 -> getString(R.string.xingdun_auto_delete_days_90)
        31_536_000 -> getString(R.string.xingdun_auto_delete_year_1)
        else -> getString(R.string.xingdun_auto_delete_seconds, seconds)
    }

    private fun showError(message: Int) {
        status.setText(message)
        status.visibility = View.VISIBLE
        retry.visibility = if (configuration == null) View.VISIBLE else View.GONE
        renderState()
    }

    private fun renderState() {
        progress.visibility = if (isLoading || isUpdating) View.VISIBLE else View.GONE
        back.alpha = if (isUpdating) 0.35f else 1f
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        title.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        (content.findViewWithTag<View>(TAG_SECTION) as? TextView)?.setTextColor(colors.textColorSecondary)
        optionsCard.background = rounded(colors.bgColorOperate, 18f)
        status.setTextColor(WARNING)
        for (index in 0 until optionsCard.childCount) {
            val child = optionsCard.getChildAt(index)
            (child.findViewWithTag<View>(TAG_OPTION_TEXT) as? TextView)?.setTextColor(colors.textColorPrimary)
            if (child.tag == TAG_DIVIDER) child.setBackgroundColor(colors.strokeColorPrimary)
        }
    }

    private fun dividerView() = View(this).apply { tag = TAG_DIVIDER }
    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color
    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun previewConfiguration() = XingDunAutoDeleteConfiguration(
        conversationId = conversationID,
        ttlSeconds = 86_400,
        enabled = true,
        version = 4,
        allowedTtlSeconds = XingDunAutoDeletePolicy.DEFAULT_TTL_SECONDS
    )

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val EXTRA_CAN_UPDATE = "can_update"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_auto_delete_preview"
        private const val C2C_PREFIX = "c2c_"
        private const val GROUP_PREFIX = "group_"
        private const val ROLE_OWNER = "owner"
        private const val ROLE_ADMINISTRATOR = "administrator"
        private const val TAG_SECTION = "auto_delete_section"
        private const val TAG_OPTION_TEXT = "auto_delete_option_text"
        private const val TAG_DIVIDER = "auto_delete_divider"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val WARNING = 0xFFB36A00.toInt()

        fun start(context: Context, conversationID: String, canUpdate: Boolean) {
            context.startActivity(Intent(context, XingDunAutoDeleteActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                putExtra(EXTRA_CAN_UPDATE, canUpdate)
            })
        }
    }
}
