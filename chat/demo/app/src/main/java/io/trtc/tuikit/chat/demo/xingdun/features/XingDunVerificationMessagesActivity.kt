package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.reflect.TypeToken
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.canHandle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.fromUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.groupDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.isJoinRequest
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** iOS-parity verification inbox: friend applications and group applications share one page. */
class XingDunVerificationMessagesActivity : BaseActivity() {
    private val contactStore = ContactStore.shared
    private val groupStore = GroupStore.shared
    private lateinit var clear: TextView
    private lateinit var friendTab: TextView
    private lateinit var groupTab: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recycler: RecyclerView
    private lateinit var status: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var retry: TextView
    private val adapter = VerificationAdapter(::respondToFriend, ::respondToServerGroup, ::respondToNativeGroup)

    private var selectedTab = Tab.FRIEND
    private var friends: List<FriendApplication> = emptyList()
    private var serverGroups: List<ServerGroupInvitation> = emptyList()
    private var nativeGroups: List<GroupApplicationInfo> = emptyList()
    private var loading = false
    private var loadError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildPage()
        observeNativeGroups()
        refresh()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BACKGROUND)
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))
        root.addView(segment(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
            marginStart = 16.dp(); marginEnd = 16.dp(); topMargin = 8.dp(); bottomMargin = 8.dp()
        })

        val body = FrameLayout(this)
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(BRAND)
            setProgressBackgroundColorSchemeColor(Color.WHITE)
            setOnRefreshListener { refresh() }
        }
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@XingDunVerificationMessagesActivity)
            adapter = this@XingDunVerificationMessagesActivity.adapter
            itemAnimator = null
            setBackgroundColor(Color.WHITE)
        }
        swipeRefresh.addView(recycler, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(swipeRefresh, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        statusTitle = TextView(this).apply {
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY)
        }
        statusMessage = TextView(this).apply {
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(TEXT_SECONDARY); setPadding(28.dp(), 10.dp(), 28.dp(), 18.dp())
        }
        retry = actionButton(getString(R.string.xingdun_retry), primary = true) { refresh() }
        status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24.dp(), 80.dp(), 24.dp(), 24.dp())
            addView(statusTitle)
            addView(statusMessage)
            addView(retry, LinearLayout.LayoutParams(150.dp(), 42.dp()))
        }
        body.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        render()
    }

    private fun header(): View = FrameLayout(this).apply {
        setBackgroundColor(Color.WHITE)
        addView(TextView(this@XingDunVerificationMessagesActivity).apply {
            text = "‹"; textSize = 38f; gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(52.dp(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        addView(TextView(this@XingDunVerificationMessagesActivity).apply {
            setText(R.string.xingdun_verification_messages_title); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        clear = TextView(this@XingDunVerificationMessagesActivity).apply {
            setText(R.string.xingdun_clear); textSize = 15f; gravity = Gravity.CENTER; setTextColor(BRAND)
            setPadding(12.dp(), 0, 16.dp(), 0); setOnClickListener { confirmClearResolvedFriends() }
        }
        addView(clear, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
    }

    private fun segment(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(SEGMENT_BG, 10f)
        setPadding(3.dp(), 3.dp(), 3.dp(), 3.dp())
        friendTab = segmentButton(R.string.xingdun_friend) { select(Tab.FRIEND) }
        groupTab = segmentButton(R.string.xingdun_group) { select(Tab.GROUP) }
        addView(friendTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(groupTab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun segmentButton(textRes: Int, onClick: () -> Unit) = TextView(this).apply {
        setText(textRes); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(TEXT_PRIMARY); setOnClickListener { onClick() }
    }

    private fun select(tab: Tab) {
        if (selectedTab == tab) return
        selectedTab = tab
        render()
        if (tab == Tab.GROUP) refreshNativeGroups()
    }

    private fun refresh() {
        if (loading) return
        loading = true
        loadError = null
        render()
        lifecycleScope.launch {
            try {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                val client = XingDunSessionManager.apiClient()
                val received: FriendApplicationPage = client.get(
                    session, "friend/receivedApply", mapOf("page" to "1", "pageSize" to "50"), FriendApplicationPage::class.java
                )
                val sent: FriendApplicationPage = client.get(
                    session, "friend/sentApply", mapOf("page" to "1", "pageSize" to "50"), FriendApplicationPage::class.java
                )
                friends = (received.list.map { it.copy(direction = Direction.RECEIVED) } +
                    sent.list.map { it.copy(direction = Direction.SENT) }).sortedByDescending { it.id }
                if (received.unreadCount > 0) {
                    runCatching { client.postEmpty(session, "friend/readApply", emptyMap<String, String>()) }
                }
                contactStore.clearFriendApplicationUnreadCount(object : CompletionHandler {
                    override fun onSuccess() = Unit
                    override fun onFailure(code: Int, desc: String) = Unit
                })
                val type = object : TypeToken<List<ServerGroupInvitation>>() {}.type
                serverGroups = client.get(session, "team/invitations", emptyMap(), type)
                refreshNativeGroups()
            } catch (error: Throwable) {
                loadError = error.message.orEmpty().ifBlank { getString(R.string.xingdun_verification_load_failed) }
            } finally {
                loading = false
                swipeRefresh.isRefreshing = false
                render()
            }
        }
    }

    private fun observeNativeGroups() {
        lifecycleScope.launch {
            groupStore.state.applicationList.collectLatest {
                nativeGroups = it
                if (selectedTab == Tab.GROUP) render()
            }
        }
    }

    private fun refreshNativeGroups() {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {
                groupStore.clearApplicationUnreadCount(object : CompletionHandler {
                    override fun onSuccess() = Unit
                    override fun onFailure(code: Int, desc: String) = Unit
                })
            }
            override fun onFailure(code: Int, desc: String) {
                if (serverGroups.isEmpty()) {
                    loadError = desc.ifBlank { getString(R.string.xingdun_verification_load_failed) }
                    render()
                }
            }
        })
    }

    private fun render() {
        friendTab.background = rounded(if (selectedTab == Tab.FRIEND) Color.WHITE else Color.TRANSPARENT, 8f)
        groupTab.background = rounded(if (selectedTab == Tab.GROUP) Color.WHITE else Color.TRANSPARENT, 8f)
        clear.visibility = if (selectedTab == Tab.FRIEND) View.VISIBLE else View.INVISIBLE
        clear.isEnabled = friends.any { it.status != STATUS_PENDING }
        clear.alpha = if (clear.isEnabled) 1f else .35f

        val rows = if (selectedTab == Tab.FRIEND) {
            friends.map { VerificationRow.Friend(it) }
        } else {
            val native = nativeGroups.map { VerificationRow.NativeGroup(it) }
            val existingKeys = nativeGroups.map { "${it.groupID}|${it.fromUser}" }.toSet()
            val server = serverGroups.filterNot { "${it.groupId}|${it.inviterUserId}" in existingKeys }
                .map { VerificationRow.ServerGroup(it) }
            native + server
        }
        adapter.submit(rows)
        val hasRows = rows.isNotEmpty()
        recycler.visibility = if (hasRows) View.VISIBLE else View.GONE
        status.visibility = if (hasRows) View.GONE else View.VISIBLE
        retry.visibility = if (loadError != null) View.VISIBLE else View.GONE
        statusTitle.text = when {
            loading -> getString(if (selectedTab == Tab.FRIEND) R.string.xingdun_loading_friend_applications else R.string.xingdun_loading_group_applications)
            loadError != null -> getString(R.string.xingdun_verification_load_failed)
            selectedTab == Tab.FRIEND -> getString(R.string.xingdun_no_friend_applications)
            else -> getString(R.string.xingdun_no_group_applications)
        }
        statusMessage.text = when {
            loading -> ""
            loadError != null -> loadError
            selectedTab == Tab.FRIEND -> getString(R.string.xingdun_no_friend_applications_message)
            else -> getString(R.string.xingdun_no_group_applications_message)
        }
    }

    private fun respondToFriend(application: FriendApplication, approve: Boolean) {
        confirm(if (approve) R.string.xingdun_confirm_accept_friend else R.string.xingdun_confirm_reject_friend) {
            lifecycleScope.launch {
                runOperation {
                    val session = requireNotNull(XingDunSessionManager.currentSession())
                    XingDunSessionManager.apiClient().postEmpty(
                        session, if (approve) "friend/agree" else "friend/reject", mapOf("apply_id" to application.id)
                    )
                    if (approve) contactStore.loadFriends()
                    refresh()
                }
            }
        }
    }

    private fun respondToServerGroup(invitation: ServerGroupInvitation, approve: Boolean) {
        confirm(if (approve) R.string.xingdun_confirm_accept_group else R.string.xingdun_confirm_reject_group) {
            lifecycleScope.launch {
                runOperation {
                    val session = requireNotNull(XingDunSessionManager.currentSession())
                    XingDunSessionManager.apiClient().postEmpty(
                        session, "team/handleInvitation", mapOf("invitation_id" to invitation.id, "approve" to approve)
                    )
                    if (approve) groupStore.loadJoinedGroups()
                    refresh()
                }
            }
        }
    }

    private fun respondToNativeGroup(application: GroupApplicationInfo, approve: Boolean) {
        confirm(if (approve) R.string.xingdun_confirm_accept_group else R.string.xingdun_confirm_reject_group) {
            val completion = object : CompletionHandler {
                override fun onSuccess() {
                    if (approve) groupStore.loadJoinedGroups()
                    refreshNativeGroups()
                }
                override fun onFailure(code: Int, desc: String) = showError(desc)
            }
            if (approve) groupStore.acceptApplication(application, completion)
            else groupStore.refuseApplication(application, completion)
        }
    }

    private fun confirm(messageRes: Int, operation: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(messageRes)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> operation() }
            .show()
    }

    private fun confirmClearResolvedFriends() {
        val resolved = friends.filter { it.status != STATUS_PENDING }
        if (resolved.isEmpty()) return
        confirm(R.string.xingdun_confirm_clear_applications) {
            lifecycleScope.launch {
                runOperation {
                    val session = requireNotNull(XingDunSessionManager.currentSession())
                    resolved.forEach {
                        XingDunSessionManager.apiClient().postEmpty(session, "friend/deleteApply", mapOf("apply_id" to it.id))
                    }
                    refresh()
                }
            }
        }
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        try { block() } catch (error: Throwable) { showError(error.message.orEmpty()) }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message.ifBlank { getString(R.string.xingdun_action_failed) }, Toast.LENGTH_LONG).show()
    }

    private fun actionButton(text: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else BRAND)
        background = rounded(if (primary) BRAND else Color.WHITE, 8f, if (primary) null else BRAND)
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = radius.dp().toFloat(); setColor(color); stroke?.let { setStroke(1.dp(), it) }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp() = this * resources.displayMetrics.density

    companion object {
        private const val PAGE_BACKGROUND = 0xFFF5F6FA.toInt()
        private const val BRAND = 0xFF23B39C.toInt()
        private const val TEXT_PRIMARY = 0xFF15191D.toInt()
        private const val TEXT_SECONDARY = 0xFF7A8088.toInt()
        private const val SEGMENT_BG = 0xFFE9ECEF.toInt()
        private const val STATUS_PENDING = 0

        fun start(context: Context) = context.startActivity(Intent(context, XingDunVerificationMessagesActivity::class.java))
    }

    private enum class Tab { FRIEND, GROUP }
    private enum class Direction { RECEIVED, SENT }

    private data class FriendApplicationPage(
        val total: Int = 0,
        val list: List<FriendApplication> = emptyList(),
        val page: Int = 1,
        val pageSize: Int = 20,
        val unreadCount: Int = 0
    )

    private data class FriendApplication(
        val id: Int = 0,
        val fromUserId: Int = 0,
        val toUserId: Int = 0,
        val applyMsg: String? = null,
        val status: Int = 0,
        val isRead: Int = 0,
        val fromUser: ApplicationUser? = null,
        val toUser: ApplicationUser? = null,
        val direction: Direction = Direction.RECEIVED
    ) {
        val user: ApplicationUser? get() = if (direction == Direction.RECEIVED) fromUser else toUser
    }

    private data class ApplicationUser(
        val id: Int? = null,
        val customId: String? = null,
        val nickname: String? = null,
        val avatar: String? = null,
        val timUserId: String? = null
    )

    private data class ServerGroupInvitation(
        val id: Int = 0,
        val groupId: String = "",
        val groupName: String = "",
        val inviterUserId: String = "",
        val inviterName: String = "",
        val inviterAvatar: String? = null,
        val message: String? = null,
        val status: Int = 0
    )

    private sealed interface VerificationRow {
        data class Friend(val value: FriendApplication) : VerificationRow
        data class ServerGroup(val value: ServerGroupInvitation) : VerificationRow
        data class NativeGroup(val value: GroupApplicationInfo) : VerificationRow
    }

    private inner class VerificationAdapter(
        private val onFriend: (FriendApplication, Boolean) -> Unit,
        private val onServerGroup: (ServerGroupInvitation, Boolean) -> Unit,
        private val onNativeGroup: (GroupApplicationInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<VerificationAdapter.Holder>() {
        private var rows: List<VerificationRow> = emptyList()
        fun submit(value: List<VerificationRow>) { rows = value; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                background = rounded(Color.WHITE, 0f)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val avatar = Avatar(parent.context)
            row.addView(avatar, LinearLayout.LayoutParams(48.dp(), 48.dp()))
            val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(TEXT_PRIMARY); maxLines = 1 }
            val summary = TextView(parent.context).apply { textSize = 13f; setTextColor(TEXT_SECONDARY); maxLines = 2 }
            texts.addView(name); texts.addView(summary)
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12.dp(); marginEnd = 8.dp() })
            val actions = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val reject = actionButton(getString(R.string.xingdun_reject), false) {}
            val accept = actionButton(getString(R.string.xingdun_accept), true) {}
            actions.addView(reject, LinearLayout.LayoutParams(58.dp(), 34.dp()).apply { marginEnd = 8.dp() })
            actions.addView(accept, LinearLayout.LayoutParams(58.dp(), 34.dp()))
            val state = TextView(parent.context).apply { textSize = 13f; gravity = Gravity.CENTER; setTextColor(TEXT_SECONDARY) }
            val accessory = FrameLayout(parent.context).apply { addView(actions); addView(state) }
            row.addView(accessory, LinearLayout.LayoutParams(124.dp(), 34.dp()))
            return Holder(row, avatar, name, summary, actions, reject, accept, state)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            val presentation = when (row) {
                is VerificationRow.Friend -> friendPresentation(row.value)
                is VerificationRow.ServerGroup -> serverGroupPresentation(row.value)
                is VerificationRow.NativeGroup -> nativeGroupPresentation(row.value)
            }
            holder.avatar.setContent(
                if (presentation.avatar.isNullOrBlank()) Avatar.AvatarContent.Text(presentation.name)
                else Avatar.AvatarContent.Image(presentation.avatar, presentation.name)
            )
            holder.name.text = presentation.name
            holder.summary.text = presentation.summary
            holder.actions.visibility = if (presentation.canHandle) View.VISIBLE else View.GONE
            holder.state.visibility = if (presentation.canHandle) View.GONE else View.VISIBLE
            holder.state.text = presentation.status
            holder.reject.setOnClickListener { dispatch(row, false) }
            holder.accept.setOnClickListener { dispatch(row, true) }
        }

        private fun dispatch(row: VerificationRow, approve: Boolean) = when (row) {
            is VerificationRow.Friend -> onFriend(row.value, approve)
            is VerificationRow.ServerGroup -> onServerGroup(row.value, approve)
            is VerificationRow.NativeGroup -> onNativeGroup(row.value, approve)
        }

        override fun getItemCount() = rows.size

        inner class Holder(
            itemView: View,
            val avatar: Avatar,
            val name: TextView,
            val summary: TextView,
            val actions: LinearLayout,
            val reject: TextView,
            val accept: TextView,
            val state: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    private data class Presentation(
        val name: String,
        val avatar: String?,
        val summary: String,
        val status: String,
        val canHandle: Boolean
    )

    private fun friendPresentation(item: FriendApplication): Presentation {
        val user = item.user
        val name = user?.nickname?.takeIf(String::isNotBlank)
            ?: user?.timUserId?.takeIf(String::isNotBlank)
            ?: getString(R.string.xingdun_deleted_user)
        val summary = if (item.direction == Direction.SENT) {
            getString(when (item.status) {
                1 -> R.string.xingdun_friend_sent_agreed
                2 -> R.string.xingdun_friend_sent_rejected
                3 -> R.string.xingdun_friend_application_expired
                else -> R.string.xingdun_friend_waiting
            })
        } else item.applyMsg?.takeIf(String::isNotBlank) ?: getString(R.string.xingdun_friend_request_default)
        return Presentation(name, user?.avatar, summary, statusText(item.status), item.direction == Direction.RECEIVED && item.status == 0)
    }

    private fun serverGroupPresentation(item: ServerGroupInvitation) = Presentation(
        item.inviterName.takeIf(String::isNotBlank) ?: item.inviterUserId,
        item.inviterAvatar,
        item.message?.takeIf(String::isNotBlank) ?: getString(R.string.xingdun_group_invitation_summary, item.groupName.ifBlank { item.groupId }),
        groupStatusText(item.status),
        item.status == 0
    )

    private fun nativeGroupPresentation(item: GroupApplicationInfo) = Presentation(
        item.fromUserDisplayName,
        item.fromUserAvatarURL,
        getString(if (item.isJoinRequest) R.string.xingdun_group_join_summary else R.string.xingdun_group_invite_summary, item.groupDisplayName),
        if (item.canHandle) getString(R.string.xingdun_pending) else getString(R.string.xingdun_processed),
        item.canHandle
    )

    private fun statusText(status: Int) = getString(when (status) {
        0 -> R.string.xingdun_pending
        1 -> R.string.xingdun_agreed
        2 -> R.string.xingdun_rejected
        3 -> R.string.xingdun_expired
        else -> R.string.xingdun_unknown_status
    })

    private fun groupStatusText(status: Int) = getString(when (status) {
        1 -> R.string.xingdun_joined
        2 -> R.string.xingdun_rejected
        else -> R.string.xingdun_pending
    })
}
