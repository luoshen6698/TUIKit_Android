package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.forward.ForwardTargetSelectorDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Android counterpart of iOS `XingDunContactDetailView`. */
class XingDunContactDetailActivity : BaseActivity() {
    private lateinit var content: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var more: TextView
    private lateinit var chatButton: TextView
    private lateinit var detailRefresh: SwipeRefreshLayout

    private val timUserID: String by lazy { intent.getStringExtra(EXTRA_USER_ID).orEmpty().trim() }
    private var detail: XingDunContactDetail? = null
    private var isOperating = false
    private var relationshipState: String = RELATIONSHIP_UNKNOWN
    private val remarkEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val remark = result.data?.getStringExtra(XingDunContactRemarkActivity.EXTRA_RESULT_REMARK).orEmpty()
        detail = detail?.copy(alias = remark.normalized())
        ContactStore.shared.loadFriends()
        render()
        Toast.makeText(this, R.string.xingdun_contact_detail_remark_updated, Toast.LENGTH_SHORT).show()
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
        render()
        loadDetail()
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
            setText(R.string.xingdun_contact_detail_title)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            marginStart = 58.dp()
            marginEnd = 58.dp()
        })
        more = TextView(context).apply {
            text = "•••"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            background = rounded(0xFFF0F2F5.toInt(), 21f)
            contentDescription = getString(R.string.xingdun_contact_detail_more)
            setOnClickListener { showMoreMenu(this) }
        }
        addView(more, FrameLayout.LayoutParams(42.dp(), 42.dp(), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = 5.dp()
        })
    }

    private fun loadDetail() {
        loading.visibility = View.VISIBLE
        val session = XingDunSessionManager.currentSession()
        if (session == null) {
            loading.visibility = View.GONE
            if (::detailRefresh.isInitialized) detailRefresh.isRefreshing = false
            showLoadFailure(getString(R.string.xingdun_session_expired))
            return
        }
        lifecycleScope.launch {
            val detailResult = runCatching {
                XingDunSessionManager.apiClient().get<XingDunContactDetail>(
                    session,
                    "user/detail",
                    mapOf("tim_user_id" to timUserID),
                    XingDunContactDetail::class.java
                )
            }
            detailResult.onSuccess { response ->
                relationshipState = if (response.isBlacklist) RELATIONSHIP_BLOCKED else RELATIONSHIP_FRIEND
                detail = response.copy(
                    timUserId = response.timUserId.trim().ifEmpty { timUserID },
                    nickname = response.nickname.normalized() ?: detail?.nickname,
                    avatar = response.avatar.normalized() ?: detail?.avatar
                )
            }
            if (detailResult.isFailure && relationshipState == RELATIONSHIP_UNKNOWN) {
                runCatching {
                    XingDunSessionManager.apiClient().getNullable<JsonObject>(
                        session,
                        "user/searchForFriend",
                        mapOf("keyword" to timUserID),
                        JsonObject::class.java,
                    )
                }.onSuccess { profile ->
                    if (profile != null) applyRelationshipProfile(profile)
                }.onFailure { error ->
                    if (detail == null) showLoadFailure(error.localizedMessage.orEmpty())
                }
            } else if (detailResult.isFailure && detail == null) {
                showLoadFailure(detailResult.exceptionOrNull()?.localizedMessage.orEmpty())
            }
            render()
            loading.visibility = View.GONE
            detailRefresh.isRefreshing = false
        }
    }

    private fun applyRelationshipProfile(profile: JsonObject) {
        relationshipState = if (profile.boolean("is_self")) {
            RELATIONSHIP_SELF
        } else {
            profile.string("relationship_status") ?: RELATIONSHIP_NONE
        }
        val current = detail ?: fallbackDetail()
        detail = current.copy(
            id = profile.int("id") ?: current.id,
            customId = profile.string("custom_id") ?: current.customId,
            nickname = profile.string("nickname") ?: current.nickname,
            avatar = profile.string("avatar") ?: current.avatar,
            signature = profile.string("signature") ?: current.signature,
            timUserId = profile.string("tim_user_id") ?: current.timUserId.ifBlank { timUserID },
            isBlacklist = relationshipState == RELATIONSHIP_BLOCKED,
        )
    }

    private fun render() {
        val current = detail ?: return
        content.removeAllViews()
        content.addView(profileCard(current), cardParams())

        val friendRelationship = isFriendRelationship()
        val features = XingDunSessionManager.currentSession()?.features
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
            if (friendRelationship) {
                addView(disclosureRow(
                    R.string.xingdun_contact_detail_remark,
                    current.alias.displayValue()
                ) { showRemarkEditor() })
                addView(divider())
            }
            addView(valueRow(R.string.xingdun_contact_detail_birthday, current.birthday.displayValue()))
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_phone, current.phone.displayValue()))
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_email, current.email.displayValue()))
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_signature, current.signature.displayValue()))
        }, cardParams(top = 6))

        if (friendRelationship) {
            content.addView(card().apply {
                addView(blacklistRow(current.isBlacklist))
            }, cardParams(top = 16))
        }
        configureBottomAction()
        more.isEnabled = true
        more.alpha = 1f
    }

    private fun profileCard(current: XingDunContactDetail): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        val name = current.nickname.normalized() ?: timUserID
        addView(Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
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
            addView(TextView(context).apply {
                text = getString(
                    R.string.xingdun_contact_detail_account_id,
                    current.customId.normalized() ?: current.timUserId.ifBlank { timUserID }
                )
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, 6.dp(), 0, 0)
                setTextIsSelectable(true)
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
            }
            canAddFriend() -> {
                chatButton.visibility = View.VISIBLE
                chatButton.setText(R.string.xingdun_add_friend)
            }
            else -> chatButton.visibility = View.GONE
        }
    }

    private fun openBottomAction() {
        if (isFriendRelationship()) {
            ChatActivity.start(this, "c2c_$timUserID")
        } else if (canAddFriend()) {
            val keyword = detail?.customId.normalized() ?: timUserID
            XingDunFeatureActivity.start(this, XingDunFeatureActivity.MODE_FRIEND_SEARCH, keyword)
        }
    }

    private fun blacklistRow(checked: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 0, 10.dp(), 0)
        minimumHeight = 56.dp()
        isClickable = !isOperating
        isFocusable = true
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
        remarkEditor.launch(XingDunContactRemarkActivity.intent(this, timUserID, detail?.alias.orEmpty()))
    }

    private fun confirmBlacklist(enabled: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (enabled) R.string.xingdun_contact_detail_block_title else R.string.xingdun_contact_detail_unblock_title)
            .setMessage(if (enabled) R.string.xingdun_contact_detail_block_message else R.string.xingdun_contact_detail_unblock_message)
            .setNegativeButton(R.string.xingdun_cancel, null)
            .setPositiveButton(if (enabled) R.string.xingdun_contact_detail_block else R.string.xingdun_contact_detail_unblock) { _, _ ->
                setBlacklist(enabled)
            }
            .show()
    }

    private fun setBlacklist(enabled: Boolean) {
        if (isOperating) return
        isOperating = true
        val success = {
            isOperating = false
            detail = detail?.copy(isBlacklist = enabled)
            ContactStore.shared.loadBlackList()
            render()
            Toast.makeText(
                this,
                if (enabled) R.string.xingdun_contact_detail_blocked else R.string.xingdun_contact_detail_unblocked,
                Toast.LENGTH_SHORT
            ).show()
        }
        val failure = { _: Int, _: String -> isOperating = false; render() }
        if (!dispatch(BusinessAction.SetFriendBlacklist(timUserID, enabled), success, failure)) {
            val handler = completion(success, failure)
            if (enabled) ContactStore.shared.addToBlacklist(timUserID, handler)
            else ContactStore.shared.removeFromBlacklist(timUserID, handler)
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_contact_detail_delete_title)
            .setMessage(R.string.xingdun_contact_detail_delete_message)
            .setNegativeButton(R.string.xingdun_cancel, null)
            .setPositiveButton(R.string.xingdun_contact_detail_delete) { _, _ -> deleteContact() }
            .show()
    }

    private fun deleteContact() {
        if (isOperating) return
        isOperating = true
        val success = {
            isOperating = false
            ContactStore.shared.loadFriends()
            Toast.makeText(this, R.string.xingdun_contact_detail_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
        val failure = { _: Int, _: String -> isOperating = false }
        if (!dispatch(BusinessAction.DeleteFriend(timUserID), success, failure)) {
            ContactStore.shared.deleteFriend(timUserID, completion(success, failure))
        }
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
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
                    MENU_REPORT -> detail?.let { current ->
                        XingDunFeatureActivity.startReport(
                            this@XingDunContactDetailActivity,
                            "user",
                            timUserID,
                            current.alias.normalized() ?: current.nickname.normalized() ?: timUserID,
                            current.customId.normalized() ?: current.timUserId.ifBlank { timUserID },
                        )
                    }
                    MENU_DELETE -> confirmDelete()
                }
                true
            }
            show()
        }
    }

    private fun showRecommendPicker() {
        ForwardTargetSelectorDialog(this) { conversationIDs ->
            sendContactCard(conversationIDs)
        }.show()
    }

    private fun sendContactCard(conversationIDs: List<String>) {
        val current = detail ?: return
        if (conversationIDs.isEmpty()) return
        val payload = JsonObject().apply {
            addProperty("type", CONTACT_CARD_TYPE)
            addProperty("version", 1)
            addProperty("user_id", current.timUserId.ifBlank { timUserID })
            current.customId.normalized()?.let { addProperty("custom_id", it) }
            addProperty("display_name", current.alias.normalized() ?: current.nickname.normalized() ?: timUserID)
            current.avatar.normalized()?.let { addProperty("avatar_url", it) }
            current.departmentPath.takeIf(List<String>::isNotEmpty)?.joinToString(" / ")?.let { addProperty("department", it) }
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
        nickname = intent.getStringExtra(EXTRA_NICKNAME).normalized() ?: timUserID,
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
        private const val EXTRA_NICKNAME = "nickname"
        private const val EXTRA_AVATAR = "avatar"
        private const val EXTRA_SIGNATURE = "signature"
        private const val EXTRA_REMARK = "remark"
        private const val EXTRA_BLACKLIST = "blacklist"
        private const val EXTRA_RELATIONSHIP = "relationship"
        private const val MENU_RECOMMEND = 1
        private const val MENU_REPORT = 2
        private const val MENU_DELETE = 3
        private const val CONTACT_CARD_TYPE = "xingdun_contact_card"
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
            context.startActivity(Intent(context, XingDunContactDetailActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userID)
                putExtra(EXTRA_NICKNAME, nickname)
                putExtra(EXTRA_AVATAR, avatar)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
