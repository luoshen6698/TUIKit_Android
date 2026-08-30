package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Contact-card-only, single-selection counterpart of iOS `XingDunChatForwardPickerView`. */
class XingDunContactForwardPickerActivity : BaseActivity() {
    private val conversationStore = ConversationListStore.create()
    private val contactStore = ContactStore.shared
    private val groupStore = GroupStore.shared

    private lateinit var recentTab: TextView
    private lateinit var contactsTab: TextView
    private lateinit var groupsTab: TextView
    private lateinit var list: RecyclerView
    private lateinit var search: EditText
    private lateinit var status: TextView
    private val adapter = TargetAdapter { finishWith(it.conversationID) }

    private val sourceConversationID: String by lazy {
        intent.getStringExtra(EXTRA_SOURCE_CONVERSATION_ID).orEmpty().trim()
    }
    private val isContactCardPicker: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_CONTACT_CARD_PICKER, false)
    }
    private var tab = Tab.RECENT
    private var query = ""
    private var isLoading = true
    private var loadFailed = false
    private var recent = emptyList<Target>()
    private var contacts = emptyList<Target>()
    private var groups = emptyList<Target>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (isContactCardPicker) tab = Tab.CONTACTS
        buildPage()
        observeStores()
        refresh()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp()))

        search = EditText(this).apply {
            hint = getString(R.string.xingdun_contact_forward_search_contacts)
            textSize = 15f
            maxLines = 1
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_TERTIARY)
            background = rounded(0xFFF4F5F7.toInt(), 22f)
            setPadding(18.dp(), 0, 18.dp(), 0)
            doAfterTextChanged {
                query = it?.toString().orEmpty().trim()
                render()
            }
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply {
            marginStart = 16.dp()
            marginEnd = 16.dp()
            topMargin = 8.dp()
            bottomMargin = 8.dp()
        })
        if (!isContactCardPicker) {
            root.addView(segment(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
                marginStart = 16.dp()
                marginEnd = 16.dp()
                topMargin = 6.dp()
                bottomMargin = 6.dp()
            })
        }
        root.addView(View(this).apply { setBackgroundColor(DIVIDER) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        val body = FrameLayout(this)
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@XingDunContactForwardPickerActivity)
            adapter = this@XingDunContactForwardPickerActivity.adapter
            itemAnimator = null
            setBackgroundColor(Color.WHITE)
        }
        body.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(TEXT_SECONDARY)
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
            setOnClickListener {
                if (loadFailed) refresh()
            }
        }
        body.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        updateTabs()
    }

    private fun header(): FrameLayout = FrameLayout(this).apply {
        setPadding(10.dp(), 0, 10.dp(), 0)
        addView(TextView(context).apply {
            setText(R.string.xingdun_cancel)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(BRAND)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(72.dp(), 42.dp(), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(TextView(context).apply {
            setText(
                if (isContactCardPicker) R.string.xingdun_contact_card_picker_title
                else R.string.xingdun_contact_forward_title
            )
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginStart = 76.dp()
            marginEnd = 76.dp()
        })
    }

    private fun segment(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(3.dp(), 3.dp(), 3.dp(), 3.dp())
        background = rounded(0xFFF0F1F3.toInt(), 12f)
        recentTab = tabButton(R.string.xingdun_contact_forward_recent, Tab.RECENT)
        contactsTab = tabButton(R.string.xingdun_contact_forward_contacts, Tab.CONTACTS)
        groupsTab = tabButton(R.string.xingdun_contact_forward_groups, Tab.GROUPS)
        addView(recentTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(contactsTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(groupsTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun tabButton(label: Int, target: Tab): TextView = TextView(this).apply {
        setText(label)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(TEXT_PRIMARY)
        setOnClickListener {
            if (tab == target) return@setOnClickListener
            tab = target
            query = ""
            search.setText("")
            updateTabs()
            render()
        }
    }

    private fun updateTabs() {
        if (isContactCardPicker) {
            if (::search.isInitialized) {
                search.setHint(R.string.xingdun_contact_forward_search_contacts)
            }
            return
        }
        if (!::recentTab.isInitialized) return
        listOf(recentTab to Tab.RECENT, contactsTab to Tab.CONTACTS, groupsTab to Tab.GROUPS).forEach { (view, target) ->
            view.background = rounded(if (tab == target) Color.WHITE else Color.TRANSPARENT, 9f)
            view.alpha = if (tab == target) 1f else 0.7f
        }
        if (::search.isInitialized) {
            search.hint = getString(
                if (tab == Tab.GROUPS) R.string.xingdun_contact_forward_search_groups
                else R.string.xingdun_contact_forward_search_contacts
            )
        }
    }

    private fun observeStores() {
        lifecycleScope.launch {
            conversationStore.state.conversationList.collectLatest { values ->
                recent = values.asSequence()
                    .filter { it.conversationID.isNotBlank() && it.conversationID != sourceConversationID }
                    .map { value ->
                        val group = value.conversationID.startsWith("group_")
                        Target(
                            conversationID = value.conversationID,
                            title = value.title?.trim().orEmpty().ifBlank { value.conversationID.substringAfter('_') },
                            avatar = value.avatarURL.orEmpty(),
                            group = group,
                        )
                    }
                    .distinctBy(Target::conversationID)
                    .toList()
                render()
            }
        }
        lifecycleScope.launch {
            contactStore.state.friendList.collectLatest { values ->
                contacts = values.asSequence()
                    .filter { !it.isInBlacklist }
                    .map { value ->
                        Target(
                            conversationID = "c2c_${value.userID}",
                            title = value.friendRemark?.trim().orEmpty()
                                .ifBlank { value.nickname?.trim().orEmpty() }
                                .ifBlank { value.userID },
                            avatar = value.avatarURL.orEmpty(),
                            group = false,
                        )
                    }
                    .filter { it.conversationID != sourceConversationID }
                    .sortedBy { it.title.lowercase(Locale.getDefault()) }
                    .toList()
                render()
            }
        }
        lifecycleScope.launch {
            groupStore.state.joinedGroupList.collectLatest { values ->
                groups = values.map { value ->
                    Target(
                        conversationID = "group_${value.groupID}",
                        title = value.groupName?.trim().orEmpty().ifBlank { value.groupID },
                        avatar = value.avatarURL.orEmpty(),
                        group = true,
                    )
                }.filter { it.conversationID != sourceConversationID }
                    .sortedBy { it.title.lowercase(Locale.getDefault()) }
                render()
            }
        }
    }

    private fun refresh() {
        isLoading = true
        loadFailed = false
        render()
        val remaining = AtomicInteger(3)
        val failures = AtomicInteger(0)
        val finishLoad: (Boolean) -> Unit = { failed ->
            if (failed) failures.incrementAndGet()
            if (remaining.decrementAndGet() == 0) runOnUiThread {
                isLoading = false
                loadFailed = failures.get() > 0
                render()
            }
        }
        conversationStore.loadConversations(ConversationLoadOption(), completion(finishLoad))
        contactStore.loadFriends(completion(finishLoad))
        groupStore.loadJoinedGroups(completion(finishLoad))
    }

    private fun render() {
        if (!::list.isInitialized) return
        val source = when (tab) {
            Tab.RECENT -> recent
            Tab.CONTACTS -> contacts
            Tab.GROUPS -> groups
        }
        val visible = if (query.isBlank()) source else source.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.conversationID.substringAfter('_').contains(query, ignoreCase = true)
        }
        adapter.submit(visible)
        val showStatus = loadFailed || visible.isEmpty()
        list.visibility = if (showStatus) View.GONE else View.VISIBLE
        status.visibility = if (showStatus) View.VISIBLE else View.GONE
        if (showStatus) {
            status.setText(
                when {
                    isLoading -> R.string.xingdun_contact_forward_loading
                    loadFailed -> R.string.xingdun_contact_forward_load_failed
                    query.isNotBlank() -> R.string.xingdun_contact_forward_empty_search
                    tab == Tab.RECENT -> R.string.xingdun_contact_forward_empty_recent
                    tab == Tab.CONTACTS -> R.string.xingdun_contact_forward_empty_contacts
                    else -> R.string.xingdun_contact_forward_empty_groups
                }
            )
        }
        status.isClickable = loadFailed
        status.setTextColor(if (loadFailed) BRAND else TEXT_SECONDARY)
    }

    private fun finishWith(conversationID: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_CONVERSATION_ID, conversationID))
        finish()
    }

    private fun completion(done: (Boolean) -> Unit): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() = done(false)
        override fun onFailure(code: Int, desc: String) = done(true)
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp().toFloat()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private data class Target(
        val conversationID: String,
        val title: String,
        val avatar: String,
        val group: Boolean,
    )

    private inner class TargetAdapter(
        private val onClick: (Target) -> Unit,
    ) : RecyclerView.Adapter<TargetAdapter.Holder>() {
        private val items = mutableListOf<Target>()

        fun submit(values: List<Target>) {
            items.clear()
            items.addAll(values)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20.dp(), 9.dp(), 16.dp(), 9.dp())
                minimumHeight = 70.dp()
                setBackgroundColor(Color.WHITE)
            }
            val avatar = Avatar(parent.context).apply { setSize(Avatar.AvatarSize.M) }
            row.addView(avatar, LinearLayout.LayoutParams(48.dp(), 48.dp()))
            val labels = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 0, 0, 0)
            }
            val title = TextView(parent.context).apply {
                textSize = 16f
                setTextColor(TEXT_PRIMARY)
                maxLines = 1
            }
            val subtitle = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, 2.dp(), 0, 0)
            }
            labels.addView(title)
            labels.addView(subtitle)
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val arrow = TextView(parent.context).apply {
                text = "›"
                textSize = 24f
                setTextColor(TEXT_TERTIARY)
            }
            row.addView(arrow)
            return Holder(row, avatar, title, subtitle)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        inner class Holder(
            itemView: View,
            private val avatar: Avatar,
            private val title: TextView,
            private val subtitle: TextView,
        ) : RecyclerView.ViewHolder(itemView) {
            fun bind(target: Target) {
                avatar.setContent(Avatar.AvatarContent.Image(target.avatar, target.title))
                title.text = target.title
                subtitle.setText(if (target.group) R.string.xingdun_contact_forward_group else R.string.xingdun_contact_forward_direct)
                itemView.setOnClickListener { onClick(target) }
            }
        }
    }

    private enum class Tab { RECENT, CONTACTS, GROUPS }

    companion object {
        const val EXTRA_RESULT_CONVERSATION_ID = "conversation_id_result"
        private const val EXTRA_SOURCE_CONVERSATION_ID = "source_conversation_id"
        private const val EXTRA_CONTACT_CARD_PICKER = "contact_card_picker"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val TEXT_PRIMARY = 0xFF15191D.toInt()
        private const val TEXT_SECONDARY = 0xFF7A8088.toInt()
        private const val TEXT_TERTIARY = 0xFFABB0B7.toInt()
        private const val DIVIDER = 0xFFE8EAED.toInt()

        fun intent(context: Context, sourceConversationID: String): Intent =
            Intent(context, XingDunContactForwardPickerActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_CONVERSATION_ID, sourceConversationID)
            }

        fun contactCardIntent(context: Context, sourceConversationID: String): Intent =
            Intent(context, XingDunContactForwardPickerActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_CONVERSATION_ID, sourceConversationID)
                putExtra(EXTRA_CONTACT_CARD_PICKER, true)
            }
    }
}
