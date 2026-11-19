package io.trtc.tuikit.chat.demo.main

import java.lang.ref.WeakReference

import io.trtc.tuikit.chat.demo.common.BadgeDragPolicy
import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.JsonObject
import com.tencent.imsdk.v2.V2TIMConversationOperationResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ChatContactListConfig
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItem
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactListItemIDs
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationActionIDs
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.isUnread
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.search.SearchActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunContactDetailActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunConversationPreviewCleaner
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunBlacklistActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupListActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.LushuPageView
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.trtc.tuikit.chat.demo.xingdun.features.home.XingDunHomePageView
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMinePageView
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMineAvatarView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.activity.OnBackPressedCallback
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunNotificationIntentReader
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryState
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarBadgeView
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunDirectoryPageView
import io.trtc.tuikit.chat.demo.xingdun.features.JourneyPageHeader
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFriendRequestUnread
import io.trtc.tuikit.chat.demo.xingdun.features.FriendRequestBadgePolicy
import io.trtc.tuikit.chat.demo.xingdun.features.mineCanUseIMData
import io.trtc.tuikit.chat.demo.xingdun.features.JourneyFilters
import io.trtc.tuikit.chat.uikit.components.conversationlist.ui.JourneyConversationCategory
import io.trtc.tuikit.chat.uikit.pages.ConversationsPageView
import io.trtc.tuikit.chat.uikit.pages.PopupMenuHelper
import io.trtc.tuikit.chat.uikit.pages.PopupMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupInvitationUnread
import kotlin.math.abs

private data class BottomTab(
    val tabId: Int,
    val root: LinearLayout,
    val icon: ImageView,
    val text: TextView,
    val iconResId: Int,
    val iconCutoutResId: Int = 0
)

private data class CustomerServiceContact(
    val userID: String,
    val displayName: String,
    val avatarURL: String?,
)

private data class IMConnectionNotice(
    val messageResID: Int,
    val warning: Boolean,
)

private const val BADGE_CLEAR_DRAG_THRESHOLD_DP = 48
private const val TAB_ICON_SIZE_DP = 24

// Gradient handle positions of the selected tab icon, normalized to the icon canvas.
private const val TAB_ICON_GRADIENT_LIGHT_X = 0.66f
private const val TAB_ICON_GRADIENT_LIGHT_Y = -0.33f
private const val TAB_ICON_GRADIENT_DARK_X = -0.24f
private const val TAB_ICON_GRADIENT_DARK_Y = 0.6875f

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_TARGET_TAB = "xingdun.target.tab"
        const val TAB_HOME = "home"
        const val TAB_MESSAGES = "messages"
        const val TAB_LUSHU = "lushu"
        const val TAB_CONTACTS = "contacts"
        const val TAB_PROFILE = "profile"

        private var launcherTarget: WeakReference<MainActivity>? = null

        internal fun reuseMainIntent(context: Context, tab: String): Intent? {
            val main = launcherTarget?.get() ?: return null
            val currentScope = XingDunSessionManager.currentUiSession()?.scopeKey ?: return null
            if (main.isFinishing || main.isDestroyed ||
                main.launcherScopeKey != currentScope || XingDunTenantSessionCoordinator.isLeaving() ||
                XingDunCredentialRecoveryCoordinator.state.value == XingDunCredentialRecoveryState.REDIRECTING
            ) {
                return null
            }
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_TARGET_TAB, tab)
            }
        }
    }

    private var launcherScopeKey: String? = null
    private var lushuState: Bundle? = null
    private val lushuPublisher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) (tabPageCache[R.id.demo_tab_lushu] as? LushuPageView)?.published()
    }

    private lateinit var drawerHost: DrawerLayout
    private lateinit var drawerPanel: FrameLayout
    private lateinit var mineDrawer: XingDunMinePageView
    private var mineBack: OnBackPressedCallback? = null
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: LinearLayout
    private lateinit var bottomNavContainer: FrameLayout
    private lateinit var bottomNavDivider: View
    private lateinit var messageUnreadBadge: AvatarBadgeView
    private lateinit var contactsUnreadBadge: AvatarBadgeView
    private lateinit var mainContainer: LinearLayout
    private lateinit var allBottomTabs: List<BottomTab>
    private lateinit var bottomTabs: List<BottomTab>

    private var conversationsSearchButton: ImageView? = null
    private var conversationsAddButton: ImageView? = null
    private var contactsSearchButton: ImageView? = null
    private var contactsAddButton: ImageView? = null
    private var contactsHeaderActions: LinearLayout? = null
    private var journeyCategory = JourneyConversationCategory.ALL
    private var directoryCategory = 0
    private var journeyHeader: JourneyPageHeader? = null
    private var conversationsPage: ConversationsPageView? = null
    private var contactsPage: XingDunDirectoryPageView? = null
    private var imConnectionNotice: IMConnectionNotice? = null
    private var customerServiceContacts: List<CustomerServiceContact> = emptyList()
    private var selectedTabIndex = 0
    private var renderedTabIndex = -1
    private var iconColors: List<Int>? = null
    private val tabIcons = mutableMapOf<Pair<Int, Boolean>, Drawable>()
    private val defaultTabId = if (BuildConfig.XINGDUN_HOME_ENABLED) R.id.demo_tab_home else R.id.demo_tab_messages
    private var currentTabId = defaultTabId
    private val tabPageCache = mutableMapOf<Int, View>()
    private var isDraggingMessageBadge = false
    private var messageBadgeDragStartRawX = 0f
    private var messageBadgeDragStartRawY = 0f
    private var messageBadgeAnchorLayoutListener: View.OnLayoutChangeListener? = null
    private var trackedMessageBadgeAnchorView: View? = null
    private var contactsBadgeAnchorLayoutListener: View.OnLayoutChangeListener? = null
    private var trackedContactsBadgeAnchorView: View? = null

    private val themeStore by lazy { ThemeStore.shared(this) }
    private val conversationListStore by lazy { ConversationListStore.create() }
    private val readAllCoordinator by lazy { createReadAllCoordinator(conversationListStore) }
    private val contactStore by lazy { ContactStore.shared }
    private val groupStore by lazy { GroupStore.shared }
    private var friendRequestUnreadCount = 0
    private var latestMessageBadgeUnreadCount = 0
    private val verificationUnreadCount = MutableStateFlow(0)
    private val serverGroupInvitationUnread = MutableStateFlow(XingDunGroupInvitationUnread(null, 0))
    private var mainScope: CoroutineScope? = null
    private val imSDKListener = object : V2TIMSDKListener() {
        override fun onConnecting() {
            updateIMConnectionNotice(
                IMConnectionNotice(R.string.xingdun_im_connecting_notice, warning = false),
            )
        }

        override fun onConnectSuccess() {
            updateIMConnectionNotice(null)
        }

        override fun onConnectFailed(code: Int, error: String?) {
            updateIMConnectionNotice(
                IMConnectionNotice(R.string.xingdun_im_disconnected_notice, warning = true),
            )
        }

        override fun onUserSigExpired() {
            XingDunCredentialRecoveryCoordinator.onUserSigExpired()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lushuState = savedInstanceState?.getBundle("lushu_state")
        journeyCategory = JourneyConversationCategory.values().getOrNull(savedInstanceState?.getInt("journey_category") ?: 0) ?: JourneyConversationCategory.ALL
        directoryCategory = savedInstanceState?.getInt("directory_category") ?: 0
        if (isFinishing) {
            return
        }

        setContentView(R.layout.demo_activity_main)
        currentTabId = requestedTabId(intent) ?: savedInstanceState?.getInt("main_tab", defaultTabId) ?: defaultTabId
        if (!BuildConfig.XINGDUN_HOME_ENABLED && currentTabId == R.id.demo_tab_home) {
            currentTabId = defaultTabId
        }

        XingDunSessionManager.currentSession()?.let { session ->
            XingDunCallSessionInitializer.initialize(
                this,
                session.sdkAppId,
                session.timUserId,
                session.userSig,
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) moveTaskToBack(false) else finish()
            }
        })
        setupMineDrawer()
        mainContainer = findViewById(R.id.demo_mainContainer)
        viewPager = findViewById(R.id.demo_viewPager)
        bottomNavContainer = findViewById(R.id.demo_bottomNavContainer)
        bottomNav = findViewById(R.id.demo_bottomNav)
        bottomNavDivider = findViewById(R.id.demo_bottomNavDivider)
        messageUnreadBadge = findViewById(R.id.demo_messageUnreadBadge)
        contactsUnreadBadge = findViewById(R.id.demo_contactsUnreadBadge)
        allBottomTabs = listOf(
            BottomTab(
                tabId = R.id.demo_tab_home,
                root = findViewById(R.id.demo_tab_home),
                icon = findViewById(R.id.demo_tab_home_icon),
                text = findViewById(R.id.demo_tab_home_text),
                iconResId = R.drawable.xingdun_ic_tab_home
            ),
            BottomTab(
                tabId = R.id.demo_tab_lushu,
                root = findViewById(R.id.demo_tab_lushu),
                icon = findViewById(R.id.demo_tab_lushu_icon),
                text = findViewById(R.id.demo_tab_lushu_text),
                iconResId = R.drawable.xingdun_ic_tab_lushu
            ),
            BottomTab(
                tabId = R.id.demo_tab_messages,
                root = findViewById(R.id.demo_tab_messages),
                icon = findViewById(R.id.demo_tab_messages_icon),
                text = findViewById(R.id.demo_tab_messages_text),
                iconResId = R.drawable.demo_ic_tab_messages,
                iconCutoutResId = R.drawable.demo_ic_tab_messages_lines
            ),
            BottomTab(
                tabId = R.id.demo_tab_contacts,
                root = findViewById(R.id.demo_tab_contacts),
                icon = findViewById(R.id.demo_tab_contacts_icon),
                text = findViewById(R.id.demo_tab_contacts_text),
                iconResId = R.drawable.demo_ic_tab_contacts
            )
        )
        bottomTabs = allBottomTabs.filter { BuildConfig.XINGDUN_HOME_ENABLED || it.tabId != R.id.demo_tab_home }
        allBottomTabs.forEach { tab ->
            bottomNav.removeView(tab.root)
        }
        bottomTabs.forEach { tab ->
            bottomNav.addView(tab.root)
        }

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            bottomNav.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupViewPager()
        setupBottomNav()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
        selectRequestedTab(intent)
        XingDunNotificationIntentReader.readAndStage(intent, "main_create")
        if (savedInstanceState?.getBoolean("mine_drawer_open") == true) drawerHost.post { openMineDrawer() }
        XingDunRouter.markMainReady()
        XingDunRouter.consumePendingRoute()
        V2TIMManager.getInstance().addIMSDKListener(imSDKListener)
        launcherScopeKey = XingDunSessionManager.currentUiSession()?.scopeKey
        launcherTarget = WeakReference(this)
    }

    override fun onDestroy() {
        if (launcherTarget?.get() === this) launcherTarget = null
        V2TIMManager.getInstance().removeIMSDKListener(imSDKListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectRequestedTab(intent)
        XingDunNotificationIntentReader.readAndStage(intent, "main_intent")
        XingDunRouter.markMainReady()
        XingDunRouter.consumePendingRoute()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("mine_drawer_open", ::drawerHost.isInitialized && drawerHost.isDrawerVisible(Gravity.RIGHT))
        outState.putBundle("lushu_state", (tabPageCache[R.id.demo_tab_lushu] as? LushuPageView)?.saveState() ?: lushuState)
        outState.putInt("main_tab", currentTabId)
        outState.putInt("journey_category",journeyCategory.ordinal)
        outState.putInt("directory_category",contactsPage?.category ?: directoryCategory)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (::mineDrawer.isInitialized) mineDrawer.setActive(drawerHost.isDrawerVisible(Gravity.RIGHT))
        (tabPageCache[R.id.demo_tab_home] as? XingDunHomePageView)?.setActive(currentTabId == R.id.demo_tab_home)
        (tabPageCache[R.id.demo_tab_lushu] as? LushuPageView)?.setActive(currentTabId == R.id.demo_tab_lushu)
        mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        XingDunConversationPreviewCleaner.cleanupExistingConversationPreviews(this)
        refreshUnreadCounts()
        if (currentTabId == R.id.demo_tab_contacts) contactsPage?.refresh()

        mainScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        mainScope?.launch {
            XingDunCredentialRecoveryCoordinator.state.collectLatest { recoveryState ->
                when (recoveryState) {
                    XingDunCredentialRecoveryState.REFRESHING,
                    XingDunCredentialRecoveryState.AUTHENTICATING -> updateIMConnectionNotice(
                        IMConnectionNotice(R.string.xingdun_im_credential_refreshing_notice, warning = true),
                    )
                    XingDunCredentialRecoveryState.NORMAL -> {
                        if (imConnectionNotice?.messageResID == R.string.xingdun_im_credential_refreshing_notice) {
                            updateIMConnectionNotice(null)
                        }
                    }
                    XingDunCredentialRecoveryState.REDIRECTING -> Unit
                }
            }
        }
        mainScope?.launch {
            val nativeFriendUnread=combine(contactStore.state.friendApplicationUnreadCount,LoginStore.shared.loginState.loginUserInfo) { count,profile ->
                if(mineCanUseIMData(XingDunSessionManager.currentUiSession()?.timUserId,profile?.userID))count else 0
            }
            combine(
                conversationListStore.state.totalUnreadCount,
                nativeFriendUnread,
                groupStore.state.unreadApplicationCount,
                serverGroupInvitationUnread,
                XingDunFriendRequestUnread.repository.state,
            ) { chats,nativeFriends,groups,serverGroups,serverFriends ->
                val owner=XingDunSessionManager.currentUiSession()?.scopeKey
                val friends=FriendRequestBadgePolicy.friends(nativeFriends,serverFriends,owner)
                Triple(FriendRequestBadgePolicy.total(chats.toLong(),friends),friends,
                    FriendRequestBadgePolicy.total(groups.toLong()+serverGroups.forOwner(owner),friends))
            }.collectLatest { counts ->
                friendRequestUnreadCount=counts.second
                verificationUnreadCount.value=counts.third
                updateTabBadge(R.id.demo_tab_messages,counts.first)
                updateFriendRequestEntry()
            }
        }
        mainScope?.launch {
            while (isActive) {
                try { XingDunFriendRequestUnread.repository.refresh() }
                catch(error:CancellationException) { throw error }
                catch(_:Exception) { /* Keep unread until the same account is successfully refreshed. */ }
                val owner = XingDunSessionManager.currentUiSession()?.scopeKey
                if (serverGroupInvitationUnread.value.owner != owner) {
                    serverGroupInvitationUnread.value = XingDunGroupInvitationUnread(owner, 0)
                }
                if (owner != null) {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            val session = checkNotNull(XingDunSessionManager.currentSession())
                            val data = XingDunSessionManager.apiClient().get<JsonObject>(
                                session, "team/invitationUnreadCount", emptyMap(), JsonObject::class.java)
                            XingDunGroupInvitationUnread.parse(owner, data)
                        }
                        if (XingDunSessionManager.currentUiSession()?.scopeKey == owner) {
                            serverGroupInvitationUnread.value = count
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* Keep the same owner's last badge; the inbox exposes load errors. */ }
                }
                delay(15_000)
            }
        }
        mainScope?.launch {
            groupStore.groupEventFlow.collectLatest { event ->
                when (event) {
                    is GroupEvent.OnKickedFromGroup -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.demo_group_event_kicked
                    )
                    is GroupEvent.OnGroupDismissed -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.demo_group_event_dismissed
                    )
                    else -> Unit
                }
            }
        }
    }

    override fun onStop() {
        (tabPageCache[R.id.demo_tab_home] as? XingDunHomePageView)?.setActive(false)
        (tabPageCache[R.id.demo_tab_lushu] as? LushuPageView)?.setActive(false)
        if (::mineDrawer.isInitialized) mineDrawer.setActive(false)
        super.onStop()
        mainScope?.cancel()
        mainScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        (tabPageCache[R.id.demo_tab_home] as? XingDunHomePageView)?.updateColors()
        (tabPageCache[R.id.demo_tab_lushu] as? LushuPageView)?.updateColors()
        updateStatusBarAreaColor(colors)
        bottomNavContainer.setBackgroundColor(colors.bgColorBottomBar)
        bottomNav.setBackgroundColor(colors.bgColorBottomBar)
        bottomNavDivider.setBackgroundColor(colors.strokeColorModule)

        conversationsSearchButton?.setColorFilter(colors.textColorPrimary)
        conversationsAddButton?.setColorFilter(colors.textColorPrimary)
        contactsSearchButton?.setColorFilter(colors.textColorPrimary)
        contactsAddButton?.setColorFilter(colors.textColorPrimary)

        updateSelectedTabColors(colors)
        if (::messageUnreadBadge.isInitialized) {
            messageUnreadBadge.updateColors()
        }
        if (::contactsUnreadBadge.isInitialized) {
            contactsUnreadBadge.updateColors()
        }
    }

    private fun setupViewPager() {
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectTab(position, updatePager = false)
            }
        })
        rebuildPages()
    }

    private fun rebuildPages() {
        val pages = bottomTabs.map { getOrCreatePage(it.tabId) }
        viewPager.adapter = TabPagerAdapter(pages)
        viewPager.offscreenPageLimit = pages.size
        val targetIndex = bottomTabs.indexOfFirst { it.tabId == currentTabId }.coerceAtLeast(0)
        viewPager.setCurrentItem(targetIndex, false)
        selectTab(targetIndex, updatePager = false)
    }

    private fun getOrCreatePage(tabId: Int): View {
        return tabPageCache.getOrPut(tabId) {
            when (tabId) {
                R.id.demo_tab_home -> XingDunHomePageView(this, verificationUnreadCount) {
                    serverGroupInvitationUnread.value.forOwner(XingDunSessionManager.currentUiSession()?.scopeKey) > 0
                }
                R.id.demo_tab_lushu -> LushuPageView(this) { lushuPublisher.launch(TravelActivity.intent(this, "intent")) }.apply { restoreState(lushuState) }
                R.id.demo_tab_contacts -> createContactsPage()
                else -> createConversationsPage()
            }
        }
    }

    private fun setupBottomNav() {
        bindTabClicks()
        selectTab(bottomTabs.indexOfFirst { it.tabId == currentTabId }.coerceAtLeast(0), updatePager = false)
        setupMessageBadgeDrag()
        setupContactsBadgeLayout()
    }

    private fun bindTabClicks() {
        bottomTabs.forEachIndexed { index, tab ->
            tab.root.setOnClickListener {
                selectTab(index, updatePager = true)
            }
        }
    }

    private fun selectTab(index: Int, updatePager: Boolean) {
        if (index !in bottomTabs.indices) {
            return
        }
        selectedTabIndex = index
        val previousTabId = currentTabId
        currentTabId = bottomTabs[index].tabId
        if (previousTabId != currentTabId && currentTabId == R.id.demo_tab_contacts &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) contactsPage?.refresh()
        (tabPageCache[R.id.demo_tab_home] as? XingDunHomePageView)?.setActive(currentTabId == R.id.demo_tab_home)
        (tabPageCache[R.id.demo_tab_lushu] as? LushuPageView)?.setActive(currentTabId == R.id.demo_tab_lushu)
        if (updatePager && viewPager.currentItem != index) {
            viewPager.setCurrentItem(index, false)
        }
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        updateSelectedTabColors(colors)
        updateStatusBarAreaColor(colors)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            appearanceLightStatusBarsOverride() ?: isColorLight(colors.bgColorOperate)
    }

    private fun updateStatusBarAreaColor(colors: ColorTokens) {
        if (!::bottomTabs.isInitialized) {
            return
        }
        mainContainer.setBackgroundColor(colors.bgColorOperate)
    }

    private fun updateSelectedTabColors(colors: ColorTokens) {
        if (!::bottomTabs.isInitialized) {
            return
        }
        val nextColors = listOf(colors.bgColorBubbleOwn, colors.textColorLink, colors.textColorTertiary)
        val themeChanged = iconColors != nextColors
        if (themeChanged) { iconColors = nextColors; tabIcons.clear() }
        if (!themeChanged && renderedTabIndex == selectedTabIndex) return
        val previous = renderedTabIndex
        renderedTabIndex = selectedTabIndex
        bottomTabs.forEachIndexed { index, tab ->
            if (!themeChanged && previous >= 0 && index != previous && index != selectedTabIndex) return@forEachIndexed
            val selected = index == selectedTabIndex
            tab.root.background = null
            tab.icon.setImageDrawable(tabIcons.getOrPut(tab.tabId to selected) { renderTabIcon(tab, colors, selected) })
            tab.text.setTextColor(
                if (selected) {
                    colors.textColorLink
                } else {
                    colors.textColorTertiary
                }
            )
        }
    }

    private fun renderTabIcon(tab: BottomTab, colors: ColorTokens, selected: Boolean): Drawable {
        val sizePx = TAB_ICON_SIZE_DP.dpToPx()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        ContextCompat.getDrawable(this, tab.iconResId)?.let { base ->
            base.setBounds(0, 0, sizePx, sizePx)
            base.draw(canvas)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            if (selected) {
                shader = LinearGradient(
                    sizePx * TAB_ICON_GRADIENT_LIGHT_X,
                    sizePx * TAB_ICON_GRADIENT_LIGHT_Y,
                    sizePx * TAB_ICON_GRADIENT_DARK_X,
                    sizePx * TAB_ICON_GRADIENT_DARK_Y,
                    colors.bgColorBubbleOwn,
                    colors.textColorLink,
                    Shader.TileMode.CLAMP
                )
            } else {
                color = colors.textColorTertiary
            }
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), fillPaint)
        if (tab.iconCutoutResId != 0) {
            ContextCompat.getDrawable(this, tab.iconCutoutResId)?.let { cutout ->
                val tinted = DrawableCompat.wrap(cutout)
                tinted.setBounds(0, 0, sizePx, sizePx)
                DrawableCompat.setTint(tinted, colors.bgColorBottomBar)
                DrawableCompat.setTintMode(tinted, PorterDuff.Mode.SRC_IN)
                tinted.draw(canvas)
            }
        }
        return BitmapDrawable(resources, bitmap)
    }

    private fun setupMessageBadgeDrag() {
        val badgeElevationPx = 24.dpToPx().toFloat()
        messageUnreadBadge.elevation = badgeElevationPx
        messageUnreadBadge.translationZ = badgeElevationPx
        messageUnreadBadge.isClickable = true
        messageUnreadBadge.isFocusable = true
        bottomNavContainer.clipChildren = false
        bottomNavContainer.clipToPadding = false
        mainContainer.clipChildren = false
        mainContainer.clipToPadding = false
        bottomNavContainer.bringChildToFront(messageUnreadBadge)
        bottomNavContainer.bringChildToFront(contactsUnreadBadge)
        messageUnreadBadge.setOnTouchListener { badgeView, event ->
            handleMessageBadgeTouch(badgeView, event)
        }
        bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (messageUnreadBadge.visibility == View.VISIBLE && !isDraggingMessageBadge) {
                positionMessageUnreadBadge()
            }
            if (contactsUnreadBadge.visibility == View.VISIBLE) {
                positionContactsUnreadBadge()
            }
        }
    }

    private fun setupContactsBadgeLayout() {
        contactsUnreadBadge.elevation = 24.dpToPx().toFloat()
        contactsUnreadBadge.translationZ = 24.dpToPx().toFloat()
    }

    private fun handleMessageBadgeTouch(badgeView: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (badgeView.visibility != View.VISIBLE) {
                    return false
                }
                isDraggingMessageBadge = true
                messageBadgeDragStartRawX = event.rawX
                messageBadgeDragStartRawY = event.rawY
                badgeView.parent?.requestDisallowInterceptTouchEvent(true)
                badgeView.animate().cancel()
                badgeView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120L).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                updateDraggingMessageBadge(badgeView, event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                val shouldClear = BadgeDragPolicy.shouldClearUnread(
                    messageBadgeDragStartRawY,
                    event.rawY,
                    BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx()
                )
                isDraggingMessageBadge = false
                badgeView.parent?.requestDisallowInterceptTouchEvent(false)
                if (shouldClear) {
                    clearAllUnreadByBadgeDrag(badgeView)
                } else {
                    resetMessageBadgeDrag(badgeView)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                isDraggingMessageBadge = false
                badgeView.parent?.requestDisallowInterceptTouchEvent(false)
                resetMessageBadgeDrag(badgeView)
                return true
            }
        }
        return false
    }

    private fun updateDraggingMessageBadge(badgeView: View, rawX: Float, rawY: Float) {
        val thresholdPx = BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx()
        val dragOffset = BadgeDragPolicy.badgeDragOffset(
            messageBadgeDragStartRawX,
            messageBadgeDragStartRawY,
            rawX,
            rawY
        )
        val progress = (abs(dragOffset.y).toFloat() / thresholdPx).coerceIn(0f, 1f)
        val scale = 1f + progress * 0.2f
        badgeView.translationX = dragOffset.x.toFloat()
        badgeView.translationY = dragOffset.y.toFloat()
        badgeView.scaleX = scale
        badgeView.scaleY = scale
    }

    private fun resetMessageBadgeDrag(badgeView: View) {
        badgeView.animate().cancel()
        badgeView.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180L)
            .withEndAction { updateMessageTabBadge(latestMessageBadgeUnreadCount) }
            .start()
    }

    private fun clearAllUnreadByBadgeDrag(badgeView: View) {
        resetMessageBadgeDrag(badgeView)
        markAllConversationsRead()
    }

    private fun markAllConversationsRead() {
        mainScope?.launch {
            try {
                if (readAllCoordinator.execute()) {
                    Toast.makeText(this@MainActivity, R.string.xingdun_read_all_success, Toast.LENGTH_SHORT).show()
                }
            } catch (_: TimeoutCancellationException) {
                showReadAllFailure()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("XingDunReadAll", failure.message.orEmpty())
                showReadAllFailure()
            }
        }
    }

    private fun showReadAllFailure() {
        Toast.makeText(this, R.string.xingdun_read_all_failed, Toast.LENGTH_LONG).show()
    }

    private fun refreshUnreadCounts() {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
    }

    private fun showGroupEventToast(groupID: String, messageResId: Int) {
        val fallbackName = groupID.ifEmpty { getString(R.string.demo_group_event_unknown_group) }
        groupStore.getGroupInfo(
            groupID,
            object : GetGroupInfoCompletionHandler {
                override fun onSuccess(groupInfo: GroupInfo) {
                    val groupName = groupInfo.groupName?.takeIf { it.isNotEmpty() } ?: fallbackName
                    Toast.makeText(this@MainActivity, getString(messageResId, groupName), Toast.LENGTH_LONG).show()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(this@MainActivity, getString(messageResId, fallbackName), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun updateTabBadge(tabId: Int, unreadCount: Int) {
        if (tabId == R.id.demo_tab_messages) {
            updateMessageTabBadge(unreadCount)
            return
        }
        if (tabId == R.id.demo_tab_contacts) {
            updateContactsTabBadge(unreadCount)
        }
    }

    private fun updateMessageTabBadge(unreadCount: Int) {
        latestMessageBadgeUnreadCount=unreadCount
        if (unreadCount <= 0) {
            if (!isDraggingMessageBadge) {
                messageUnreadBadge.visibility = View.INVISIBLE
            }
            return
        }
        if (isDraggingMessageBadge) {
            return
        }
        val text = if (unreadCount > 99) "99+" else unreadCount.toString()
        messageUnreadBadge.contentDescription=getString(R.string.friend_badge_total,unreadCount)
        messageUnreadBadge.setType(AvatarBadgeView.BadgeType.Text)
        messageUnreadBadge.setText(text)
        messageUnreadBadge.updateColors()
        messageUnreadBadge.alpha = 1f
        messageUnreadBadge.scaleX = 1f
        messageUnreadBadge.scaleY = 1f
        messageUnreadBadge.translationX = 0f
        messageUnreadBadge.translationY = 0f
        showMessageBadgeWhenReady()
    }

    private fun updateContactsTabBadge(unreadCount: Int) {
        if (unreadCount <= 0) {
            contactsUnreadBadge.visibility = View.INVISIBLE
            return
        }
        val text = if (unreadCount > 99) "99+" else unreadCount.toString()
        contactsUnreadBadge.setType(AvatarBadgeView.BadgeType.Text)
        contactsUnreadBadge.setText(text)
        contactsUnreadBadge.updateColors()
        showContactsBadgeWhenReady()
    }

    private fun showMessageBadgeWhenReady() {
        val anchorView = findTabIcon(R.id.demo_tab_messages)
        if (anchorView == null || anchorView.width == 0) {
            bottomNav.post { showMessageBadgeWhenReady() }
            return
        }
        attachMessageBadgeAnchorLayoutListener(anchorView)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        messageUnreadBadge.measure(unspec, unspec)
        applyOverlayBadgeLayoutParams(messageUnreadBadge, anchorView)
        if (messageUnreadBadge.visibility != View.VISIBLE) {
            messageUnreadBadge.visibility = View.VISIBLE
        }
    }

    private fun showContactsBadgeWhenReady() {
        val anchorView = findTabIcon(R.id.demo_tab_contacts)
        if (anchorView == null || anchorView.width == 0) {
            bottomNav.post { showContactsBadgeWhenReady() }
            return
        }
        attachContactsBadgeAnchorLayoutListener(anchorView)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        contactsUnreadBadge.measure(unspec, unspec)
        applyOverlayBadgeLayoutParams(contactsUnreadBadge, anchorView)
        if (contactsUnreadBadge.visibility != View.VISIBLE) {
            contactsUnreadBadge.visibility = View.VISIBLE
        }
    }

    private fun applyOverlayBadgeLayoutParams(badgeView: AvatarBadgeView, anchorView: View) {
        val anchorLoc = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val containerLoc = IntArray(2).also { bottomNavContainer.getLocationInWindow(it) }
        val anchorLeftInContainer = anchorLoc[0] - containerLoc[0]
        val anchorTopInContainer = anchorLoc[1] - containerLoc[1]
        val isRtl = bottomNav.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val badgeW = badgeView.width.takeIf { it > 0 }
            ?: badgeView.measuredWidth.takeIf { it > 0 }
            ?: return
        val badgeH = badgeView.height.takeIf { it > 0 }
            ?: badgeView.measuredHeight.takeIf { it > 0 }
            ?: return
        val badgePosition = BadgeDragPolicy.badgeTopEndPosition(
            anchorLeft = anchorLeftInContainer,
            anchorTop = anchorTopInContainer,
            anchorWidth = anchorView.width,
            badgeWidth = badgeW,
            badgeHeight = badgeH,
            horizontalOffsetPx = 0,
            verticalOffsetPx = 4.dpToPx(),
            isRtl = isRtl
        )
        val existing = badgeView.layoutParams as? FrameLayout.LayoutParams
        if (existing != null &&
            existing.leftMargin == badgePosition.left &&
            existing.topMargin == badgePosition.top &&
            existing.rightMargin == 0 &&
            existing.gravity == (Gravity.TOP or Gravity.LEFT) &&
            existing.width == FrameLayout.LayoutParams.WRAP_CONTENT &&
            existing.height == FrameLayout.LayoutParams.WRAP_CONTENT
        ) {
            return
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            leftMargin = badgePosition.left
            topMargin = badgePosition.top
            rightMargin = 0
            bottomMargin = 0
        }
        badgeView.layoutParams = params
    }

    private fun attachMessageBadgeAnchorLayoutListener(anchorView: View) {
        if (trackedMessageBadgeAnchorView === anchorView) return
        trackedMessageBadgeAnchorView?.let { prev ->
            messageBadgeAnchorLayoutListener?.let { prev.removeOnLayoutChangeListener(it) }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (messageUnreadBadge.visibility == View.VISIBLE && !isDraggingMessageBadge) {
                positionMessageUnreadBadge()
            }
        }
        anchorView.addOnLayoutChangeListener(listener)
        trackedMessageBadgeAnchorView = anchorView
        messageBadgeAnchorLayoutListener = listener
    }

    private fun attachContactsBadgeAnchorLayoutListener(anchorView: View) {
        if (trackedContactsBadgeAnchorView === anchorView) return
        trackedContactsBadgeAnchorView?.let { prev ->
            contactsBadgeAnchorLayoutListener?.let { prev.removeOnLayoutChangeListener(it) }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (contactsUnreadBadge.visibility == View.VISIBLE) {
                positionContactsUnreadBadge()
            }
        }
        anchorView.addOnLayoutChangeListener(listener)
        trackedContactsBadgeAnchorView = anchorView
        contactsBadgeAnchorLayoutListener = listener
    }

    private fun positionMessageUnreadBadge() {
        if (messageUnreadBadge.visibility != View.VISIBLE) return
        val anchorView = findTabIcon(R.id.demo_tab_messages) ?: return
        if (anchorView.width == 0) return
        attachMessageBadgeAnchorLayoutListener(anchorView)
        applyOverlayBadgeLayoutParams(messageUnreadBadge, anchorView)
    }

    private fun positionContactsUnreadBadge() {
        if (contactsUnreadBadge.visibility != View.VISIBLE) return
        val anchorView = findTabIcon(R.id.demo_tab_contacts) ?: return
        if (anchorView.width == 0) return
        attachContactsBadgeAnchorLayoutListener(anchorView)
        applyOverlayBadgeLayoutParams(contactsUnreadBadge, anchorView)
    }

    private fun findTabIcon(tabId: Int): ImageView? {
        return bottomTabs.firstOrNull { it.tabId == tabId }?.icon
    }

    private fun createConversationsPage(): View {
        val page = ConversationsPageView(this)
        conversationsPage = page
        val actionConfig = ChatConversationActionConfig(isSupportClearHistory = false).customizeActions {
            if (!editorContext.conversation.isUnread) {
                remove(ConversationActionIDs.MARK_UNREAD)
            }
            replace(ConversationActionIDs.DELETE) { action ->
                action.copy(action = { conversation ->
                    showDeleteConversationConfirmation(conversation.conversationID)
                })
            }
        }
        page.setup(
            config = actionConfig,
            showSearchBar = false,
            headerTitle = getString(R.string.demo_page_conversations_title),

            onConversationClick = { conversationInfo ->
                ChatActivity.start(this, conversationInfo.conversationID)
            }
        )
        val header = JourneyPageHeader(this,R.string.demo_page_conversations_title,R.string.jd_journey_subtitle)
        journeyHeader = header
        updateFriendRequestEntry()
        val filters = JourneyFilters(this,listOf(getString(R.string.jd_all),getString(R.string.jd_pinned),getString(R.string.jd_teams)),journeyCategory.ordinal,38) { index ->
            journeyCategory=JourneyConversationCategory.values()[index];page.setJourneyCategory(journeyCategory)
        }
        page.setJourneyPresentation(header,filters);page.setJourneyCategory(journeyCategory)
        applyIMConnectionNotice(page)
        return SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF23B39C.toInt())
            addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            setOnRefreshListener {
                page.refresh { success, description ->
                    isRefreshing = false
                    if (!success) {
                        Toast.makeText(
                            this@MainActivity,
                            description.orEmpty().ifBlank {
                                getString(R.string.xingdun_conversation_refresh_failed)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun updateFriendRequestEntry() {
        journeyHeader?.setFriendRequests(friendRequestUnreadCount) { XingDunVerificationMessagesActivity.start(this) }
    }

    private fun updateIMConnectionNotice(notice: IMConnectionNotice?) = runOnUiThread {
        imConnectionNotice = notice
        conversationsPage?.let(::applyIMConnectionNotice)
        contactsPage?.let(::applyIMConnectionNotice)
    }

    private fun applyIMConnectionNotice(page: ConversationsPageView) {
        val notice = imConnectionNotice
        journeyHeader?.setConnectionNotice(notice?.let { getString(it.messageResID) }); page.setConnectionNotice(null)
    }

    private fun applyIMConnectionNotice(page: XingDunDirectoryPageView) {
        val notice = imConnectionNotice
        page.setConnectionNotice(notice?.let { getString(it.messageResID) }, notice?.warning == true)
    }

    private fun showDeleteConversationConfirmation(conversationID: String) {
        val actionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 0, 24.dpToPx(), 8.dpToPx())
        }
        val messageView = TextView(this).apply {
            setText(R.string.xingdun_delete_conversation_message)
            setTextColor(android.graphics.Color.parseColor("#666666"))
            textSize = 14f
            setPadding(0, 8.dpToPx(), 0, 12.dpToPx())
        }
        actionContainer.addView(messageView)

        lateinit var dialog: android.app.AlertDialog
        fun addAction(labelRes: Int, isDestructive: Boolean = false, onClick: () -> Unit) {
            val actionView = TextView(this).apply {
                setText(labelRes)
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (isDestructive) android.R.color.holo_red_dark else android.R.color.holo_blue_dark,
                    )
                )
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }
            actionContainer.addView(
                actionView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dpToPx()),
            )
        }

        addAction(R.string.xingdun_delete_conversation_keep_history, isDestructive = true) {
            deleteConversation(conversationID, clearMessages = false)
        }
        addAction(R.string.xingdun_delete_conversation_clear_history, isDestructive = true) {
            deleteConversation(conversationID, clearMessages = true)
        }
        addAction(R.string.xingdun_cancel) {}

        dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_delete_conversation_title)
            .setView(actionContainer)
            .create()
        dialog.show()
    }

    private fun deleteConversation(conversationID: String, clearMessages: Boolean) {
        V2TIMManager.getConversationManager().deleteConversationList(
            listOf(conversationID),
            clearMessages,
            object : V2TIMValueCallback<List<V2TIMConversationOperationResult>> {
                override fun onSuccess(results: List<V2TIMConversationOperationResult>) {
                    val failure = results.firstOrNull { it.resultCode != 0 }
                    if (failure != null) {
                        showDeleteConversationFailure(failure.resultInfo)
                    } else {
                        conversationListStore.loadConversations()
                    }
                }

                override fun onError(code: Int, desc: String) {
                    showDeleteConversationFailure(desc)
                }
            },
        )
    }

    private fun showDeleteConversationFailure(description: String?) = runOnUiThread {
        Toast.makeText(
            this@MainActivity,
            description.orEmpty().ifBlank { getString(R.string.xingdun_delete_conversation_failed) },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun createContactsPage(): View {
        val page = XingDunDirectoryPageView(this, directoryCategory)
        contactsPage=page;applyIMConnectionNotice(page)
        return SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF4B6975.toInt())
            addView(page,ViewGroup.LayoutParams(-1,-1))
            setOnChildScrollUpCallback { _,_ -> page.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.journey_directory_list)?.canScrollVertically(-1)==true }
            setOnRefreshListener { page.refresh { isRefreshing=false } }
        }
    }

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun selectRequestedTab(intent: Intent?) {
        if (!::bottomTabs.isInitialized) return
        if (intent?.getStringExtra(EXTRA_TARGET_TAB) == TAB_PROFILE) {
            openMineDrawer(); intent.removeExtra(EXTRA_TARGET_TAB); return
        }
        if (::drawerHost.isInitialized) closeMineDrawer()
        val requestedTabId = requestedTabId(intent) ?: return
        val index = bottomTabs.indexOfFirst { it.tabId == requestedTabId }
        if (index >= 0) selectTab(index, updatePager = true)
        intent?.removeExtra(EXTRA_TARGET_TAB)
    }

    private fun requestedTabId(intent: Intent?): Int? = when (intent?.getStringExtra(EXTRA_TARGET_TAB)) {
        TAB_HOME -> defaultTabId
        TAB_MESSAGES -> R.id.demo_tab_messages
        TAB_LUSHU -> R.id.demo_tab_lushu
        TAB_CONTACTS -> R.id.demo_tab_contacts
        else -> null
    }

    fun selectTabByName(tab: String) {
        selectRequestedTab(Intent().putExtra(EXTRA_TARGET_TAB, tab))
    }

    private fun setupMineDrawer() {
        drawerHost = findViewById(R.id.demo_drawer_host)
        drawerPanel = findViewById(R.id.demo_mine_drawer)
        mineDrawer = XingDunMinePageView(this, close = ::closeMineDrawer, markRead = ::markAllConversationsRead,
            createTeam = { ContactFlowLauncher.showCreateGroupChatPage(this) { id -> ChatActivity.start(this, id) } })
        drawerPanel.addView(mineDrawer, FrameLayout.LayoutParams(-1, -1))
        drawerHost.setScrimColor(0x8A000000.toInt())
        drawerHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT)
        drawerHost.setDrawerTitle(Gravity.RIGHT, getString(R.string.demo_page_me_title))
        drawerHost.addOnLayoutChangeListener { view, l, _, right, _, oldL, _, oldR, _ ->
            if (right-l != oldR-oldL || drawerPanel.layoutParams.width != ((right-l)*.88f).toInt()) {
                drawerPanel.layoutParams = (drawerPanel.layoutParams as DrawerLayout.LayoutParams).apply { width = ((right-l)*.88f).toInt() }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(drawerPanel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom); insets
        }
        val back = object : OnBackPressedCallback(false) { override fun handleOnBackPressed() = closeMineDrawer() }
        mineBack = back; onBackPressedDispatcher.addCallback(this, back)
        drawerHost.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(view: View, offset: Float) { back.isEnabled = offset > 0f }
            override fun onDrawerOpened(view: View) { back.isEnabled = true; mainContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS; drawerPanel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES; mineDrawer.setActive(true) }
            override fun onDrawerClosed(view: View) {
                back.isEnabled = false; mainContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO; mineDrawer.setActive(false)
                drawerHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT)
            }
        })
    }

    fun openMineDrawer() {
        if (!::drawerHost.isInitialized) return
        drawerHost.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT)
        drawerHost.openDrawer(Gravity.RIGHT)
    }

    fun closeMineDrawer() {
        if (::drawerHost.isInitialized) drawerHost.closeDrawer(Gravity.RIGHT, false)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

class TabPagerAdapter(
    private val pages: List<View>
) : RecyclerView.Adapter<TabPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val pageView: View) : RecyclerView.ViewHolder(pageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = pages[viewType]
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = position
}
