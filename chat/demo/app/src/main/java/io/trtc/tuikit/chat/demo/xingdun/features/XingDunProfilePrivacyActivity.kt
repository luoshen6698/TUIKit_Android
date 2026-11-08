package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch

/** Restriction is saved by the server before Tencent synchronization. Read back after every write. */
class XingDunProfilePrivacyActivity : BaseActivity() {
    private lateinit var status: TextView
    private lateinit var retry: TextView
    private lateinit var refresh: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var stateIcon: ImageView
    private lateinit var statePanel: LinearLayout
    private val switches = linkedMapOf<String, SwitchCompat>()
    private var busy = false
    private var rendering = false
    private var syncRetry: Pair<String, Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildPage()
    }

    override fun appearanceLightStatusBarsOverride(): Boolean = true

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(TravelUi.BG)
        }
        val header = FrameLayout(this)
        header.addView(TextView(this).apply {
            setText(R.string.xingdun_profile_privacy_title)
            textSize = 18f
            setTextColor(TravelUi.INK)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(56.dp(), 0, 56.dp(), 0) })
        header.addView(headerAction(R.drawable.travel_auth_back, R.string.xingdun_back) { finish() }.apply {
            id = R.id.pv_back
        }, FrameLayout.LayoutParams(48.dp(), 48.dp(), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = 8.dp() })
        refresh = headerAction(R.drawable.xingdun_ic_about_refresh, R.string.xingdun_privacy_refresh) { read() }.apply {
            id = R.id.pv_refresh
        }
        header.addView(refresh, FrameLayout.LayoutParams(48.dp(), 48.dp(), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = 8.dp() })
        root.addView(header, LinearLayout.LayoutParams(-1, 56.dp()))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 12.dp(), 20.dp(), 28.dp())
        }
        val intro = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 8.dp(), 0, 20.dp())
        }
        intro.addView(ImageView(this).apply {
            setImageResource(R.drawable.xingdun_ic_mine_privacy)
            imageTintList = ColorStateList.valueOf(TravelUi.ACCENT)
            background = TravelUi(this@XingDunProfilePrivacyActivity).box(TravelUi.PALE, 14)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        intro.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(R.string.pv_intro, 17f, TravelUi.INK, true))
            addView(label(R.string.xingdun_privacy_description, 13f, TravelUi.SECONDARY).apply {
                setPadding(0, 6.dp(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 14.dp() })
        content.addView(intro)
        addGroup(content, R.string.pv_basic, listOf(
            Triple("account", R.string.xingdun_privacy_account, R.drawable.xingdun_ic_settings_account),
            Triple("signature", R.string.xingdun_privacy_signature, R.drawable.xingdun_ic_mine_feedback),
            Triple("birthday", R.string.xingdun_privacy_birthday, R.drawable.pv_calendar),
        ))
        addGroup(content, R.string.pv_contact, listOf(
            Triple("phone", R.string.xingdun_privacy_phone, R.drawable.xingdun_ic_account_phone),
            Triple("email", R.string.xingdun_privacy_email, R.drawable.xingdun_ic_account_email),
        ))
        statePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = TravelUi(this@XingDunProfilePrivacyActivity).box(TravelUi.PALE, 14)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        }
        val stateRow = LinearLayout(this).apply { gravity = Gravity.TOP }
        progress = ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(TravelUi.ACCENT)
        }
        stateRow.addView(progress, LinearLayout.LayoutParams(18.dp(), 18.dp()).apply { topMargin = 2.dp(); marginEnd = 10.dp() })
        stateIcon = ImageView(this).apply {
            setImageResource(R.drawable.xingdun_ic_mine_privacy)
            imageTintList = ColorStateList.valueOf(TravelUi.ACCENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        stateRow.addView(stateIcon, LinearLayout.LayoutParams(18.dp(), 18.dp()).apply { topMargin = 2.dp(); marginEnd = 10.dp() })
        status = label(R.string.xingdun_privacy_loading, 13f, TravelUi.SECONDARY).apply {
            id = R.id.pv_status
            ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
        stateRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        statePanel.addView(stateRow)
        retry = label(R.string.xingdun_privacy_refresh, 14f, TravelUi.ACCENT, true).apply {
            id = R.id.pv_retry
            gravity = Gravity.CENTER
            minHeight = 48.dp()
            background = TravelUi(this@XingDunProfilePrivacyActivity).box(Color.WHITE, 12, true)
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { syncRetry?.let { (field, visible) -> read(field, visible) } ?: read() }
        }
        statePanel.addView(retry, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp() })
        content.addView(statePanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.dp() })
        root.addView(ScrollView(this).apply {
            id = R.id.pv_scroll
            isFillViewport = true
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun headerAction(icon: Int, description: Int, action: () -> Unit) = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(TravelUi.ACCENT)
        contentDescription = getString(description)
        setPadding(13.dp(), 13.dp(), 13.dp(), 13.dp())
        background = android.util.TypedValue().let { value ->
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            androidx.appcompat.content.res.AppCompatResources.getDrawable(context, value.resourceId)
        }
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun label(text: Int, size: Float, color: Int, medium: Boolean = false) = TextView(this).apply {
        setText(text)
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        setLineSpacing(3.dp().toFloat(), 1f)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun addGroup(parent: LinearLayout, title: Int, fields: List<Triple<String, Int, Int>>) {
        parent.addView(label(title, 13f, TravelUi.SECONDARY, true).apply {
            setPadding(4.dp(), 4.dp(), 0, 10.dp())
            ViewCompat.setAccessibilityHeading(this, true)
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = TravelUi(this@XingDunProfilePrivacyActivity).box(Color.WHITE, 18, true)
        }
        fields.forEachIndexed { index, (field, titleRes, icon) ->
            if (index > 0) card.addView(View(this).apply { setBackgroundColor(TravelUi.BORDER) },
                LinearLayout.LayoutParams(-1, 1.dp()).apply { marginStart = 52.dp(); marginEnd = 16.dp() })
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 8.dp(), 12.dp(), 8.dp())
                minimumHeight = 68.dp()
            }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(TravelUi.ACCENT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(22.dp(), 22.dp()))
            row.addView(label(titleRes, 15f, TravelUi.INK).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 14.dp(); marginEnd = 8.dp() })
            val control = SwitchCompat(this).apply {
                id = when (field) {
                    "account" -> R.id.pv_account
                    "signature" -> R.id.pv_signature
                    "birthday" -> R.id.pv_birthday
                    "phone" -> R.id.pv_phone
                    else -> R.id.pv_email
                }
                contentDescription = getString(titleRes)
                minHeight = 48.dp()
                minWidth = 52.dp()
                showText = false
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
                thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
                trackTintList = ColorStateList(states, intArrayOf(TravelUi.ACCENT, 0xFFB7C2C7.toInt()))
                isEnabled = false
                setOnCheckedChangeListener { _, visible -> if (!rendering) update(field, visible) }
            }
            switches[field] = control
            row.addView(control, LinearLayout.LayoutParams(-2, -2))
            row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.setOnClickListener { if (control.isEnabled) control.toggle() }
            card.addView(row)
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 22.dp() })
    }

    private fun showState(message: Int, loading: Boolean = false, attention: Boolean = false) {
        status.setText(message)
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        stateIcon.visibility = if (loading) View.GONE else View.VISIBLE
        val color = if (attention) 0xFF866843.toInt() else TravelUi.SECONDARY
        status.setTextColor(color)
        stateIcon.imageTintList = ColorStateList.valueOf(color)
        statePanel.background = TravelUi(this).box(if (attention) 0xFFF5EFE6.toInt() else TravelUi.PALE, 14)
        retry.visibility = if (attention) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) read()
    }

    private fun read(field: String? = null, visible: Boolean = false) {
        if (busy) return
        val session = XingDunSessionManager.currentSession() ?: return
        val generation = XingDunSessionManager.currentAuthenticationGeneration()
        busy = true
        switches.values.forEach { it.isEnabled = false }
        retry.isEnabled = false
        refresh.isEnabled = false
        refresh.alpha = .4f
        showState(R.string.xingdun_privacy_loading, loading = true)
        lifecycleScope.launch {
            val write = if (field == null) null else runCatching {
                XingDunSessionManager.apiClient().postEmpty(session, "user/privacySettings", mapOf("field" to field, "visible" to visible))
            }
            val response = runCatching {
                XingDunProfileVisibility.parseSettings(XingDunSessionManager.apiClient().get<JsonObject>(
                    session, "user/privacySettings", emptyMap(), JsonObject::class.java))
            }
            if (generation == null || generation != XingDunSessionManager.currentAuthenticationGeneration() ||
                XingDunSessionManager.currentSession()?.let { it.companyCode == session.companyCode && it.sdkAppId == session.sdkAppId && it.timUserId == session.timUserId } != true) {
                finish()
                return@launch
            }
            busy = false
            retry.isEnabled = true
            refresh.isEnabled = true
            refresh.alpha = 1f
            response.onSuccess { (policy, synced) ->
                syncRetry = if (synced) null else "account" to XingDunProfileVisibility.visible(policy, "account")
                retry.setText(if (synced) R.string.xingdun_privacy_refresh else R.string.xingdun_privacy_retry_sync)
                rendering = true
                switches.forEach { (key, control) ->
                    control.isChecked = XingDunProfileVisibility.visible(policy, key)
                    control.isEnabled = true
                }
                rendering = false
                showState(when {
                    !synced -> R.string.xingdun_privacy_pending
                    write?.isFailure == true -> R.string.xingdun_privacy_write_failed_read_back
                    else -> R.string.pv_auto_save
                }, attention = !synced || write?.isFailure == true)
            }.onFailure {
                syncRetry = null
                retry.setText(R.string.xingdun_privacy_refresh)
                // Keep controls disabled until a fresh authoritative read succeeds.
                showState(R.string.xingdun_privacy_read_failed, attention = true)
            }
        }
    }

    private fun update(field: String, visible: Boolean) = read(field, visible)
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
