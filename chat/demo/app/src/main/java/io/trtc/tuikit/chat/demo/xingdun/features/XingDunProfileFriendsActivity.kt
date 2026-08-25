package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.launch
import java.util.Locale

/** Dedicated counterpart of iOS `XingDunProfileFriendsView`. */
class XingDunProfileFriendsActivity : BaseActivity() {
    private lateinit var list: LinearLayout
    private lateinit var state: LinearLayout
    private lateinit var stateTitle: TextView
    private lateinit var stateDetail: TextView
    private lateinit var retry: TextView
    private lateinit var search: EditText
    private var contacts: List<ContactInfo> = emptyList()
    private var loading = true
    private var loadError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildPage()
        observeContacts()
        loadFriends()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F6FA.toInt())
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))
        search = EditText(this).apply {
            setHint(R.string.xingdun_profile_friends_search_hint)
            textSize = 15f
            setSingleLine(true)
            setPadding(18.dp(), 0, 18.dp(), 0)
            background = rounded(0xFFFFFFFF.toInt(), 14f, 0xFFE5E8EC.toInt())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46.dp()).apply {
            marginStart = 16.dp(); marginEnd = 16.dp(); topMargin = 10.dp(); bottomMargin = 10.dp()
        })

        val body = FrameLayout(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 16f)
        }
        body.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginStart = 12.dp(); marginEnd = 12.dp(); bottomMargin = 12.dp()
        })
        state = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28.dp(), 28.dp(), 28.dp(), 28.dp())
            stateTitle = TextView(context).apply {
                textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            }
            stateDetail = TextView(context).apply {
                textSize = 14f; setTextColor(0xFF7A8088.toInt()); gravity = Gravity.CENTER
                setPadding(0, 8.dp(), 0, 18.dp())
            }
            retry = TextView(context).apply {
                setText(R.string.xingdun_profile_friends_retry)
                setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 15f
                background = rounded(0xFF23B39C.toInt(), 12f)
                setOnClickListener { loadFriends() }
            }
            addView(stateTitle)
            addView(stateDetail)
            addView(retry, LinearLayout.LayoutParams(150.dp(), 46.dp()))
        }
        body.addView(state, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun header(): View = FrameLayout(this).apply {
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            text = "‹"; textSize = 34f; gravity = Gravity.CENTER
            setTextColor(0xFF15191D.toInt())
            contentDescription = getString(R.string.xingdun_back)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(52.dp(), 52.dp(), Gravity.START))
        addView(TextView(context).apply {
            setText(R.string.xingdun_profile_friends_title)
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(0xFF15191D.toInt())
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            marginStart = 58.dp(); marginEnd = 58.dp()
        })
    }

    private fun observeContacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ContactStore.shared.state.friendList.collect { value ->
                    contacts = value
                    render()
                }
            }
        }
    }

    private fun loadFriends() {
        loading = true
        loadError = null
        render()
        ContactStore.shared.loadFriends(object : CompletionHandler {
            override fun onSuccess() {
                runOnUiThread { loading = false; render() }
            }

            override fun onFailure(code: Int, desc: String) {
                runOnUiThread {
                    loading = false
                    loadError = desc.takeIf(String::isNotBlank) ?: getString(R.string.xingdun_profile_friends_load_failed)
                    render()
                }
            }
        })
    }

    private fun render() {
        if (!::list.isInitialized) return
        val query = if (::search.isInitialized) search.text.toString().trim() else ""
        val visible = contacts
            .filter { query.isEmpty() || listOf(it.displayName, it.userID, it.nickname, it.friendRemark).any { value -> value?.contains(query, true) == true } }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        list.removeAllViews()
        when {
            loading && contacts.isEmpty() -> showState(R.string.xingdun_profile_friends_loading, 0, false)
            loadError != null && contacts.isEmpty() -> showState(R.string.xingdun_profile_friends_load_failed, loadError.orEmpty(), true)
            visible.isEmpty() && query.isEmpty() -> showState(R.string.xingdun_profile_friends_empty, R.string.xingdun_profile_friends_empty_detail, false)
            visible.isEmpty() -> showState(R.string.xingdun_profile_friends_no_result, R.string.xingdun_profile_friends_no_result_detail, false)
            else -> {
                state.visibility = View.GONE
                visible.forEachIndexed { index, contact ->
                    list.addView(friendRow(contact))
                    if (index < visible.lastIndex) list.addView(View(this).apply { setBackgroundColor(0xFFE9EBEF.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 76.dp() })
                }
            }
        }
    }

    private fun friendRow(contact: ContactInfo): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
        isClickable = true; isFocusable = true
        setOnClickListener { ChatActivity.start(this@XingDunProfileFriendsActivity, "c2c_${contact.userID}") }
        val avatar = Avatar(context).apply {
            setSize(Avatar.AvatarSize.M)
            setContent(Avatar.AvatarContent.Image(contact.avatarURL, contact.displayName))
        }
        addView(avatar, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 0, 0, 0)
            addView(TextView(context).apply {
                text = contact.displayName; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF15191D.toInt())
            })
            addView(TextView(context).apply {
                text = getString(R.string.xingdun_profile_friends_user_id, contact.userID)
                textSize = 13f; setTextColor(0xFF7A8088.toInt()); setPadding(0, 3.dp(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply { text = "›"; textSize = 28f; setTextColor(0xFFB4B8BE.toInt()) })
    }

    private fun showState(title: Int, detail: Int, showRetry: Boolean) {
        showState(title, if (detail == 0) "" else getString(detail), showRetry)
    }

    private fun showState(title: Int, detail: String, showRetry: Boolean) {
        state.visibility = View.VISIBLE
        stateTitle.setText(title)
        stateDetail.text = detail
        stateDetail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
        retry.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius.dp().toFloat()
        stroke?.let { setStroke(1.dp(), it) }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, XingDunProfileFriendsActivity::class.java).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
