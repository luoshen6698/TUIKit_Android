package io.trtc.tuikit.chat.demo.main

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
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.Bundle
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
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.JsonObject
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
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.search.SearchActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunContactDetailActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunBlacklistActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupListActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity
import io.trtc.tuikit.chat.demo.xingdun.features.workspace.XingDunWorkspacePageView
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunMinePageView
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarBadgeView
import io.trtc.tuikit.chat.uikit.pages.ContactsPageView
import io.trtc.tuikit.chat.uikit.pages.ConversationsPageView
import io.trtc.tuikit.chat.uikit.pages.PopupMenuHelper
import io.trtc.tuikit.chat.uikit.pages.PopupMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        const val TAB_MESSAGES = "messages"
        const val TAB_WORKSPACE = "workspace"
        const val TAB_CONTACTS = "contacts"
        const val TAB_PROFILE = "profile"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: LinearLayout
    private lateinit var bottomNavContainer: FrameLayout
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
    private var contactsPage: ContactsPageView? = null
    private var customerServiceContacts: List<CustomerServiceContact> = emptyList()
    private var selectedTabIndex = 0
    private var currentTabId = R.id.demo_tab_messages
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
    private val contactStore by lazy { ContactStore.shared }
    private val groupStore by lazy { GroupStore.shared }
    private val verificationUnreadCount = MutableStateFlow(0)
    private var mainScope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }

        setContentView(R.layout.demo_activity_main)

        mainContainer = findViewById(R.id.demo_mainContainer)
        viewPager = findViewById(R.id.demo_viewPager)
        bottomNavContainer = findViewById(R.id.demo_bottomNavContainer)
        bottomNav = findViewById(R.id.demo_bottomNav)
        messageUnreadBadge = findViewById(R.id.demo_messageUnreadBadge)
        contactsUnreadBadge = findViewById(R.id.demo_contactsUnreadBadge)
        allBottomTabs = listOf(
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
            ),
            BottomTab(
                tabId = R.id.demo_tab_calls,
                root = findViewById(R.id.demo_tab_calls),
                icon = findViewById(R.id.demo_tab_calls_icon),
                text = findViewById(R.id.demo_tab_calls_text),
                iconResId = R.drawable.demo_ic_tab_calls
            ),
            BottomTab(
                tabId = R.id.demo_tab_me,
                root = findViewById(R.id.demo_tab_me),
                icon = findViewById(R.id.demo_tab_me_icon),
                text = findViewById(R.id.demo_tab_me_text),
                iconResId = R.drawable.demo_ic_tab_me
            )
        )
        bottomTabs = allBottomTabs
        allBottomTabs.forEach { tab ->
            bottomNav.removeView(tab.root)
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
        refreshUnreadCounts()
        selectRequestedTab(intent)
        XingDunRouter.consumePendingRoute()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectRequestedTab(intent)
    }

    override fun onStart() {
        super.onStart()
        mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        refreshUnreadCounts()
        contactsPage?.let(::refreshCustomerServiceContacts)

        mainScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        mainScope?.launch {
            conversationListStore.state.totalUnreadCount.collectLatest { unreadCount ->
                updateTabBadge(R.id.demo_tab_messages, unreadCount.toInt())
            }
        }
        mainScope?.launch {
            combine(
                contactStore.state.friendApplicationUnreadCount,
                groupStore.state.unreadApplicationCount
            ) { friendApplicationUnreadCount, groupApplicationUnreadCount ->
                friendApplicationUnreadCount + groupApplicationUnreadCount
            }.collectLatest { unreadCount ->
                verificationUnreadCount.value = unreadCount
                updateTabBadge(R.id.demo_tab_contacts, unreadCount)
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
        super.onStop()
        mainScope?.cancel()
        mainScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        updateStatusBarAreaColor(colors)
        bottomNavContainer.setBackgroundColor(colors.bgColorTopBar)
        bottomNav.background = roundedBackground(colors.bgColorBottomBar, 32f)

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
                R.id.demo_tab_calls -> createCallsPage()
                R.id.demo_tab_contacts -> createContactsPage()
                R.id.demo_tab_me -> createMePage()
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
        currentTabId = bottomTabs[index].tabId
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
        bottomTabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            tab.root.background = if (selected) {
                roundedBackground(colors.bgColorInput, 28f)
            } else {
                null
            }
            tab.icon.setImageDrawable(renderTabIcon(tab, colors, selected))
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
                DrawableCompat.setTint(tinted, if (selected) colors.bgColorInput else colors.bgColorBottomBar)
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
            .start()
    }

    private fun clearAllUnreadByBadgeDrag(badgeView: View) {
        badgeView.animate().cancel()
        badgeView.animate()
            .translationX(0f)
            .translationY(-BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx().toFloat() * 1.5f)
            .scaleX(0.6f)
            .scaleY(0.6f)
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                badgeView.visibility = View.INVISIBLE
                badgeView.translationX = 0f
                badgeView.translationY = 0f
                badgeView.scaleX = 1f
                badgeView.scaleY = 1f
                badgeView.alpha = 1f
            }
            .start()
        runCatching {
            conversationListStore.clearConversationUnreadCount("")
        }.onSuccess {
            Toast.makeText(this, R.string.demo_clear_all_unread_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            resetMessageBadgeDrag(badgeView)
        }
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
        val addButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_add_circle)
            contentDescription = getString(R.string.xingdun_more_message_actions)
            layoutParams = ViewGroup.LayoutParams(24.dpToPx(), 24.dpToPx())
        }
        val searchButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_search)
            contentDescription = getString(R.string.xingdun_global_search)
            layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                marginEnd = 16.dpToPx()
            }
            setOnClickListener { SearchActivity.start(this@MainActivity) }
        }
        conversationsSearchButton = searchButton
        conversationsAddButton = addButton
        addButton.setOnClickListener { anchor ->
            PopupMenuHelper(this).show(
                anchor,
                listOf(
                    PopupMenuItem(
                        title = getString(R.string.xingdun_start_group_chat),
                        onClick = {
                            ContactFlowLauncher.showCreateGroupChatPage(this) { conversationId ->
                                ChatActivity.start(this, conversationId)
                            }
                        },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.xingdun_add_friend_menu),
                        onClick = {
                            XingDunFeatureActivity.start(
                                this,
                                XingDunFeatureActivity.MODE_FRIEND_SEARCH,
                                selectedTab = TAB_MESSAGES,
                            )
                        },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.xingdun_scan_qr),
                        onClick = {
                            XingDunFeatureActivity.start(
                                this,
                                XingDunFeatureActivity.MODE_QR_SCANNER,
                                selectedTab = TAB_MESSAGES,
                            )
                        },
                        iconResId = R.drawable.xingdun_ic_mine_qr
                    )
                )
            )
        }
        val headerActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(searchButton)
            addView(addButton)
        }
        page.setup(
            config = actionConfig,
            showSearchBar = false,
            headerTitle = getString(R.string.demo_page_conversations_title),
            headerRightAction = headerActions,
            onConversationClick = { conversationInfo ->
                ChatActivity.start(this, conversationInfo.conversationID)
            }
        )
        return page
    }

    private fun showDeleteConversationConfirmation(conversationID: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_delete_conversation_title)
            .setMessage(R.string.xingdun_delete_conversation_message)
            .setItems(
                arrayOf(
                    getString(R.string.xingdun_delete_conversation_keep_history),
                    getString(R.string.xingdun_delete_conversation_clear_history),
                )
            ) { _, which ->
                when (which) {
                    0 -> conversationListStore.deleteConversation(conversationID)
                    1 -> clearHistoryAndDeleteConversation(conversationID)
                }
            }
            .setNegativeButton(R.string.xingdun_cancel, null)
            .show()
    }

    private fun clearHistoryAndDeleteConversation(conversationID: String) {
        conversationListStore.clearConversationMessages(
            conversationID,
            object : CompletionHandler {
                override fun onSuccess() {
                    conversationListStore.deleteConversation(conversationID)
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(
                        this@MainActivity,
                        desc.ifBlank { getString(R.string.xingdun_delete_conversation_failed) },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    private fun createContactsPage(): View {
        val page = ContactsPageView(this)
        contactsPage = page
        val searchButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_search)
            contentDescription = getString(R.string.xingdun_global_search)
            layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                marginEnd = 16.dpToPx()
            }
            setOnClickListener { SearchActivity.start(this@MainActivity) }
        }
        val addButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_add_circle)
            contentDescription = getString(R.string.xingdun_more_contact_actions)
            layoutParams = ViewGroup.LayoutParams(24.dpToPx(), 24.dpToPx())
        }
        contactsSearchButton = searchButton
        contactsAddButton = addButton
        addButton.setOnClickListener { anchor ->
            PopupMenuHelper(this).show(
                anchor,
                listOf(
                    PopupMenuItem(
                        title = getString(R.string.demo_add_friend),
                        onClick = {
                            XingDunFeatureActivity.start(
                                this,
                                XingDunFeatureActivity.MODE_FRIEND_SEARCH,
                                selectedTab = TAB_CONTACTS,
                            )
                        },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.xingdun_start_group_chat),
                        onClick = {
                            ContactFlowLauncher.showCreateGroupChatPage(this) { conversationId ->
                                ChatActivity.start(this, conversationId)
                            }
                        },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.xingdun_scan_qr),
                        onClick = {
                            XingDunFeatureActivity.start(
                                this,
                                XingDunFeatureActivity.MODE_QR_SCANNER,
                                selectedTab = TAB_CONTACTS,
                            )
                        },
                        iconResId = R.drawable.xingdun_ic_mine_qr
                    )
                )
            )
        }
        val headerActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(searchButton)
            addView(addButton)
        }
        contactsHeaderActions = headerActions
        configureContactsPage(page)
        return SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF23B39C.toInt())
            addView(page, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            setOnRefreshListener {
                page.refresh { isRefreshing = false }
                refreshCustomerServiceContacts(page)
            }
        }
    }

    private fun createContactsPageConfiguration() = ChatContactListConfig(
            showGroupApplications = false,
            showSearchBar = false,
            excludedContactIDs = customerServiceContacts.map(CustomerServiceContact::userID).toSet(),
        ).customizeItems {
            replace(ContactListItemIDs.NEW_CONTACTS) { item ->
                item.copy(
                    title = getString(R.string.xingdun_new_friends),
                    titleResID = 0,
                    iconResID = R.drawable.xingdun_ic_contacts_new_friends,
                    badgeCount = verificationUnreadCount,
                    onClick = { XingDunVerificationMessagesActivity.start(this@MainActivity) }
                )
            }
            replace(ContactListItemIDs.MY_GROUPS) { item ->
                item.copy(
                    title = getString(R.string.xingdun_my_groups),
                    titleResID = 0,
                    iconResID = R.drawable.xingdun_ic_contacts_groups,
                    onClick = { XingDunGroupListActivity.start(this@MainActivity) }
                )
            }
            replace(ContactListItemIDs.BLACKLIST) { item ->
                item.copy(
                    iconResID = R.drawable.xingdun_ic_contacts_blacklist,
                    onClick = { XingDunBlacklistActivity.start(this@MainActivity) },
                )
            }
            customerServiceContacts.forEachIndexed { index, service ->
                add(ContactCustomItem(
                    ID = "xingdun.customerService.$index",
                    title = service.displayName,
                    avatarURL = service.avatarURL,
                    iconResID = if (service.avatarURL.isNullOrBlank()) {
                        R.drawable.xingdun_ic_contacts_customer_service
                    } else {
                        0
                    },
                    sectionTitle = getString(R.string.xingdun_my_customer_service),
                    onClick = {
                        XingDunContactDetailActivity.start(
                            this@MainActivity,
                            service.userID,
                            service.displayName,
                            service.avatarURL,
                        )
                    },
                ))
            }
        }

    private fun refreshCustomerServiceContacts(page: ContactsPageView) {
        val session = XingDunSessionManager.currentSession()
        if (session?.features?.customerService != true) {
            if (customerServiceContacts.isNotEmpty()) {
                customerServiceContacts = emptyList()
                configureContactsPage(page)
            }
            return
        }
        val scope = mainScope ?: return
        scope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "cs/identity",
                    emptyMap(),
                    JsonObject::class.java,
                )
            }.onSuccess { identity ->
                val enabled = identity.get("customer_service_enabled")
                    ?.takeUnless { it.isJsonNull }
                    ?.let { runCatching { it.asBoolean }.getOrNull() }
                    ?: false
                val assigned = identity.getAsJsonArray("customer_services")
                    ?.mapNotNull { element ->
                        element.takeIf { it.isJsonObject }?.asJsonObject?.let { service ->
                            val userID = service.stringValue("tim_user_id") ?: return@let null
                            CustomerServiceContact(
                                userID = userID,
                                displayName = service.stringValue("nickname")
                                    ?: service.stringValue("custom_id")
                                    ?: getString(R.string.xingdun_enterprise_customer_service),
                                avatarURL = service.stringValue("avatar"),
                            )
                        }
                    }
                    .orEmpty()
                val ordinaryEntryEnabled = identity.get("ordinary_entry_enabled")
                    ?.takeUnless { it.isJsonNull }
                    ?.let { runCatching { it.asBoolean }.getOrNull() }
                    ?: false
                val fallback = identity.stringValue("official_cs_tim_user_id")
                    ?.takeIf { enabled && ordinaryEntryEnabled }
                    ?.let { userID ->
                        CustomerServiceContact(
                            userID = userID,
                            displayName = getString(R.string.xingdun_enterprise_customer_service),
                            avatarURL = null,
                        )
                    }
                val refreshed = if (enabled) {
                    (assigned.ifEmpty { listOfNotNull(fallback) }).distinctBy(CustomerServiceContact::userID)
                } else {
                    emptyList()
                }
                if (refreshed != customerServiceContacts) {
                    customerServiceContacts = refreshed
                    configureContactsPage(page)
                }
            }
        }
    }

    private fun configureContactsPage(page: ContactsPageView) {
        // Rebuild only the official header customizations; the Store-backed contact list remains intact.
        val refreshed = createContactsPageConfiguration()
        page.setup(
            config = refreshed,
            headerTitle = getString(R.string.demo_page_contacts_title),
            headerRightAction = contactsHeaderActions,
            onContactClick = { contactInfo -> XingDunContactDetailActivity.start(this, contactInfo) },
            onGroupClick = { contactInfo -> ChatActivity.start(this, "group_${contactInfo.userID}") },
        )
    }

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun createCallsPage(): View {
        return XingDunWorkspacePageView(this)
    }

    private fun selectRequestedTab(intent: Intent?) {
        if (!::bottomTabs.isInitialized) return
        val requestedTabId = when (intent?.getStringExtra(EXTRA_TARGET_TAB)) {
            TAB_WORKSPACE -> R.id.demo_tab_calls
            TAB_CONTACTS -> R.id.demo_tab_contacts
            TAB_PROFILE -> R.id.demo_tab_me
            else -> R.id.demo_tab_messages
        }
        val index = bottomTabs.indexOfFirst { it.tabId == requestedTabId }
        if (index >= 0) selectTab(index, updatePager = true)
        intent?.removeExtra(EXTRA_TARGET_TAB)
    }

    fun selectTabByName(tab: String) {
        selectRequestedTab(Intent().putExtra(EXTRA_TARGET_TAB, tab))
    }

    private fun createMePage(): View {
        return XingDunMinePageView(this)
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
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
