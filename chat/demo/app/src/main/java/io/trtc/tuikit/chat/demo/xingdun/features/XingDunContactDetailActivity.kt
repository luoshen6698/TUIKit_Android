package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.JsonObject
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicInteger

/** Android counterpart of iOS `XingDunContactDetailView`. */
class XingDunContactDetailActivity : BaseActivity() {
    private lateinit var content: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var more: View
    private lateinit var chatButton: TextView
    private lateinit var detailRefresh: SwipeRefreshLayout

    private val timUserID: String by lazy { intent.getStringExtra(EXTRA_USER_ID).orEmpty().trim() }
    private val fallbackCustomID: String? by lazy { intent.getStringExtra(EXTRA_CUSTOM_ID).normalized() }
    private val sourceGroupID: String? by lazy { intent.getStringExtra("source_group_id").normalized() }
    private var detail: XingDunContactDetail? = null
    private var isOperating = false
    private var operationError: String? = null
    private var relationshipState: String = RELATIONSHIP_UNKNOWN
    private var independentResumed = false
    private var independentLoader: XingDunIndependentProfileLoader? = null
    private var independentLoadJob: Job? = null
    private var independentPickerScope: XingDunGroupMemberPreviewScope? = null
    private var pendingIndependentRecommendation: Pair<XingDunGroupMemberPreviewScope, String>? = null
    private var groupSourceResumed = false
    private var groupSourcePageScope: XingDunGroupMemberPreviewScope? = null
    private var groupSourceState: XingDunGroupSourceProfileState = XingDunGroupSourceProfileState.Loading
    private var groupSourceRefreshJob: Job? = null
    private var groupSourceViewRevision = 0L
    private val groupSourceRenderState = XingDunGroupSourceProfileRenderState()
    private var groupSourceActionJob: Job? = null
    private var groupSourceGateBusyOwner: Long? = null
    private var groupSourceHardActionLoading = false
    private var sourceMoreMenu: PopupMenu? = null
    private var sourceDialog: AlertDialog? = null
    private val groupSourceLoader by lazy {
        XingDunGroupSourceProfileLoader(
            groupID = sourceGroupID.orEmpty(),
            targetUserID = timUserID,
            currentScope = ::profileScope,
            loadGroup = { group ->
                XingDunSessionManager.apiClient().get(
                    checkNotNull(XingDunSessionManager.currentSession()), "team/detail",
                    mapOf("team_id" to group), io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail::class.java,
                )
            },
            loadProfile = { target ->
                XingDunSessionManager.apiClient().get(
                    checkNotNull(XingDunSessionManager.currentSession()), "user/detail",
                    mapOf("tim_user_id" to target), XingDunContactDetail::class.java,
                )
            },
            loadSelfProfile = {
                XingDunSessionManager.apiClient().get(
                    checkNotNull(XingDunSessionManager.currentSession()), "user/profile",
                    emptyMap(), XingDunContactDetail::class.java,
                )
            },
            loadRelationship = { keyword ->
                XingDunSessionManager.apiClient().getNullable(
                    checkNotNull(XingDunSessionManager.currentSession()), "user/searchForFriend",
                    mapOf("keyword" to keyword), JsonObject::class.java,
                )
            },
            publish = ::applyGroupSourceState,
            isActive = { groupSourceResumed && !isFinishing && !isDestroyed },
            recommend = { conversation -> sendContactCard(listOf(conversation)) },
            showRecommendationFailure = { Toast.makeText(this, R.string.xingdun_contact_detail_recommend_failed, Toast.LENGTH_SHORT).show() },
        )
    }

    private fun profileScope(): XingDunGroupMemberPreviewScope? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val generation = XingDunSessionManager.currentAuthenticationGeneration() ?: return null
        return XingDunGroupMemberPreviewScope(session.companyCode, session.sdkAppId, session.timUserId, generation)
    }
    private val remarkEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val remark = result.data?.getStringExtra(XingDunContactRemarkActivity.EXTRA_RESULT_REMARK).orEmpty()
        detail = detail?.copy(alias = remark.normalized())
        ContactStore.shared.loadFriends()
        render()
        Toast.makeText(this, R.string.xingdun_contact_detail_remark_updated, Toast.LENGTH_SHORT).show()
    }
    private val contactForwardPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pickerScope = independentPickerScope
        independentPickerScope = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val conversationID = result.data
            ?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_CONVERSATION_ID)
            .orEmpty()
        if (conversationID.isNotBlank()) {
            if (sourceGroupID == null) {
                // ActivityResult arrives before onResume; re-read privacy before sending a card.
                if (pickerScope != null && pickerScope == profileScope()) {
                    pendingIndependentRecommendation = pickerScope to conversationID
                    if (independentResumed) loadIndependentDetail()
                }
            }
            else {
                groupSourceLoader.queueRecommendation(conversationID)
                if (groupSourceResumed) startGroupSourceRefresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (timUserID.isEmpty()) {
            finish()
            return
        }
        relationshipState = intent.getStringExtra(EXTRA_RELATIONSHIP).orEmpty().ifBlank { RELATIONSHIP_UNKNOWN }
        detail = fallbackDetail()
        buildPage()
        if (sourceGroupID == null) {
            applyIndependentState(XingDunIndependentProfileState(null, RELATIONSHIP_UNKNOWN, loading = true))
        } else {
            groupSourcePageScope = profileScope()
            // Capture this page's owner before returning from onCreate.
            groupSourceLoader
            applyGroupSourceState(XingDunGroupSourceProfileState.Loading)
        }
    }

    override fun onResume() {
        super.onResume()
        if (sourceGroupID == null && ::content.isInitialized && !isFinishing) {
            independentResumed = true
            loadIndependentDetail()
        }
        if (sourceGroupID != null && ::content.isInitialized && !isFinishing) {
            groupSourceResumed = true
            startGroupSourceRefresh()
        }
    }

    override fun onPause() {
        if (sourceGroupID == null && ::content.isInitialized) {
            independentResumed = false
            independentLoader?.invalidate()
            independentLoadJob?.cancel()
            independentLoadJob = null
            sourceMoreMenu?.dismiss()
            sourceDialog?.dismiss()
            applyIndependentState(XingDunIndependentProfileState(null, RELATIONSHIP_UNKNOWN, loading = true))
        }
        if (sourceGroupID != null && ::content.isInitialized) {
            groupSourceResumed = false
            groupSourceViewRevision++
            cancelGroupSourceGate()
            groupSourceRefreshJob?.cancel()
            groupSourceRefreshJob = null
            groupSourceLoader.invalidate()
            applyGroupSourceState(XingDunGroupSourceProfileState.Loading)
        }
        super.onPause()
    }

    private fun cancelGroupSourceGate() {
        val ownedBusy = groupSourceGateBusyOwner != null
        val pending = groupSourceActionJob
        groupSourceGateBusyOwner = null
        groupSourceActionJob = null
        groupSourceHardActionLoading = false
        // An SDK mutation handed off after authorization owns its own busy flag until completion.
        if (ownedBusy) isOperating = false
        pending?.cancel()
    }

    private fun startGroupSourceRefresh() {
        if (!groupSourceResumed || isFinishing || isDestroyed) return
        groupSourceViewRevision++
        cancelGroupSourceGate()
        groupSourceRefreshJob?.cancel()
        // refresh() advances its request before awaiting; preserve an ON_START picker result.
        groupSourceRefreshJob = lifecycleScope.launch {
            var periodic = false
            while (isActive && groupSourceResumed) {
                if (!groupSourceHardActionLoading) {
                    groupSourceLoader.refresh(periodic)
                    periodic = true
                }
                // Business policies have no reliable shared SDK invalidation stream here.
                // One foreground job also rechecks while the same profile stays open.
                delay(10_000L)
            }
        }
    }

    private fun applyGroupSourceState(state: XingDunGroupSourceProfileState) {
        val previouslyFriend = isFriendRelationship()
        groupSourceState = state
        if (state is XingDunGroupSourceProfileState.Loaded) {
            detail = state.profile.copy(timUserId = state.profile.timUserId.trim().ifEmpty { timUserID })
            relationshipState = RELATIONSHIP_UNKNOWN
            state.relationship?.let(::applyRelationshipProfile)
            if (previouslyFriend != isFriendRelationship()) sourceMoreMenu?.dismiss()
        } else {
            operationError = null
            // Remove both rendered fields and cached data before checking authority again.
            detail = null
            relationshipState = RELATIONSHIP_UNKNOWN
        }
        render()
        loading.visibility = if (state === XingDunGroupSourceProfileState.Loading) View.VISIBLE else View.GONE
        detailRefresh.isRefreshing = false
    }

    private fun renderGroupSourceRestriction() {
        sourceMoreMenu?.dismiss()
        sourceDialog?.dismiss()
        content.removeAllViews()
        chatButton.visibility = View.GONE
        more.isEnabled = false
        more.alpha = 0f
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30.dp(), 80.dp(), 30.dp(), 30.dp())
            addView(TextView(context).apply {
                setText(when (groupSourceState) {
                    XingDunGroupSourceProfileState.Loading -> R.string.xingdun_group_info_loading
                    XingDunGroupSourceProfileState.Restricted -> R.string.xingdun_group_member_card_restricted
                    else -> R.string.xingdun_contact_detail_load_failed
                })
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(TEXT_PRIMARY)
            })
            if (groupSourceState !== XingDunGroupSourceProfileState.Loading) {
                addView(primaryButton(R.string.xingdun_retry) { loadDetail() }, LinearLayout.LayoutParams(150.dp(), 46.dp()).apply {
                    topMargin = 20.dp()
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BACKGROUND)
        }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))

        loading = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = ColorStateList.valueOf(BRAND)
            indeterminateTintList = ColorStateList.valueOf(BRAND)
        }
        root.addView(loading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dp()))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 18.dp())
        }
        val detailScroll = ScrollView(this).apply {
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        detailRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(BRAND)
            setProgressBackgroundColorSchemeColor(Color.WHITE)
            setOnRefreshListener { loadDetail() }
            addView(detailScroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(detailRefresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        chatButton = primaryButton(R.string.xingdun_contact_detail_start_chat) { openBottomAction() }
        root.addView(chatButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            marginStart = 20.dp()
            marginEnd = 20.dp()
            topMargin = 8.dp()
            bottomMargin = 12.dp()
        })

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun header(): View = FrameLayout(this).apply {
        addView(TextView(context).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            contentDescription = getString(R.string.xingdun_back)
            tag = R.string.xingdun_back
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(52.dp(), 52.dp(), Gravity.START))
        addView(TextView(context).apply {
            setText(R.string.xingdun_contact_detail_title)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            marginStart = 58.dp()
            marginEnd = 58.dp()
        })
        more = object : View(context) {
            private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val density = resources.displayMetrics.density
                val centerX = width / 2f
                val centerY = height / 2f
                val spacing = 5.5f * density
                val radius = 1.25f * density
                for (position in -1..1) {
                    canvas.drawCircle(centerX + position * spacing, centerY, radius, dotPaint)
                }
            }
        }.apply {
            background = InsetDrawable(
                rounded(0xFFF0F2F5.toInt(), 17f),
                7.dp(),
                9.dp(),
                7.dp(),
                9.dp(),
            )
            contentDescription = getString(R.string.xingdun_contact_detail_more)
            tag = R.string.xingdun_contact_detail_more
            setOnClickListener { showMoreMenu(this) }
        }
        addView(more, FrameLayout.LayoutParams(48.dp(), 52.dp(), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = 2.dp()
        })
    }

    private fun loadDetail() {
        if (sourceGroupID != null) startGroupSourceRefresh() else loadIndependentDetail()
    }

    private fun loadIndependentDetail() {
        if (!independentResumed || isFinishing || isDestroyed) return
        independentLoader?.invalidate()
        independentLoadJob?.cancel()
        val session = XingDunSessionManager.currentSession()
        if (session == null) {
            applyIndependentState(XingDunIndependentProfileState(null, RELATIONSHIP_UNKNOWN, failed = true))
            return
        }
        val loader = XingDunIndependentProfileLoader(
            targetUserID = timUserID,
            fallback = fallbackDetail(),
            initialRelationship = intent.getStringExtra(EXTRA_RELATIONSHIP).orEmpty().ifBlank { RELATIONSHIP_UNKNOWN },
            currentScope = ::profileScope,
            currentCredentials = {
                XingDunIndependentProfileCredentials.fromSession(XingDunSessionManager.currentSession())
            },
            isActive = { independentResumed && !isFinishing && !isDestroyed },
            loadProfile = {
                XingDunSessionManager.apiClient().get(
                    session, "user/detail", mapOf("tim_user_id" to timUserID), XingDunContactDetail::class.java,
                )
            },
            loadRelationship = { keyword ->
                XingDunSessionManager.apiClient().getNullable(
                    session, "user/searchForFriend", mapOf("keyword" to keyword), JsonObject::class.java,
                )
            },
            publish = ::applyIndependentState,
        )
        independentLoader = loader
        independentLoadJob = lifecycleScope.launch { loader.refresh() }
    }

    private fun applyIndependentState(state: XingDunIndependentProfileState) {
        detail = state.profile
        relationshipState = state.relationship
        operationError = null
        if (state.profile != null) render()
        else {
            content.removeAllViews()
            chatButton.visibility = View.GONE
            more.isEnabled = false
            more.alpha = 0f
            if (state.failed) showLoadFailure(getString(R.string.xingdun_action_failed))
            else content.addView(TextView(this).apply {
                setText(R.string.xingdun_loading)
                gravity = Gravity.CENTER
                setPadding(30.dp(), 80.dp(), 30.dp(), 30.dp())
                setTextColor(TEXT_PRIMARY)
            })
        }
        loading.visibility = if (state.loading) View.VISIBLE else View.GONE
        detailRefresh.isRefreshing = false
        if (!state.loading) {
            val pending = pendingIndependentRecommendation
            pendingIndependentRecommendation = null
            if (pending != null && pending.first == profileScope() && independentResumed) {
                if (state.profile != null && isFriendRelationship() && independentLoader?.ownsSnapshot() == true) {
                    sendContactCard(listOf(pending.second))
                } else Toast.makeText(this, R.string.xingdun_contact_detail_recommend_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyRelationshipProfile(profile: JsonObject) {
        val merged = mergeXingDunRelationshipProfile(
            detail ?: fallbackDetail(), profile, timUserID,
            authoritativeOwnProfile = sourceGroupID != null && timUserID == profileScope()?.userID,
        )
        detail = merged.first
        relationshipState = merged.second
    }

    private fun hasCurrentIndependentProfile(): Boolean {
        if (independentResumed && detail != null && independentLoader?.ownsSnapshot() == true) return true
        loadIndependentDetail()
        return false
    }

    private fun render() {
        if (sourceGroupID != null && groupSourceState !is XingDunGroupSourceProfileState.Loaded) {
            groupSourceRenderState.reset()
            renderGroupSourceRestriction()
            return
        }
        val current = detail ?: return
        val features = XingDunSessionManager.currentSession()?.features
        if (sourceGroupID != null && !groupSourceRenderState.shouldRender(
            current, relationshipState, features?.audioCall == true, features?.videoCall == true,
            isOperating, operationError, timUserID,
        )) return
        val focusTag = if (sourceGroupID != null) currentFocus?.tag as? Int else null
        content.removeAllViews()
        content.addView(profileCard(current), cardParams())
        operationError?.takeIf(String::isNotBlank)?.let { message ->
            content.addView(TextView(this).apply {
                text = message
                textSize = 13f
                setTextColor(0xFF8A6215.toInt())
                background = rounded(0xFFFFF4D6.toInt(), 10f)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            }, cardParams(top = 12))
        }

        val friendRelationship = isFriendRelationship()
        if (friendRelationship && (features?.audioCall == true || features?.videoCall == true)) {
            val calls = card()
            if (features.audioCall) {
                calls.addView(actionRow(R.string.xingdun_audio_call, R.drawable.xingdun_ic_call_audio) { startCall(CallMediaType.Audio) })
            }
            if (features.audioCall && features.videoCall) calls.addView(divider())
            if (features.videoCall) {
                calls.addView(actionRow(R.string.xingdun_video_call, R.drawable.xingdun_ic_call_video) { startCall(CallMediaType.Video) })
            }
            content.addView(calls, cardParams(top = 16))
        }

        content.addView(sectionLabel(R.string.xingdun_contact_detail_user_information))
        content.addView(card().apply {
            addView(valueRow(R.string.xingdun_account_type_title, getString(XingDunAccountType.label(current.accountType))))
            if (friendRelationship) {
                addView(divider())
                addView(disclosureRow(
                    R.string.xingdun_contact_detail_remark,
                    current.alias.displayValue()
                ) { showRemarkEditor() })
                addView(divider())
            }
            listOf(
                Triple("birthday", R.string.xingdun_contact_detail_birthday, current.birthday),
                Triple("phone", R.string.xingdun_contact_detail_phone, current.phone),
                Triple("email", R.string.xingdun_contact_detail_email, current.email),
                Triple("signature", R.string.xingdun_contact_detail_signature, current.signature),
            ).forEach { (field, label, value) ->
                if (XingDunProfileVisibility.visible(current.profileVisibility, field)) {
                    if (childCount > 0) addView(divider())
                    addView(valueRow(label, value.displayValue()))
                }
            }
        }, cardParams(top = 6))

        if (friendRelationship) {
            content.addView(card().apply {
                addView(blacklistRow(current.isBlacklist))
            }, cardParams(top = 16))
        }
        configureBottomAction()
        chatButton.isEnabled = !isOperating
        chatButton.alpha = if (isOperating) .55f else 1f
        more.isEnabled = !isOperating
        more.alpha = if (isOperating) .35f else 1f
        if (focusTag != null && sourceMoreMenu == null && sourceDialog == null) {
            val target = window.decorView.findViewWithTag<View>(focusTag)
            if (target != null && target.isShown && target.isEnabled && target.isFocusable) target.requestFocus()
            else if (currentFocus?.tag != focusTag) currentFocus?.clearFocus()
        }
    }

    private fun profileCard(current: XingDunContactDetail): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        val name = current.nickname.normalized() ?: getString(R.string.xingdun_contact_private_name)
        addView(Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
            setShape(Avatar.AvatarShape.Round)
            setContent(Avatar.AvatarContent.Image(current.avatar.orEmpty(), name))
        }, LinearLayout.LayoutParams(52.dp(), 52.dp()))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 0, 0, 0)
            addView(TextView(context).apply {
                text = name
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_PRIMARY)
                maxLines = 1
            })
            if (XingDunProfileVisibility.visible(current.profileVisibility, "account")) addView(TextView(context).apply {
                text = getString(
                    R.string.xingdun_contact_detail_account_id,
                    current.customId.normalized() ?: current.timUserId.ifBlank { timUserID }
                )
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, 6.dp(), 0, 0)
                setTextIsSelectable(true)
                tag = R.string.xingdun_contact_detail_account_id
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun isFriendRelationship(): Boolean =
        relationshipState == RELATIONSHIP_FRIEND || relationshipState == RELATIONSHIP_BLOCKED

    private fun canAddFriend(): Boolean = relationshipState != RELATIONSHIP_UNKNOWN &&
        relationshipState != RELATIONSHIP_FRIEND &&
        relationshipState != RELATIONSHIP_BLOCKED &&
        relationshipState != RELATIONSHIP_SELF

    private fun configureBottomAction() {
        when {
            isFriendRelationship() -> {
                chatButton.visibility = View.VISIBLE
                chatButton.setText(R.string.xingdun_contact_detail_start_chat)
                chatButton.tag = R.string.xingdun_contact_detail_start_chat
            }
            canAddFriend() -> {
                chatButton.visibility = View.VISIBLE
                chatButton.setText(R.string.xingdun_add_friend)
                chatButton.tag = R.string.xingdun_add_friend
            }
            else -> chatButton.visibility = View.GONE
        }
    }

    private fun openBottomAction() {
        if (sourceGroupID == null && !hasCurrentIndependentProfile()) return
        if (sourceGroupID != null && (!groupSourceResumed ||
            groupSourceState !is XingDunGroupSourceProfileState.Loaded || profileScope() != groupSourcePageScope)) {
            loadDetail()
            return
        }
        if (isFriendRelationship()) {
            val group = sourceGroupID
            if (group == null) ChatActivity.start(this, "c2c_$timUserID")
            else dispatchGroupSourceAction(requiresPrivateChat = true, available = ::isFriendRelationship) {
                ChatActivity.start(this, "c2c_$timUserID")
            }
        } else if (canAddFriend()) {
            if (dispatchGroupSourceAction(available = ::canAddFriend) { openFriendSearchAuthorized() }) return
            openFriendSearchAuthorized()
        }
    }

    private fun openFriendSearchAuthorized() {
        val keyword = detail?.customId.normalized() ?: timUserID
        XingDunFeatureActivity.start(this, XingDunFeatureActivity.MODE_FRIEND_SEARCH, keyword, MainActivity.TAB_CONTACTS)
    }

    private fun ownsGroupAction(scope: XingDunGroupMemberPreviewScope, revision: Long): Boolean =
        !isFinishing && !isDestroyed && groupSourceResumed && revision == groupSourceViewRevision &&
            scope == groupSourcePageScope && profileScope() == scope && groupSourceState is XingDunGroupSourceProfileState.Loaded

    private fun concealChangedGroupActionOwner(scope: XingDunGroupMemberPreviewScope, revision: Long): Boolean {
        if (revision != groupSourceViewRevision || !groupSourceResumed || isFinishing || isDestroyed || profileScope() == scope) return false
        groupSourceLoader.invalidate()
        applyGroupSourceState(XingDunGroupSourceProfileState.Loading)
        return true
    }

    /** Retained periodic display data never authorizes a new group-source action. */
    private fun dispatchGroupSourceAction(
        requiresPrivateChat: Boolean = false,
        available: () -> Boolean,
        onAllowed: () -> Unit,
    ): Boolean {
        val group = sourceGroupID ?: return !hasCurrentIndependentProfile()
        if (isOperating || !groupSourceResumed || isFinishing || isDestroyed) return true
        val scope = profileScope()
        if (scope == null || scope != groupSourcePageScope || groupSourceState !is XingDunGroupSourceProfileState.Loaded) {
            loadDetail()
            return true
        }
        val revision = ++groupSourceViewRevision
        groupSourceGateBusyOwner = revision
        isOperating = true
        groupSourceHardActionLoading = true
        operationError = null
        render()
        groupSourceActionJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            var allowed = false
            try {
                if (!groupSourceLoader.refresh() || !ownsGroupAction(scope, revision)) return@launch
                groupSourceHardActionLoading = false
                if (!available()) {
                    operationError = getString(R.string.xingdun_action_failed)
                    return@launch
                }
                if (requiresPrivateChat) {
                    try {
                        XingDunGroupSettingsAccess.authorize(group, "private_chat", timUserID)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (ownsGroupAction(scope, revision)) operationError = getString(R.string.xingdun_group_access_denied)
                        return@launch
                    }
                }
                if (ownsGroupAction(scope, revision) && !available()) {
                    operationError = getString(R.string.xingdun_action_failed)
                    return@launch
                }
                allowed = ownsGroupAction(scope, revision)
            } finally {
                if (revision == groupSourceViewRevision && groupSourceGateBusyOwner == revision) {
                    groupSourceGateBusyOwner = null
                    groupSourceHardActionLoading = false
                    groupSourceActionJob = null
                    isOperating = false
                    if (!concealChangedGroupActionOwner(scope, revision) && !isFinishing && !isDestroyed && groupSourceResumed && profileScope() == scope) render()
                }
            }
            // Release the temporary busy guard before handing off to existing SDK mutation flows.
            if (allowed && !concealChangedGroupActionOwner(scope, revision) && ownsGroupAction(scope, revision)) {
                if (available()) onAllowed()
                else { operationError = getString(R.string.xingdun_action_failed); render() }
            }
        }.also { it.start() }
        return true
    }

    private fun blacklistRow(checked: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 0, 10.dp(), 0)
        minimumHeight = 56.dp()
        isClickable = !isOperating
        isFocusable = true
        tag = R.string.xingdun_contact_detail_blacklist
        addView(TextView(context).apply {
            setText(R.string.xingdun_contact_detail_blacklist)
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(SwitchCompat(context).apply {
            isChecked = checked
            isClickable = false
            thumbTintList = ColorStateList.valueOf(if (checked) BRAND else Color.WHITE)
        })
        setOnClickListener { confirmBlacklist(!checked) }
    }

    private fun showRemarkEditor() {
        if (dispatchGroupSourceAction(available = ::isFriendRelationship) { showRemarkEditorAuthorized() }) return
        showRemarkEditorAuthorized()
    }

    private fun showRemarkEditorAuthorized() {
        remarkEditor.launch(XingDunContactRemarkActivity.intent(this, timUserID, detail?.alias.orEmpty()))
    }

    private fun confirmBlacklist(enabled: Boolean) {
        if (dispatchGroupSourceAction(available = ::isFriendRelationship) { confirmBlacklistAuthorized(enabled) }) return
        confirmBlacklistAuthorized(enabled)
    }

    private fun confirmBlacklistAuthorized(enabled: Boolean) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (enabled) R.string.xingdun_contact_detail_block_title else R.string.xingdun_contact_detail_unblock_title)
            .setMessage(if (enabled) R.string.xingdun_contact_detail_block_message else R.string.xingdun_contact_detail_unblock_message)
            .setNegativeButton(R.string.xingdun_cancel, null)
            .setPositiveButton(if (enabled) R.string.xingdun_contact_detail_block else R.string.xingdun_contact_detail_unblock) { _, _ ->
                setBlacklist(enabled)
            }
            .show()
        trackGroupSourceDialog(dialog)
    }

    private fun setBlacklist(enabled: Boolean) {
        if (dispatchGroupSourceAction(available = ::isFriendRelationship) { setBlacklistAuthorized(enabled) }) return
        setBlacklistAuthorized(enabled)
    }

    private fun setBlacklistAuthorized(enabled: Boolean) {
        if (isOperating) return
        isOperating = true
        operationError = null
        render()
        val success = {
            isOperating = false
            operationError = null
            detail = detail?.copy(isBlacklist = enabled)
            ContactStore.shared.loadBlackList()
            render()
            Toast.makeText(
                this,
                if (enabled) R.string.xingdun_contact_detail_blocked else R.string.xingdun_contact_detail_unblocked,
                Toast.LENGTH_SHORT
            ).show()
        }
        val failure = { _: Int, description: String ->
            isOperating = false
            operationError = description.ifBlank { getString(R.string.xingdun_action_failed) }
            render()
        }
        if (!dispatch(BusinessAction.SetFriendBlacklist(timUserID, enabled), success, failure)) {
            val handler = completion(success, failure)
            if (enabled) ContactStore.shared.addToBlacklist(timUserID, handler)
            else ContactStore.shared.removeFromBlacklist(timUserID, handler)
        }
    }

    private fun confirmDelete() {
        if (dispatchGroupSourceAction(available = ::isFriendRelationship) { confirmDeleteAuthorized() }) return
        confirmDeleteAuthorized()
    }

    private fun confirmDeleteAuthorized() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_contact_detail_delete_title)
            .setMessage(R.string.xingdun_contact_detail_delete_message)
            .setNegativeButton(R.string.xingdun_cancel, null)
            .setPositiveButton(R.string.xingdun_contact_detail_delete) { _, _ -> deleteContact() }
            .show()
        trackGroupSourceDialog(dialog)
    }

    private fun deleteContact() {
        if (dispatchGroupSourceAction(available = ::isFriendRelationship) { deleteContactAuthorized() }) return
        deleteContactAuthorized()
    }

    private fun deleteContactAuthorized() {
        if (isOperating) return
        isOperating = true
        operationError = null
        render()
        val success = {
            isOperating = false
            ContactStore.shared.loadFriends()
            Toast.makeText(this, R.string.xingdun_contact_detail_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
        val failure = { _: Int, description: String ->
            isOperating = false
            operationError = description.ifBlank { getString(R.string.xingdun_action_failed) }
            render()
        }
        if (!dispatch(BusinessAction.DeleteFriend(timUserID), success, failure)) {
            ContactStore.shared.deleteFriend(timUserID, completion(success, failure))
        }
    }

    private fun trackGroupSourceDialog(dialog: AlertDialog) {
        sourceDialog?.dismiss()
        sourceDialog = dialog
        dialog.setOnDismissListener { if (sourceDialog === dialog) sourceDialog = null }
    }

    private fun showMoreMenu(anchor: View) {
        if (dispatchGroupSourceAction(available = { detail != null }) { showMoreMenuAuthorized(anchor) }) return
        showMoreMenuAuthorized(anchor)
    }

    private fun showMoreMenuAuthorized(anchor: View) {
        sourceMoreMenu?.dismiss()
        val popup = PopupMenu(this, anchor).apply {
            if (isFriendRelationship()) {
                menu.add(0, MENU_RECOMMEND, 0, R.string.xingdun_contact_detail_recommend)
            }
            menu.add(0, MENU_REPORT, 1, R.string.xingdun_report)
            if (isFriendRelationship()) {
                menu.add(0, MENU_DELETE, 2, R.string.xingdun_contact_detail_delete)
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RECOMMEND -> showRecommendPicker()
                    MENU_REPORT -> startReport()
                    MENU_DELETE -> confirmDelete()
                }
                true
            }
            setOnDismissListener { if (sourceMoreMenu === it) sourceMoreMenu = null }
        }
        sourceMoreMenu = popup
        popup.show()
    }

    private fun startReport() {
        if (dispatchGroupSourceAction(available = { detail != null }) { startReportAuthorized() }) return
        startReportAuthorized()
    }

    private fun startReportAuthorized() {
        detail?.let { current ->
            XingDunFeatureActivity.startReport(this, "user", timUserID,
                current.alias.normalized() ?: current.nickname.normalized() ?: timUserID,
                current.customId.normalized() ?: current.timUserId.ifBlank { timUserID })
        }
    }

    private fun showRecommendPicker() {
        if (dispatchGroupSourceAction(available = ::isFriendRelationship) { showRecommendPickerAuthorized() }) return
        showRecommendPickerAuthorized()
    }

    private fun showRecommendPickerAuthorized() {
        if (sourceGroupID == null) independentPickerScope = profileScope()
        contactForwardPicker.launch(
            XingDunContactForwardPickerActivity.intent(this, "c2c_$timUserID")
        )
    }

    private fun sendContactCard(conversationIDs: List<String>) {
        if (sourceGroupID == null && !hasCurrentIndependentProfile()) return
        val current = detail ?: return
        if (conversationIDs.isEmpty()) return
        val payload = JsonObject().apply {
            addProperty("type", CONTACT_CARD_TYPE)
            add("data", JsonObject().apply {
                addProperty("accid", current.timUserId.ifBlank { timUserID })
                addProperty("name", current.alias.normalized() ?: current.nickname.normalized() ?: timUserID)
                current.avatar.normalized()?.let { addProperty("avatar", it) }
                current.customId.normalized()?.let { addProperty("customId", it) }
            })
        }.toString()
        val remaining = AtomicInteger(conversationIDs.size)
        val failures = AtomicInteger(0)
        conversationIDs.forEach { conversationID ->
            MessageInputStore.create(conversationID).sendMessage(
                SendMessagePayload.CustomSendMessagePayload(payload, CONTACT_CARD_TYPE, ""),
                SendMessageOption(),
                object : CompletionHandler {
                    override fun onSuccess() = finishOne()
                    override fun onFailure(code: Int, desc: String) {
                        failures.incrementAndGet()
                        finishOne()
                    }
                    private fun finishOne() {
                        if (remaining.decrementAndGet() == 0) runOnUiThread {
                            Toast.makeText(
                                this@XingDunContactDetailActivity,
                                if (failures.get() == 0) R.string.xingdun_contact_detail_recommended
                                else R.string.xingdun_contact_detail_recommend_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }

    private fun startCall(mediaType: CallMediaType) {
        if (dispatchGroupSourceAction(requiresPrivateChat = true, available = {
                val features = XingDunSessionManager.currentSession()?.features
                isFriendRelationship() && (if (mediaType == CallMediaType.Audio) features?.audioCall == true else features?.videoCall == true)
            }) { startCallAuthorized(mediaType) }) return
        startCallAuthorized(mediaType)
    }

    private fun startCallAuthorized(mediaType: CallMediaType) {
        TUICallKit.createInstance(this).calls(
            listOf(timUserID),
            mediaType,
            null,
            object : CompletionHandler {
                override fun onSuccess() = Unit
                override fun onFailure(code: Int, desc: String) {
                    runOnUiThread {
                        Toast.makeText(
                            this@XingDunContactDetailActivity,
                            desc.ifBlank { getString(R.string.xingdun_action_failed) },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun showLoadFailure(message: String) {
        content.removeAllViews()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30.dp(), 80.dp(), 30.dp(), 30.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_contact_detail_load_failed)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(TEXT_PRIMARY)
            })
            addView(TextView(context).apply {
                text = message.ifBlank { getString(R.string.xingdun_action_failed) }
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(TEXT_SECONDARY)
                setPadding(0, 10.dp(), 0, 20.dp())
            })
            addView(primaryButton(R.string.xingdun_retry) { loadDetail() }, LinearLayout.LayoutParams(150.dp(), 46.dp()))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        chatButton.visibility = View.GONE
        more.isEnabled = false
        more.alpha = 0f
    }

    private fun dispatch(
        action: BusinessAction,
        onSuccess: () -> Unit,
        onFailure: (Int, String) -> Unit
    ): Boolean = BusinessActionRegistry.dispatch(action, object : BusinessActionCompletion {
        override fun onSuccess(result: BusinessActionResult) = onSuccess()
        override fun onFailure(code: Int, description: String) = onFailure(code, description)
    })

    private fun completion(
        onSuccess: () -> Unit,
        onFailure: (Int, String) -> Unit
    ): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() = onSuccess()
        override fun onFailure(code: Int, desc: String) = onFailure(code, desc)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 16f)
    }

    private fun cardParams(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top.dp()
        }

    private fun sectionLabel(text: Int): TextView = TextView(this).apply {
        setText(text)
        textSize = 13f
        setTextColor(TEXT_SECONDARY)
        setPadding(4.dp(), 18.dp(), 0, 3.dp())
    }

    private fun valueRow(title: Int, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 0, 16.dp(), 0)
        minimumHeight = 54.dp()
        addView(TextView(context).apply {
            setText(title)
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(TEXT_SECONDARY)
            gravity = Gravity.END
            maxLines = 2
            setTextIsSelectable(true)
            tag = title
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f))
    }

    private fun disclosureRow(title: Int, value: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 12.dp(), 0)
            minimumHeight = 54.dp()
            isClickable = true
            isFocusable = true
            tag = title
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                setText(title)
                textSize = 16f
                setTextColor(TEXT_PRIMARY)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = value
                textSize = 15f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.END
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 25f
                setTextColor(0xFFB4B8BE.toInt())
                setPadding(8.dp(), 0, 0, 0)
            })
        }

    private fun actionRow(title: Int, icon: Int, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = 50.dp()
            isClickable = true
            isFocusable = true
            tag = title
            setOnClickListener { onClick() }
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(BRAND)
                contentDescription = getString(title)
            }, LinearLayout.LayoutParams(20.dp(), 20.dp()))
            addView(TextView(context).apply {
                setText(title)
                textSize = 16f
                setTextColor(BRAND)
                setPadding(8.dp(), 0, 0, 0)
            })
        }

    private fun primaryButton(title: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        setText(title)
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = rounded(BRAND, 14f)
        isClickable = true
        isFocusable = true
        tag = title
        setOnClickListener { onClick() }
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(0xFFE9EBEF.toInt()) }.also {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 16.dp() }
    }

    private fun rounded(fill: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.dp().toFloat()
    }

    private fun fallbackDetail(): XingDunContactDetail = XingDunContactDetail(
        customId = fallbackCustomID,
        nickname = intent.getStringExtra(EXTRA_NICKNAME).normalized() ?: getString(R.string.xingdun_contact_private_name),
        avatar = intent.getStringExtra(EXTRA_AVATAR).normalized(),
        signature = intent.getStringExtra(EXTRA_SIGNATURE).normalized(),
        timUserId = timUserID,
        alias = intent.getStringExtra(EXTRA_REMARK).normalized(),
        isBlacklist = intent.getBooleanExtra(EXTRA_BLACKLIST, false)
    )

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
    private fun String?.displayValue(): String = normalized() ?: getString(R.string.xingdun_not_set)
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)
    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.let { value -> runCatching { value.asInt }.getOrNull() }
    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.let { value -> runCatching { value.asBoolean }.getOrNull() } ?: false

    companion object {
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_CUSTOM_ID = "custom_id"
        private const val EXTRA_NICKNAME = "nickname"
        private const val EXTRA_AVATAR = "avatar"
        private const val EXTRA_SIGNATURE = "signature"
        private const val EXTRA_REMARK = "remark"
        private const val EXTRA_BLACKLIST = "blacklist"
        private const val EXTRA_RELATIONSHIP = "relationship"
        private const val MENU_RECOMMEND = 1
        private const val MENU_REPORT = 2
        private const val MENU_DELETE = 3
        private const val CONTACT_CARD_TYPE = "contact_card"
        private const val RELATIONSHIP_UNKNOWN = "unknown"
        private const val RELATIONSHIP_NONE = "none"
        private const val RELATIONSHIP_FRIEND = "friend"
        private const val RELATIONSHIP_BLOCKED = "blocked"
        private const val RELATIONSHIP_SELF = "self"

        private val PAGE_BACKGROUND = 0xFFF5F6FA.toInt()
        private val BRAND = 0xFF23B39C.toInt()
        private val TEXT_PRIMARY = 0xFF15191D.toInt()
        private val TEXT_SECONDARY = 0xFF7A8088.toInt()

        fun start(context: Context, contact: ContactInfo) {
            context.startActivity(Intent(context, XingDunContactDetailActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, contact.userID)
                putExtra(EXTRA_NICKNAME, contact.nickname)
                putExtra(EXTRA_AVATAR, contact.avatarURL)
                putExtra(EXTRA_SIGNATURE, contact.aboutMe)
                putExtra(EXTRA_REMARK, contact.friendRemark)
                putExtra(EXTRA_BLACKLIST, contact.isInBlacklist)
                putExtra(EXTRA_RELATIONSHIP, if (contact.isInBlacklist) RELATIONSHIP_BLOCKED else RELATIONSHIP_FRIEND)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        fun start(context: Context, userID: String, nickname: String?, avatar: String?) {
            start(context, userID, null, nickname, avatar)
        }

        fun startFromGroup(context: Context, groupID: String, userID: String, nickname: String?, avatar: String?) {
            context.startActivity(Intent(context, XingDunContactDetailActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userID)
                putExtra(EXTRA_NICKNAME, nickname)
                putExtra(EXTRA_AVATAR, avatar)
                putExtra("source_group_id", groupID)
            })
        }

        fun start(context: Context, userID: String, customID: String?, nickname: String?, avatar: String?) {
            context.startActivity(Intent(context, XingDunContactDetailActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userID)
                putExtra(EXTRA_CUSTOM_ID, customID)
                putExtra(EXTRA_NICKNAME, nickname)
                putExtra(EXTRA_AVATAR, avatar)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
