package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
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
    private lateinit var retry: Button
    private val switches = linkedMapOf<String, SwitchCompat>()
    private var busy = false
    private var rendering = false
    private var syncRetry: Pair<String, Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 0, 20.dp(), 16.dp())
            setBackgroundColor(0xFFF5F6FA.toInt())
        }
        root.addView(Button(this).apply {
            setText(R.string.xingdun_back)
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            setText(R.string.xingdun_profile_privacy_title)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 12.dp(), 0, 16.dp())
        })
        val labels = listOf(R.string.xingdun_privacy_account, R.string.xingdun_privacy_phone,
            R.string.xingdun_privacy_email, R.string.xingdun_privacy_birthday, R.string.xingdun_privacy_signature)
        XingDunProfileVisibility.fields.zip(labels).forEach { (field, label) ->
            val control = SwitchCompat(this).apply {
                setText(label)
                textSize = 17f
                minHeight = 56.dp()
                isEnabled = false
                setOnCheckedChangeListener { _, visible -> if (!rendering) update(field, visible) }
            }
            switches[field] = control
            root.addView(control)
        }
        status = TextView(this).apply { textSize = 15f; setPadding(0, 16.dp(), 0, 12.dp()) }
        root.addView(status)
        retry = Button(this).apply {
            setText(R.string.xingdun_privacy_refresh)
            setOnClickListener { syncRetry?.let { (field, visible) -> read(field, visible) } ?: read() }
        }
        root.addView(retry)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(20.dp(), bars.top, 20.dp(), bars.bottom + 16.dp())
            insets
        }
        setContentView(ScrollView(this).apply { addView(root, ViewGroup.LayoutParams(-1, -2)) })
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
        status.setText(R.string.xingdun_privacy_loading)
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
            response.onSuccess { (policy, synced) ->
                syncRetry = if (synced) null else "account" to XingDunProfileVisibility.visible(policy, "account")
                retry.setText(if (synced) R.string.xingdun_privacy_refresh else R.string.xingdun_privacy_retry_sync)
                rendering = true
                switches.forEach { (key, control) ->
                    control.isChecked = XingDunProfileVisibility.visible(policy, key)
                    control.isEnabled = true
                }
                rendering = false
                status.setText(when {
                    !synced -> R.string.xingdun_privacy_pending
                    write?.isFailure == true -> R.string.xingdun_privacy_write_failed_read_back
                    else -> R.string.xingdun_privacy_description
                })
            }.onFailure {
                syncRetry = null
                retry.setText(R.string.xingdun_privacy_refresh)
                // Keep controls disabled until a fresh authoritative read succeeds.
                status.setText(R.string.xingdun_privacy_read_failed)
            }
        }
    }

    private fun update(field: String, visible: Boolean) = read(field, visible)
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
