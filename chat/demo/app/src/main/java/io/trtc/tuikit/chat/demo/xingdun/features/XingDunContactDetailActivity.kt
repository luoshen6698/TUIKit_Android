package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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

    private val timUserID: String by lazy { intent.getStringExtra(EXTRA_USER_ID).orEmpty().trim() }
    private var detail: XingDunContactDetail? = null
    private var isOperating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (timUserID.isEmpty()) {
            finish()
            return
        }
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
        root.addView(ScrollView(this).apply {
            clipToPadding = false
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        chatButton = primaryButton(R.string.xingdun_contact_detail_start_chat) {
            ChatActivity.start(this, "c2c_$timUserID")
        }
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
            contentDescription = getString(R.string.xingdun_contact_detail_more)
            setOnClickListener { showMoreMenu(this) }
        }
        addView(more, FrameLayout.LayoutParams(52.dp(), 52.dp(), Gravity.END))
    }

    private fun loadDetail() {
        loading.visibility = View.VISIBLE
        val session = XingDunSessionManager.currentSession()
        if (session == null) {
            loading.visibility = View.GONE
            showLoadFailure(getString(R.string.xingdun_session_expired))
            return
        }
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<XingDunContactDetail>(
                    session,
                    "user/detail",
                    mapOf("tim_user_id" to timUserID),
                    XingDunContactDetail::class.java
                )
            }.onSuccess { response ->
                detail = response.copy(
                    timUserId = response.timUserId.trim().ifEmpty { timUserID },
                    nickname = response.nickname.normalized() ?: detail?.nickname,
                    avatar = response.avatar.normalized() ?: detail?.avatar
                )
                render()
            }.onFailure { error ->
                if (detail == null) showLoadFailure(error.localizedMessage.orEmpty())
            }
            loading.visibility = View.GONE
        }
    }

    private fun render() {
        val current = detail ?: return
        content.removeAllViews()
        content.addView(profileCard(current), cardParams())

        val features = XingDunSessionManager.currentSession()?.features
        if (features?.audioCall == true || features?.videoCall == true) {
            content.addView(sectionLabel(R.string.xingdun_contact_detail_call_section))
            val calls = card()
            if (features.audioCall) {
                calls.addView(actionRow(R.string.xingdun_audio_call, "☎") { startCall(CallMediaType.Audio) })
            }
            if (features.audioCall && features.videoCall) calls.addView(divider())
            if (features.videoCall) {
                calls.addView(actionRow(R.string.xingdun_video_call, "▣") { startCall(CallMediaType.Video) })
            }
            content.addView(calls, cardParams(top = 6))
        }

        content.addView(sectionLabel(R.string.xingdun_contact_detail_user_information))
        content.addView(card().apply {
            addView(disclosureRow(
                R.string.xingdun_contact_detail_remark,
                current.alias.displayValue()
            ) { showRemarkEditor() })
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_birthday, current.birthday.displayValue()))
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_phone, current.phone.displayValue()))
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_email, current.email.displayValue()))
            addView(divider())
            addView(valueRow(R.string.xingdun_contact_detail_signature, current.signature.displayValue()))
        }, cardParams(top = 6))

        content.addView(card().apply {
            addView(blacklistRow(current.isBlacklist))
        }, cardParams(top = 16))
        chatButton.visibility = View.VISIBLE
        more.isEnabled = true
        more.alpha = 1f
    }

    private fun profileCard(current: XingDunContactDetail): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        val name = current.nickname.normalized() ?: timUserID
        addView(Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
            setContent(Avatar.AvatarContent.Image(current.avatar.orEmpty(), name))
        }, LinearLayout.LayoutParams(64.dp(), 64.dp()))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 0, 0, 0)
            addView(TextView(context).apply {
                text = name
                textSize = 20f
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
        val input = EditText(this).apply {
            setText(detail?.alias.orEmpty())
            hint = getString(R.string.xingdun_contact_detail_remark_hint)
            filters = arrayOf(InputFilter.LengthFilter(96))
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_contact_detail_set_remark)
            .setView(FrameLayout(this).apply {
                setPadding(22.dp(), 0, 22.dp(), 0)
                addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            })
            .setNegativeButton(R.string.xingdun_cancel, null)
            .setPositiveButton(R.string.xingdun_contact_detail_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val remark = input.text.toString().trim()
                if (remark.toByteArray().size > 96) {
                    input.error = getString(R.string.xingdun_contact_detail_remark_too_long)
                } else {
                    updateRemark(remark) { dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    private fun updateRemark(remark: String, onSuccess: () -> Unit) {
        if (isOperating) return
        isOperating = true
        val success = {
            isOperating = false
            detail = detail?.copy(alias = remark.normalized())
            ContactStore.shared.loadFriends()
            render()
            Toast.makeText(this, R.string.xingdun_contact_detail_remark_updated, Toast.LENGTH_SHORT).show()
            onSuccess()
        }
        val failure = { _: Int, _: String -> isOperating = false }
        if (!dispatch(BusinessAction.SetFriendRemark(timUserID, remark), success, failure)) {
            ContactStore.shared.setFriendRemark(timUserID, remark, completion(success, failure))
        }
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
            menu.add(0, MENU_RECOMMEND, 0, R.string.xingdun_contact_detail_recommend)
            menu.add(0, MENU_REPORT, 1, R.string.xingdun_report)
            menu.add(0, MENU_DELETE, 2, R.string.xingdun_contact_detail_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RECOMMEND -> showRecommendPicker()
                    MENU_REPORT -> XingDunFeatureActivity.startReport(this@XingDunContactDetailActivity, "user", timUserID)
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

    private fun actionRow(title: Int, icon: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = 50.dp()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = icon
                textSize = 18f
                setTextColor(BRAND)
            })
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

    companion object {
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_NICKNAME = "nickname"
        private const val EXTRA_AVATAR = "avatar"
        private const val EXTRA_SIGNATURE = "signature"
        private const val EXTRA_REMARK = "remark"
        private const val EXTRA_BLACKLIST = "blacklist"
        private const val MENU_RECOMMEND = 1
        private const val MENU_REPORT = 2
        private const val MENU_DELETE = 3
        private const val CONTACT_CARD_TYPE = "xingdun_contact_card"

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
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
