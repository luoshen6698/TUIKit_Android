package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.reflect.TypeToken
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMetadata
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/** Android counterpart of iOS `XingDunGroupListView`. */
class XingDunGroupListActivity : BaseActivity() {
    private val groupStore = GroupStore.shared
    private val conversationStore = ConversationListStore.create()

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var warning: TextView
    private lateinit var countHeader: TextView
    private lateinit var status: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var retry: TextView
    private lateinit var list: RecyclerView
    private val adapter = GroupAdapter { ChatActivity.start(this, "group_${it.groupID}") }

    private var groups: List<GroupInfo> = emptyList()
    private var metadata: Map<String, XingDunGroupMetadata> = emptyMap()
    private var mutedConversationIDs: Set<String> = emptySet()
    private var searchQuery = ""
    private var isLoading = true
    private var loadFailed = false
    private var metadataFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildPage()
        observeStores()
        refresh()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BACKGROUND)
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))

        root.addView(EditText(this).apply {
            hint = getString(R.string.xingdun_group_list_search_hint)
            textSize = 15f
            maxLines = 1
            isSingleLine = true
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_TERTIARY)
            background = rounded(Color.WHITE, 12f)
            setPadding(16.dp(), 0, 16.dp(), 0)
            doAfterTextChanged {
                searchQuery = it?.toString().orEmpty().trim()
                render()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply {
            marginStart = 16.dp()
            marginEnd = 16.dp()
            topMargin = 12.dp()
        })

        warning = TextView(this).apply {
            setText(R.string.xingdun_group_list_metadata_warning)
            textSize = 13f
            setTextColor(0xFF8A6215.toInt())
            background = rounded(0xFFFFF4D6.toInt(), 10f)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            visibility = View.GONE
        }
        root.addView(warning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = 16.dp()
            marginEnd = 16.dp()
            topMargin = 10.dp()
        })

        countHeader = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_SECONDARY)
            setPadding(20.dp(), 14.dp(), 20.dp(), 10.dp())
            visibility = View.GONE
        }
        root.addView(countHeader)

        val pageBody = FrameLayout(this)
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(BRAND)
            setProgressBackgroundColorSchemeColor(Color.WHITE)
            setOnRefreshListener { refresh() }
        }
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@XingDunGroupListActivity)
            adapter = this@XingDunGroupListActivity.adapter
            setBackgroundColor(Color.WHITE)
            itemAnimator = null
        }
        swipeRefresh.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pageBody.addView(swipeRefresh, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        statusTitle = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
        }
        statusMessage = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(TEXT_SECONDARY)
            setPadding(28.dp(), 10.dp(), 28.dp(), 18.dp())
        }
        retry = primaryButton(R.string.xingdun_retry) { refresh() }.apply { visibility = View.GONE }
        status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp(), 80.dp(), 24.dp(), 24.dp())
            addView(statusTitle)
            addView(statusMessage)
            addView(retry, LinearLayout.LayoutParams(150.dp(), 46.dp()))
        }
        pageBody.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(pageBody, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            contentDescription = getString(R.string.xingdun_back)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(52.dp(), 52.dp(), Gravity.START))
        addView(TextView(context).apply {
            setText(R.string.xingdun_group_list_title)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            marginStart = 58.dp()
            marginEnd = 58.dp()
        })
        addView(TextView(context).apply {
            text = "↻"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(BRAND)
            contentDescription = getString(R.string.xingdun_group_list_refresh)
            setOnClickListener { refresh() }
        }, FrameLayout.LayoutParams(52.dp(), 52.dp(), Gravity.END))
    }

    private fun observeStores() {
        lifecycleScope.launch {
            groupStore.state.joinedGroupList.collectLatest {
                groups = it
                render()
            }
        }
        lifecycleScope.launch {
            conversationStore.state.conversationList.collectLatest { conversations ->
                mutedConversationIDs = conversations
                    .filter { it.receiveOption != ReceiveMessageOption.RECEIVE }
                    .mapTo(linkedSetOf()) { it.conversationID }
                render()
            }
        }
    }

    private fun refresh() {
        if (swipeRefresh.isRefreshing && isLoading) return
        isLoading = true
        loadFailed = false
        metadataFailed = false
        swipeRefresh.isRefreshing = true
        render()
        groupStore.loadJoinedGroups(object : CompletionHandler {
            override fun onSuccess() = runOnUiThread {
                isLoading = false
                swipeRefresh.isRefreshing = false
                render()
            }

            override fun onFailure(code: Int, desc: String) = runOnUiThread {
                isLoading = false
                loadFailed = true
                swipeRefresh.isRefreshing = false
                render()
            }
        })
        loadMetadata()
    }

    private fun loadMetadata() {
        val session = XingDunSessionManager.currentSession() ?: return
        lifecycleScope.launch {
            runCatching {
                val type = TypeToken.getParameterized(List::class.java, XingDunGroupMetadata::class.java).type
                XingDunSessionManager.apiClient().get<List<XingDunGroupMetadata>>(
                    session,
                    "team/list",
                    emptyMap(),
                    type
                )
            }.onSuccess { result ->
                metadata = result.mapNotNull { item ->
                    item.groupId.trim().takeIf(String::isNotEmpty)?.let { it to item }
                }.toMap()
                metadataFailed = false
                render()
            }.onFailure {
                metadataFailed = true
                render()
            }
        }
    }

    private fun render() {
        if (!::list.isInitialized) return
        val visible = groups
            .filter { group ->
                if (searchQuery.isBlank()) true else {
                    displayName(group).contains(searchQuery, ignoreCase = true) ||
                        group.groupID.contains(searchQuery, ignoreCase = true)
                }
            }
            .sortedWith(compareBy<GroupInfo>(
                { metadata[it.groupID]?.isOfficial != true },
                { metadata[it.groupID]?.isCustomerService != true },
                { displayName(it).lowercase(Locale.getDefault()) },
                { it.groupID.lowercase(Locale.ROOT) }
            ))

        warning.visibility = if (metadataFailed && groups.isNotEmpty()) View.VISIBLE else View.GONE
        countHeader.visibility = if (visible.isNotEmpty()) View.VISIBLE else View.GONE
        if (visible.isNotEmpty()) {
            countHeader.text = getString(R.string.xingdun_group_list_joined_count, visible.size)
            status.visibility = View.GONE
            list.visibility = View.VISIBLE
            adapter.submit(visible.map { group ->
                GroupRow(
                    info = group,
                    name = displayName(group),
                    avatar = group.avatarURL?.trim().orEmpty().ifEmpty { metadata[group.groupID]?.avatar.orEmpty() },
                    official = metadata[group.groupID]?.isOfficial == true,
                    muted = mutedConversationIDs.contains("group_${group.groupID}")
                )
            })
            return
        }

        adapter.submit(emptyList())
        list.visibility = View.GONE
        status.visibility = View.VISIBLE
        when {
            isLoading -> {
                statusTitle.setText(R.string.xingdun_group_list_loading)
                statusMessage.text = ""
                retry.visibility = View.GONE
            }
            loadFailed -> {
                statusTitle.setText(R.string.xingdun_group_list_load_failed)
                statusMessage.setText(R.string.xingdun_group_list_load_failed_message)
                retry.visibility = View.VISIBLE
            }
            searchQuery.isNotBlank() -> {
                statusTitle.setText(R.string.xingdun_group_list_search_empty)
                statusMessage.setText(R.string.xingdun_group_list_search_empty_message)
                retry.visibility = View.GONE
            }
            else -> {
                statusTitle.setText(R.string.xingdun_group_list_empty)
                statusMessage.setText(R.string.xingdun_group_list_empty_message)
                retry.visibility = View.GONE
            }
        }
    }

    private fun displayName(group: GroupInfo): String {
        val sdkName = group.groupName?.trim().orEmpty()
        val businessName = metadata[group.groupID]?.name?.trim().orEmpty()
        return sdkName.takeIf { it.isNotEmpty() && it != group.groupID }
            ?: businessName.takeIf(String::isNotEmpty)
            ?: group.groupID
    }

    private fun primaryButton(title: Int, action: () -> Unit): TextView = TextView(this).apply {
        setText(title)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(BRAND, 13f)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.dp().toFloat()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private data class GroupRow(
        val info: GroupInfo,
        val name: String,
        val avatar: String,
        val official: Boolean,
        val muted: Boolean
    )

    private inner class GroupAdapter(
        private val onClick: (GroupInfo) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.Holder>() {
        private val items = mutableListOf<GroupRow>()

        fun submit(updated: List<GroupRow>) {
            items.clear()
            items.addAll(updated)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(groupRowView(parent.context))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        private fun groupRowView(context: Context): View = LinearLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 12.dp(), 18.dp(), 12.dp())
            minimumHeight = 78.dp()
            setBackgroundColor(Color.WHITE)
            addView(Avatar(context).apply {
                id = ID_GROUP_AVATAR
                setSize(Avatar.AvatarSize.M)
            }, LinearLayout.LayoutParams(52.dp(), 52.dp()))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 0, 0, 0)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        id = ID_GROUP_NAME
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(TEXT_PRIMARY)
                        maxLines = 1
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        id = ID_GROUP_BADGE
                        setText(R.string.xingdun_group_list_official)
                        textSize = 11f
                        setTextColor(BRAND)
                        background = rounded(0xFFE1F7F2.toInt(), 8f)
                        setPadding(8.dp(), 2.dp(), 8.dp(), 2.dp())
                    })
                })
                addView(TextView(context).apply {
                    id = ID_GROUP_SUBTITLE
                    textSize = 13f
                    setTextColor(TEXT_SECONDARY)
                    setPadding(0, 5.dp(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                id = ID_GROUP_MUTED
                text = "♩̸"
                textSize = 18f
                setTextColor(TEXT_TERTIARY)
                contentDescription = getString(R.string.xingdun_group_list_muted)
                setPadding(10.dp(), 0, 0, 0)
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 25f
                setTextColor(TEXT_TERTIARY)
                setPadding(8.dp(), 0, 0, 0)
            })
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val avatar: Avatar = itemView.findViewById(ID_GROUP_AVATAR)
            private val name: TextView = itemView.findViewById(ID_GROUP_NAME)
            private val badge: TextView = itemView.findViewById(ID_GROUP_BADGE)
            private val subtitle: TextView = itemView.findViewById(ID_GROUP_SUBTITLE)
            private val muted: TextView = itemView.findViewById(ID_GROUP_MUTED)

            fun bind(row: GroupRow) {
                avatar.setContent(Avatar.AvatarContent.Image(row.avatar, row.name))
                name.text = row.name
                badge.visibility = if (row.official) View.VISIBLE else View.GONE
                subtitle.text = row.info.memberCount?.let {
                    getString(R.string.xingdun_group_list_members, it)
                }.orEmpty()
                muted.visibility = if (row.muted) View.VISIBLE else View.GONE
                itemView.setOnClickListener { onClick(row.info) }
            }
        }
    }

    companion object {
        private val PAGE_BACKGROUND = 0xFFF5F6FA.toInt()
        private val BRAND = 0xFF23B39C.toInt()
        private val TEXT_PRIMARY = 0xFF15191D.toInt()
        private val TEXT_SECONDARY = 0xFF7A8088.toInt()
        private val TEXT_TERTIARY = 0xFFABB0B7.toInt()
        private val ID_GROUP_AVATAR = View.generateViewId()
        private val ID_GROUP_NAME = View.generateViewId()
        private val ID_GROUP_BADGE = View.generateViewId()
        private val ID_GROUP_SUBTITLE = View.generateViewId()
        private val ID_GROUP_MUTED = View.generateViewId()

        fun start(context: Context) {
            context.startActivity(Intent(context, XingDunGroupListActivity::class.java).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
