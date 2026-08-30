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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.reflect.TypeToken
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.canHandle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.fromUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.groupDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.isJoinRequest
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
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
    private lateinit var statusIcon: ImageView
    private lateinit var statusProgress: ProgressBar
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var retry: TextView
    private lateinit var operationWarning: TextView
    private val adapter = VerificationAdapter(::respondToFriend, ::respondToServerGroup, ::respondToNativeGroup)

    private var selectedTab = Tab.FRIEND
    private var friends: List<FriendApplication> = emptyList()
    private var receivedFriends: List<FriendApplication> = emptyList()
    private var sentFriends: List<FriendApplication> = emptyList()
    private var receivedPage = 1
    private var sentPage = 1
    private var receivedHasMore = false
    private var sentHasMore = false
    private var serverGroups: List<ServerGroupInvitation> = emptyList()
    private var nativeGroups: List<GroupApplicationInfo> = emptyList()
    private var loading = false
    private var loadingMore = false
    private var loadError: String? = null
    private var operationError: String? = null
    private var groupPollingJob: Job? = null
    private val operatingRowKeys = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildPage()
        observeNativeGroups()
        refresh()
    }

    override fun onStart() {
        super.onStart()
        if (selectedTab == Tab.GROUP) {
            refreshNativeGroups()
            lifecycleScope.launch { refreshGroupApplicationsQuietly() }
            startGroupPolling()
        }
    }

    override fun onStop() {
        groupPollingJob?.cancel()
        groupPollingJob = null
        super.onStop()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BACKGROUND)
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))
        root.addView(segment(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 36.dp()).apply {
            marginStart = 16.dp(); marginEnd = 16.dp(); topMargin = 6.dp(); bottomMargin = 6.dp()
        })
        operationWarning = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF8A5A00.toInt())
            setBackgroundColor(0xFFFFF4D6.toInt())
            setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
            visibility = View.GONE
        }
        root.addView(
            operationWarning,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

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
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || selectedTab != Tab.FRIEND || loading || loadingMore) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (manager.findLastVisibleItemPosition() >=
                        this@XingDunVerificationMessagesActivity.adapter.itemCount - 3
                    ) {
                        loadMoreFriends()
                    }
                }
            })
        }
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val friend = (adapter.rowAt(viewHolder.bindingAdapterPosition) as? VerificationRow.Friend)?.value
                return if (selectedTab == Tab.FRIEND && friend?.status != STATUS_PENDING) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val friend = (adapter.rowAt(position) as? VerificationRow.Friend)?.value
                if (friend == null || friend.status == STATUS_PENDING) {
                    if (position != RecyclerView.NO_POSITION) adapter.notifyItemChanged(position)
                } else {
                    confirmDeleteFriend(friend, position)
                }
            }
        }).attachToRecyclerView(recycler)
        swipeRefresh.addView(recycler, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(swipeRefresh, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        statusIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        statusProgress = ProgressBar(this).apply {
            isIndeterminate = true
        }
        statusTitle = TextView(this).apply {
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY)
        }
        statusMessage = TextView(this).apply {
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(TEXT_SECONDARY); setPadding(28.dp(), 10.dp(), 28.dp(), 18.dp())
        }
        retry = actionButton(getString(R.string.xingdun_retry), primary = true) { refresh() }
        status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24.dp(), 80.dp(), 24.dp(), 24.dp())
            addView(statusIcon, LinearLayout.LayoutParams(72.dp(), 72.dp()).apply { bottomMargin = 14.dp() })
            addView(statusProgress, LinearLayout.LayoutParams(36.dp(), 36.dp()).apply { bottomMargin = 14.dp() })
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
        operationError = null
        render()
        if (tab == Tab.GROUP) {
            refreshNativeGroups()
            startGroupPolling()
        } else {
            groupPollingJob?.cancel()
            groupPollingJob = null
        }
    }

    private fun refresh() {
        if (loading) return
        loading = true
        loadError = null
        operationError = null
        render()
        lifecycleScope.launch {
            try {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                val client = XingDunSessionManager.apiClient()
                val received = loadFriendPage(session, Direction.RECEIVED, 1)
                val sent = loadFriendPage(session, Direction.SENT, 1)
                receivedFriends = received.list.map { it.copy(direction = Direction.RECEIVED) }
                sentFriends = sent.list.map { it.copy(direction = Direction.SENT) }
                receivedPage = received.page
                sentPage = sent.page
                receivedHasMore = received.hasMore
                sentHasMore = sent.hasMore
                mergeFriends()
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

    private suspend fun loadFriendPage(
        session: XingDunStoredSession,
        direction: Direction,
        page: Int,
    ): FriendApplicationPage {
        val path = if (direction == Direction.RECEIVED) "friend/receivedApply" else "friend/sentApply"
        val result: FriendApplicationPage = XingDunSessionManager.apiClient().get(
            session,
            path,
            mapOf("page" to page.toString(), "pageSize" to FRIEND_PAGE_SIZE.toString()),
            FriendApplicationPage::class.java,
        )
        val effectivePageSize = result.pageSize.takeIf { it > 0 } ?: FRIEND_PAGE_SIZE
        val hasMore = result.list.size >= effectivePageSize &&
            (result.total <= 0 || result.page * effectivePageSize < result.total)
        return result.copy(hasMore = hasMore)
    }

    private fun loadMoreFriends() {
        if (!receivedHasMore && !sentHasMore) return
        loadingMore = true
        lifecycleScope.launch {
            try {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                if (receivedHasMore) {
                    val page = loadFriendPage(session, Direction.RECEIVED, receivedPage + 1)
                    receivedFriends = (receivedFriends + page.list.map { it.copy(direction = Direction.RECEIVED) })
                        .distinctBy(FriendApplication::id)
                    receivedPage = page.page
                    receivedHasMore = page.hasMore
                }
                if (sentHasMore) {
                    val page = loadFriendPage(session, Direction.SENT, sentPage + 1)
                    sentFriends = (sentFriends + page.list.map { it.copy(direction = Direction.SENT) })
                        .distinctBy(FriendApplication::id)
                    sentPage = page.page
                    sentHasMore = page.hasMore
                }
                mergeFriends()
                render()
            } catch (error: Throwable) {
                showError(error.message.orEmpty())
            } finally {
                loadingMore = false
            }
        }
    }

    private fun mergeFriends() {
        friends = (receivedFriends + sentFriends).sortedWith(
            compareByDescending<FriendApplication> { it.createdAt.orEmpty() }
                .thenByDescending(FriendApplication::id),
        )
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

    private fun startGroupPolling() {
        if (groupPollingJob?.isActive == true || selectedTab != Tab.GROUP) return
        groupPollingJob = lifecycleScope.launch {
            while (isActive && selectedTab == Tab.GROUP) {
                delay(GROUP_REFRESH_INTERVAL_MS)
                refreshGroupApplicationsQuietly()
            }
        }
    }

    private suspend fun refreshGroupApplicationsQuietly() {
        val session = XingDunSessionManager.currentSession() ?: return
        runCatching {
            val type = object : TypeToken<List<ServerGroupInvitation>>() {}.type
            XingDunSessionManager.apiClient().get<List<ServerGroupInvitation>>(
                session,
                "team/invitations",
                emptyMap(),
                type,
            )
        }.onSuccess {
            serverGroups = it
            loadError = null
            refreshNativeGroups()
            render()
        }.onFailure {
            if (serverGroups.isEmpty() && nativeGroups.isEmpty()) {
                loadError = it.message.orEmpty().ifBlank { getString(R.string.xingdun_verification_load_failed) }
                render()
            }
        }
    }

    private fun render() {
        friendTab.background = rounded(if (selectedTab == Tab.FRIEND) Color.WHITE else Color.TRANSPARENT, 8f)
        groupTab.background = rounded(if (selectedTab == Tab.GROUP) Color.WHITE else Color.TRANSPARENT, 8f)
        clear.visibility = View.VISIBLE
        clear.isEnabled = selectedTab == Tab.FRIEND && friends.any { it.status != STATUS_PENDING }
        clear.alpha = if (clear.isEnabled) 1f else .35f
        operationWarning.text = operationError.orEmpty()
        operationWarning.visibility = if (operationError.isNullOrBlank()) View.GONE else View.VISIBLE

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
        statusProgress.visibility = if (loading) View.VISIBLE else View.GONE
        statusIcon.visibility = if (loading) View.GONE else View.VISIBLE
        statusIcon.setImageResource(
            if (selectedTab == Tab.FRIEND) R.drawable.xingdun_ic_contacts_new_friends
            else R.drawable.xingdun_ic_contacts_groups,
        )
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
        val operation: () -> Unit = {
            val key = friendRowKey(application)
            setRowOperating(key, true)
            lifecycleScope.launch {
                try {
                    val session = requireNotNull(XingDunSessionManager.currentSession())
                    XingDunSessionManager.apiClient().postEmpty(
                        session, if (approve) "friend/agree" else "friend/reject", mapOf("apply_id" to application.id)
                    )
                    if (approve) {
                        contactStore.loadFriends()
                        sendFriendAcceptedMessageAndOpen(application)
                    }
                    refresh()
                } catch (error: Throwable) {
                    showError(error.message.orEmpty())
                } finally {
                    setRowOperating(key, false)
                }
            }
        }
        if (approve) {
            operation()
        } else {
            confirm(
                R.string.xingdun_confirm_reject_friend,
                R.string.xingdun_confirm_reject_friend_message,
                R.string.xingdun_reject,
                operation,
            )
        }
    }

    private fun respondToServerGroup(invitation: ServerGroupInvitation, approve: Boolean) {
        val key = serverGroupRowKey(invitation)
        setRowOperating(key, true)
        lifecycleScope.launch {
            try {
                val session = requireNotNull(XingDunSessionManager.currentSession())
                XingDunSessionManager.apiClient().postEmpty(
                    session, "team/handleInvitation", mapOf("invitation_id" to invitation.id, "approve" to approve)
                )
                if (approve) {
                    groupStore.loadJoinedGroups()
                    ChatActivity.start(this@XingDunVerificationMessagesActivity, "group_${invitation.groupId}")
                }
                refresh()
            } catch (error: Throwable) {
                showError(error.message.orEmpty())
            } finally {
                setRowOperating(key, false)
            }
        }
    }

    private fun respondToNativeGroup(application: GroupApplicationInfo, approve: Boolean) {
        val key = nativeGroupRowKey(application)
        setRowOperating(key, true)
        val completion = object : CompletionHandler {
            override fun onSuccess() {
                setRowOperating(key, false)
                if (approve) {
                    groupStore.loadJoinedGroups()
                    ChatActivity.start(
                        this@XingDunVerificationMessagesActivity,
                        "group_${application.groupID}",
                    )
                }
                refreshNativeGroups()
            }
            override fun onFailure(code: Int, desc: String) {
                setRowOperating(key, false)
                showError(desc)
            }
        }
        if (approve) groupStore.acceptApplication(application, completion)
        else groupStore.refuseApplication(application, completion)
    }

    private fun sendFriendAcceptedMessageAndOpen(application: FriendApplication) {
        val userID = application.user?.timUserId?.trim().orEmpty()
        if (userID.isEmpty()) return
        val conversationID = "c2c_$userID"
        val openConversation = {
            ChatActivity.start(this@XingDunVerificationMessagesActivity, conversationID)
        }
        MessageInputStore.create(conversationID).sendMessage(
            SendMessagePayload.TextSendMessagePayload(getString(R.string.xingdun_friend_accepted_auto_message)),
            SendMessageOption(),
            object : CompletionHandler {
                override fun onSuccess() = runOnUiThread(openConversation)
                override fun onFailure(code: Int, desc: String) = runOnUiThread {
                    Toast.makeText(
                        this@XingDunVerificationMessagesActivity,
                        R.string.xingdun_friend_accepted_message_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                    openConversation()
                }
            },
        )
    }

    private fun confirmDeleteFriend(application: FriendApplication, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_confirm_delete_application)
            .setMessage(R.string.xingdun_confirm_delete_application_message)
            .setNegativeButton(android.R.string.cancel) { _, _ -> adapter.notifyItemChanged(position) }
            .setOnCancelListener { adapter.notifyItemChanged(position) }
            .setPositiveButton(R.string.xingdun_delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val session = requireNotNull(XingDunSessionManager.currentSession())
                        XingDunSessionManager.apiClient().postEmpty(
                            session,
                            "friend/deleteApply",
                            mapOf("apply_id" to application.id),
                        )
                        receivedFriends = receivedFriends.filterNot { it.id == application.id }
                        sentFriends = sentFriends.filterNot { it.id == application.id }
                        mergeFriends()
                        render()
                    } catch (error: Throwable) {
                        showError(error.message.orEmpty())
                        adapter.notifyItemChanged(position)
                    }
                }
            }
            .show()
    }

    private fun confirm(titleRes: Int, messageRes: Int, positiveRes: Int, operation: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(positiveRes) { _, _ -> operation() }
            .show()
    }

    private fun confirmClearResolvedFriends() {
        val resolved = friends.filter { it.status != STATUS_PENDING }
        if (resolved.isEmpty()) return
        confirm(
            R.string.xingdun_confirm_clear_applications,
            R.string.xingdun_confirm_clear_applications_message,
            R.string.xingdun_clear,
        ) {
            lifecycleScope.launch {
                try {
                    val session = requireNotNull(XingDunSessionManager.currentSession())
                    resolved.forEach {
                        XingDunSessionManager.apiClient().postEmpty(session, "friend/deleteApply", mapOf("apply_id" to it.id))
                    }
                    refresh()
                } catch (error: Throwable) {
                    showError(error.message.orEmpty())
                }
            }
        }
    }

    private fun setRowOperating(key: String, operating: Boolean) {
        if (operating) operatingRowKeys += key else operatingRowKeys -= key
        adapter.notifyDataSetChanged()
    }

    private fun friendRowKey(application: FriendApplication) = "friend:${application.id}"

    private fun serverGroupRowKey(invitation: ServerGroupInvitation) = "server-group:${invitation.id}"

    private fun nativeGroupRowKey(application: GroupApplicationInfo) =
        "native-group:${application.groupID}:${application.fromUser}"

    private fun rowKey(row: VerificationRow) = when (row) {
        is VerificationRow.Friend -> friendRowKey(row.value)
        is VerificationRow.ServerGroup -> serverGroupRowKey(row.value)
        is VerificationRow.NativeGroup -> nativeGroupRowKey(row.value)
    }

    private fun showError(message: String) {
        operationError = message.ifBlank { getString(R.string.xingdun_action_failed) }
        render()
        Toast.makeText(this, operationError, Toast.LENGTH_LONG).show()
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
        private const val SUCCESS = 0xFF2FA86F.toInt()
        private const val SEGMENT_BG = 0xFFE9ECEF.toInt()
        private const val STATUS_PENDING = 0
        private const val FRIEND_PAGE_SIZE = 20
        private const val GROUP_REFRESH_INTERVAL_MS = 5_000L

        fun start(context: Context) = context.startActivity(Intent(context, XingDunVerificationMessagesActivity::class.java))
    }

    private enum class Tab { FRIEND, GROUP }
    private enum class Direction { RECEIVED, SENT }

    private data class FriendApplicationPage(
        val total: Int = 0,
        val list: List<FriendApplication> = emptyList(),
        val page: Int = 1,
        val pageSize: Int = 20,
        val unreadCount: Int = 0,
        val hasMore: Boolean = false,
    )

    private data class FriendApplication(
        val id: Int = 0,
        val fromUserId: Int = 0,
        val toUserId: Int = 0,
        val applyMsg: String? = null,
        val createdAt: String? = null,
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
        fun rowAt(position: Int): VerificationRow? = rows.getOrNull(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                background = rounded(Color.WHITE, 0f)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val header = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val avatar = Avatar(parent.context)
            header.addView(avatar, LinearLayout.LayoutParams(44.dp(), 44.dp()))
            val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(TEXT_PRIMARY); maxLines = 1 }
            val detail = TextView(parent.context).apply { textSize = 12f; setTextColor(TEXT_SECONDARY); maxLines = 1 }
            val friendSummary = TextView(parent.context).apply { textSize = 13f; setTextColor(TEXT_SECONDARY); maxLines = 2 }
            texts.addView(name); texts.addView(detail); texts.addView(friendSummary)
            header.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dp(); marginEnd = 8.dp()
            })
            val headerAccessory = accessoryViews()
            header.addView(headerAccessory.container, LinearLayout.LayoutParams(124.dp(), 34.dp()))
            row.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val groupSummary = TextView(parent.context).apply {
                textSize = 13f
                setTextColor(TEXT_PRIMARY)
                setPadding(0, 10.dp(), 0, 8.dp())
                maxLines = 3
            }
            row.addView(groupSummary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val footerAccessory = accessoryViews()
            row.addView(footerAccessory.container, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34.dp()))
            return Holder(
                row,
                avatar,
                name,
                detail,
                friendSummary,
                groupSummary,
                headerAccessory,
                footerAccessory,
            )
        }

        private fun accessoryViews(): AccessoryViews {
            val actions = LinearLayout(this@XingDunVerificationMessagesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            val reject = actionButton(getString(R.string.xingdun_reject), false) {}
            val accept = actionButton(getString(R.string.xingdun_accept), true) {}
            actions.addView(reject, LinearLayout.LayoutParams(58.dp(), 34.dp()).apply { marginEnd = 8.dp() })
            actions.addView(accept, LinearLayout.LayoutParams(58.dp(), 34.dp()))
            val state = TextView(this@XingDunVerificationMessagesActivity).apply {
                textSize = 13f
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setTextColor(TEXT_SECONDARY)
            }
            val progress = ProgressBar(this@XingDunVerificationMessagesActivity).apply {
                isIndeterminate = true
                visibility = View.GONE
            }
            val container = FrameLayout(this@XingDunVerificationMessagesActivity).apply {
                addView(actions, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
                addView(state, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
                addView(progress, FrameLayout.LayoutParams(26.dp(), 26.dp(), Gravity.END or Gravity.CENTER_VERTICAL))
            }
            return AccessoryViews(container, actions, reject, accept, state, progress)
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
            holder.detail.text = presentation.detail
            holder.detail.visibility = if (presentation.detail.isBlank()) View.GONE else View.VISIBLE
            holder.friendSummary.text = presentation.summary
            holder.groupSummary.text = presentation.summary
            val isFriend = row is VerificationRow.Friend
            holder.friendSummary.visibility = if (isFriend) View.VISIBLE else View.GONE
            holder.groupSummary.visibility = if (isFriend) View.GONE else View.VISIBLE
            holder.headerAccessory.container.visibility = if (isFriend) View.VISIBLE else View.GONE
            holder.footerAccessory.container.visibility = if (isFriend) View.GONE else View.VISIBLE
            val operating = rowKey(row) in operatingRowKeys
            bindAccessory(holder.headerAccessory, row, presentation, operating)
            bindAccessory(holder.footerAccessory, row, presentation, operating)
        }

        private fun bindAccessory(
            views: AccessoryViews,
            row: VerificationRow,
            presentation: Presentation,
            operating: Boolean,
        ) {
            views.actions.visibility = if (presentation.canHandle && !operating) View.VISIBLE else View.GONE
            views.state.visibility = if (!presentation.canHandle && !operating) View.VISIBLE else View.GONE
            views.progress.visibility = if (operating) View.VISIBLE else View.GONE
            views.state.text = listOf(presentation.statusIcon, presentation.status)
                .filter(String::isNotEmpty)
                .joinToString(" ")
            views.state.setTextColor(presentation.statusColor)
            views.reject.setOnClickListener { dispatch(row, false) }
            views.accept.setOnClickListener { dispatch(row, true) }
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
            val detail: TextView,
            val friendSummary: TextView,
            val groupSummary: TextView,
            val headerAccessory: AccessoryViews,
            val footerAccessory: AccessoryViews,
        ) : RecyclerView.ViewHolder(itemView)

    }

    private data class AccessoryViews(
        val container: FrameLayout,
        val actions: LinearLayout,
        val reject: TextView,
        val accept: TextView,
        val state: TextView,
        val progress: ProgressBar,
    )

    private data class Presentation(
        val name: String,
        val avatar: String?,
        val detail: String,
        val summary: String,
        val status: String,
        val canHandle: Boolean,
        val statusIcon: String,
        val statusColor: Int,
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
        val state = statusPresentation(item.status)
        return Presentation(
            name,
            user?.avatar,
            "",
            summary,
            state.text,
            item.direction == Direction.RECEIVED && item.status == STATUS_PENDING,
            state.icon,
            state.color,
        )
    }

    private fun serverGroupPresentation(item: ServerGroupInvitation) = Presentation(
        item.inviterName.takeIf(String::isNotBlank) ?: item.inviterUserId,
        item.inviterAvatar,
        item.groupId,
        item.message?.takeIf(String::isNotBlank) ?: getString(R.string.xingdun_group_invitation_summary, item.groupName.ifBlank { item.groupId }),
        groupStatusText(item.status),
        item.status == STATUS_PENDING,
        if (item.status == 1) "✓" else if (item.status == 2) "✕" else "",
        if (item.status == 1) SUCCESS else TEXT_SECONDARY,
    )

    private fun nativeGroupPresentation(item: GroupApplicationInfo) = Presentation(
        item.fromUserDisplayName,
        item.fromUserAvatarURL,
        item.groupID,
        getString(if (item.isJoinRequest) R.string.xingdun_group_join_summary else R.string.xingdun_group_invite_summary, item.groupDisplayName),
        if (item.canHandle) getString(R.string.xingdun_pending) else getString(R.string.xingdun_processed),
        item.canHandle,
        if (item.canHandle) "" else "✓",
        if (item.canHandle) TEXT_SECONDARY else SUCCESS,
    )

    private data class StatusPresentation(val text: String, val icon: String, val color: Int)

    private fun statusPresentation(status: Int) = when (status) {
        1 -> StatusPresentation(getString(R.string.xingdun_agreed), "✓", SUCCESS)
        2 -> StatusPresentation(getString(R.string.xingdun_rejected), "✕", TEXT_SECONDARY)
        3 -> StatusPresentation(getString(R.string.xingdun_expired), "○", TEXT_SECONDARY)
        0 -> StatusPresentation(getString(R.string.xingdun_pending), "", TEXT_SECONDARY)
        else -> StatusPresentation(getString(R.string.xingdun_unknown_status), "", TEXT_SECONDARY)
    }

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
