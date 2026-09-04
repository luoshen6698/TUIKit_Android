package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.coroutines.resume

/** Android view implementation kept deliberately close to the iOS red-packet screens. */
internal class XingDunRedpacketUiController(
    private val activity: XingDunFeatureActivity,
    private val content: LinearLayout,
    private val statusView: TextView,
    private val scrollView: ScrollView,
    private val titleView: TextView,
    private val targetID: String,
    private val fixtureEnabled: Boolean,
) {
    private data class MemberChoice(val userID: String, val displayName: String)

    private val session: XingDunStoredSession?
        get() = XingDunSessionManager.currentSession()

    fun showSend() {
        if (!ensureEnabled()) return
        val isDirect = targetID.startsWith("c2c_")
        val isGroup = targetID.startsWith("group_")
        if (!isDirect && !isGroup) {
            info(R.string.xingdun_redpacket_invalid_conversation)
            return
        }
        titleView.setText(if (isGroup) R.string.xingdun_redpacket_send_group_title else R.string.xingdun_redpacket_send_title)
        preparePage()

        val validationHint = TextView(activity).apply {
            visibility = View.GONE
            textSize = 14f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(DANGER)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            background = rounded(0xFFFFECEC.toInt(), 10f)
        }
        content.addView(validationHint, matchWrap(bottom = 10))

        var members: List<MemberChoice> = emptyList()
        var selectedReceiver: MemberChoice? = null
        var selectedType = if (isGroup) XingDunRedpacketType.TEAM_RANDOM else XingDunRedpacketType.SINGLE
        var countValue = 1

        if (isGroup) sectionLabel(R.string.xingdun_redpacket_type_label)
        val typeButtons = mutableListOf<Button>()
        if (isGroup) segmentedContainer().also { row ->
            val options = listOf(
                XingDunRedpacketType.TEAM_RANDOM to R.string.xingdun_redpacket_type_random,
                XingDunRedpacketType.TEAM_FIXED to R.string.xingdun_redpacket_type_fixed,
                XingDunRedpacketType.TEAM_EXCLUSIVE to R.string.xingdun_redpacket_type_exclusive,
            )
            options.forEach { (type, label) ->
                row.addView(segmentButton(label) {
                    selectedType = type
                    refreshSegments(typeButtons, options.indexOfFirst { it.first == type })
                    renderConditionalRows()
                }.also(typeButtons::add), weightParams())
            }
            content.addView(row, matchWrap(top = 6))
        }

        sectionLabel(if (isGroup) R.string.xingdun_redpacket_settings else R.string.xingdun_redpacket_amount_section)
        val form = verticalCard()
        val amountInput = EditText(activity).apply {
            hint = "0.00"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 17f
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_TERTIARY)
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(8.dp(), 0, 4.dp(), 0)
        }
        amountInput.doAfterTextChanged { validationHint.visibility = View.GONE }
        form.addView(labeledInputRow(
            if (isGroup) R.string.xingdun_redpacket_total_amount else R.string.xingdun_redpacket_amount_label,
            amountInput,
            R.string.xingdun_currency_yuan,
        ))
        val conditionalRows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        form.addView(conditionalRows)
        form.addView(divider())
        val greeting = EditText(activity).apply {
            setText(R.string.xingdun_redpacket_default_greeting)
            hint = activity.getString(R.string.xingdun_redpacket_default_greeting)
            textSize = 16f
            maxLines = 2
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_TERTIARY)
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
        }
        form.addView(greeting, matchWrap(horizontal = 16, vertical = 14))
        content.addView(form, matchWrap(top = 6))

        fun validatedDraft(availableBalance: Int?): XingDunValidatedRedpacketDraft =
            XingDunRedpacketSendPolicy.validate(
                amountText = amountInput.text.toString(),
                requestedType = selectedType,
                requestedCount = countValue,
                greeting = greeting.text.toString(),
                isGroup = isGroup,
                availableBalance = availableBalance,
                groupMemberCount = members.size,
                exclusiveReceiverTimUserId = selectedReceiver?.userID,
                defaultGreeting = activity.getString(R.string.xingdun_redpacket_default_greeting),
            )

        fun showValidationError(error: XingDunRedpacketValidationError, showToast: Boolean = true) {
            val message = validationMessage(error)
            validationHint.setText(message)
            validationHint.visibility = View.VISIBLE
            scrollView.smoothScrollTo(0, 0)
            if (showToast) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }

        val sendButton = destructiveButton(R.string.xingdun_redpacket_send_action) {
            val activeSession = session
            if (activeSession?.features?.redpacket != true) {
                Toast.makeText(activity, R.string.xingdun_redpacket_closed_detail, Toast.LENGTH_LONG).show()
                return@destructiveButton
            }
            val draft = try {
                validatedDraft(availableBalance = null)
            } catch (error: XingDunRedpacketValidationException) {
                showValidationError(error.reason)
                return@destructiveButton
            }
            sendButtonState(false)
            setBusy(true)
            activity.lifecycleScope.launch {
                runCatching {
                    val finalDraft = if (fixtureEnabled) {
                        draft
                    } else {
                        val latestBalance = XingDunSessionManager.apiClient().get<XingDunRedpacketBalancePayload>(
                            activeSession,
                            "redpacket/myBalance",
                            emptyMap(),
                            XingDunRedpacketBalancePayload::class.java,
                        ).redpacketBalance
                        validatedDraft(latestBalance)
                    }
                    sendRedpacket(activeSession, isGroup, finalDraft)
                }
                    .onSuccess {
                        setBusy(false)
                        Toast.makeText(activity, R.string.xingdun_redpacket_send_succeeded, Toast.LENGTH_SHORT).show()
                        activity.finish()
                    }
                    .onFailure { error ->
                        sendButtonState(true)
                        if (error is XingDunRedpacketValidationException) {
                            setBusy(false)
                            showValidationError(error.reason)
                        } else {
                            showFailure(error)
                        }
                    }
            }
        }.apply { isEnabled = false; alpha = DISABLED_ALPHA }
        content.addView(sendButton, matchWrap(top = 26, horizontal = 0))

        fun updateCountLabel(label: TextView) {
            label.text = activity.resources.getQuantityString(
                R.plurals.xingdun_redpacket_count_value,
                countValue,
                countValue,
            )
        }

        fun showMemberPicker(button: TextView) {
            if (members.isEmpty()) {
                Toast.makeText(activity, R.string.xingdun_redpacket_no_receivers, Toast.LENGTH_SHORT).show()
                return
            }
            val labels = members.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(R.string.xingdun_redpacket_select_receiver_title)
                .setSingleChoiceItems(labels, members.indexOf(selectedReceiver)) { dialog, index ->
                    selectedReceiver = members[index]
                    button.text = labels[index]
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.xingdun_cancel, null)
                .show()
        }

        fun rebuildConditionalRows() {
            conditionalRows.removeAllViews()
            if (!isGroup) return
            if (selectedType == XingDunRedpacketType.TEAM_EXCLUSIVE) {
                val receiverValue = TextView(activity).apply {
                    text = selectedReceiver?.displayName ?: activity.getString(R.string.xingdun_redpacket_choose)
                    textSize = 15f
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    setTextColor(TEXT_SECONDARY)
                }
                conditionalRows.addView(divider())
                conditionalRows.addView(clickableValueRow(
                    R.string.xingdun_redpacket_exclusive_receiver,
                    receiverValue,
                ) { showMemberPicker(receiverValue) })
            } else {
                val countText = TextView(activity).apply {
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(TEXT_PRIMARY)
                    minWidth = 78.dp()
                    updateCountLabel(this)
                }
                val minus = compactButton("−") {
                    if (countValue > 1) {
                        countValue -= 1
                        updateCountLabel(countText)
                    }
                }
                val plus = compactButton("+") {
                    val maximum = members.size.coerceAtLeast(1)
                    if (countValue < maximum) {
                        countValue += 1
                        updateCountLabel(countText)
                    }
                }
                conditionalRows.addView(divider())
                conditionalRows.addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16.dp(), 8.dp(), 12.dp(), 8.dp())
                    addView(TextView(activity).apply {
                        setText(R.string.xingdun_redpacket_count_label)
                        textSize = 16f
                        setTextColor(TEXT_PRIMARY)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(minus, LinearLayout.LayoutParams(40.dp(), 40.dp()))
                    addView(countText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 40.dp()))
                    addView(plus, LinearLayout.LayoutParams(40.dp(), 40.dp()))
                })
            }
        }
        renderConditionalRows = ::rebuildConditionalRows
        rebuildConditionalRows()
        if (isGroup) refreshSegments(typeButtons, 0)

        fun setSendEnabled(enabled: Boolean) {
            sendButton.isEnabled = enabled
            sendButton.alpha = if (enabled) 1f else DISABLED_ALPHA
        }
        sendButtonState = ::setSendEnabled

        setBusy(true)
        activity.lifecycleScope.launch {
            val activeSession = session ?: return@launch showFailure(IllegalStateException(activity.getString(R.string.xingdun_session_expired)))
            runCatching {
                val loadedBalance = if (fixtureEnabled) 5_500 else XingDunSessionManager.apiClient().get<XingDunRedpacketBalancePayload>(
                    activeSession,
                    "redpacket/myBalance",
                    emptyMap(),
                    XingDunRedpacketBalancePayload::class.java,
                ).redpacketBalance
                val loadedMembers = when {
                    !isGroup -> emptyList()
                    fixtureEnabled -> listOf(
                        MemberChoice("xd_fixture_01", "Alice"),
                        MemberChoice("xd_fixture_02", "Bob"),
                        MemberChoice("xd_fixture_03", "Carol"),
                    )
                    else -> loadMembers(targetID.removePrefix("group_"))
                }
                loadedBalance to loadedMembers
            }.onSuccess { (loadedBalance, loadedMembers) ->
                members = loadedMembers
                countValue = countValue.coerceAtMost(loadedMembers.size.coerceAtLeast(1))
                if (loadedBalance <= 0) {
                    showValidationError(XingDunRedpacketValidationError.INSUFFICIENT_BALANCE, showToast = false)
                }
                rebuildConditionalRows()
                setSendEnabled(true)
                setBusy(false)
            }.onFailure(::showFailure)
        }
    }

    fun showAccount() {
        if (!ensureEnabled()) return
        preparePage()
        val balanceAmount = TextView(activity).apply {
            text = "—"
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val balanceCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 20.dp(), 24.dp(), 20.dp())
            background = gradient(intArrayOf(0xFFF2A041.toInt(), 0xFFF9C471.toInt()), 14f)
            addView(TextView(activity).apply {
                setText(R.string.xingdun_redpacket_balance_title)
                textSize = 17f
                setTextColor(0xE6FFFFFF.toInt())
            })
            addView(balanceAmount, matchWrap(top = 7))
            addView(TextView(activity).apply {
                setText(R.string.xingdun_redpacket_balance_hint)
                textSize = 13f
                setTextColor(0xD6FFFFFF.toInt())
            }, matchWrap(top = 7))
        }
        content.addView(balanceCard, matchWrap())

        val tabs = segmentedContainer()
        val tabButtons = listOf(
            segmentButton(R.string.xingdun_redpacket_tab_sent) { selectAccountSection(0) },
            segmentButton(R.string.xingdun_redpacket_tab_received) { selectAccountSection(1) },
            segmentButton(R.string.xingdun_redpacket_tab_balance_logs) { selectAccountSection(2) },
        )
        tabButtons.forEach { tabs.addView(it, weightParams()) }
        content.addView(tabs, matchWrap(top = 12))
        val listContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer, matchWrap(top = 10))

        var selected = 0
        var page = 1
        var total = 0
        val items = mutableListOf<Any>()
        var loading = false

        fun renderList() {
            listContainer.removeAllViews()
            if (items.isEmpty() && !loading) {
                listContainer.addView(emptyCard(when (selected) {
                    0 -> R.string.xingdun_redpacket_empty_sent
                    1 -> R.string.xingdun_redpacket_empty_received
                    else -> R.string.xingdun_redpacket_empty_balance_logs
                }))
            }
            items.forEach { item ->
                when (item) {
                    is XingDunRedpacketListItem -> listContainer.addView(sentRecord(item))
                    is XingDunReceivedRedpacketItem -> listContainer.addView(receivedRecord(item))
                    is XingDunRedpacketBalanceLog -> listContainer.addView(balanceLogRecord(item))
                }
            }
            if (items.size < total && !loading) {
                listContainer.addView(secondaryButton(R.string.xingdun_load_more) { loadAccountPage(false) }, matchWrap(top = 4))
            }
        }

        fun loadPage(reset: Boolean) {
            if (loading) return
            if (reset) {
                page = 1
                total = 0
                items.clear()
            }
            loading = true
            renderList()
            setBusy(true)
            activity.lifecycleScope.launch {
                val activeSession = session ?: return@launch showFailure(IllegalStateException(activity.getString(R.string.xingdun_session_expired)))
                runCatching {
                    if (fixtureEnabled) fixtureAccountPage(selected, page)
                    else fetchAccountPage(activeSession, selected, page)
                }.onSuccess { result ->
                    result.balance?.let { balanceAmount.text = money(it) }
                    if (reset) items.clear()
                    items.addAll(result.items.filterNot { candidate -> recordKey(candidate) in items.map(::recordKey) })
                    total = result.total
                    if (items.size < total) page += 1
                    loading = false
                    setBusy(false)
                    renderList()
                }.onFailure {
                    loading = false
                    showFailure(it)
                    renderList()
                }
            }
        }
        loadAccountPage = ::loadPage

        fun selectSection(index: Int) {
            if (index == selected && items.isNotEmpty()) return
            selected = index
            refreshSegments(tabButtons, selected)
            loadPage(true)
        }
        selectAccountSection = ::selectSection

        refreshSegments(tabButtons, selected)
        var startY = 0f
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startY = event.y
                MotionEvent.ACTION_UP -> if (scrollView.scrollY == 0 && event.y - startY > 110.dp()) loadPage(true)
            }
            false
        }
        loadPage(true)
    }

    fun showDetail() {
        if (!ensureEnabled()) return
        if (targetID.isBlank()) {
            info(R.string.xingdun_redpacket_invalid)
            return
        }
        preparePage()
        var startY = 0f
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startY = event.y
                MotionEvent.ACTION_UP -> if (scrollView.scrollY == 0 && event.y - startY > 110.dp()) showDetail()
            }
            false
        }
        setBusy(true)
        activity.lifecycleScope.launch {
            val activeSession = session ?: return@launch showFailure(IllegalStateException(activity.getString(R.string.xingdun_session_expired)))
            runCatching { if (fixtureEnabled) fixtureDetailPage(targetID) else fetchDetailPage(activeSession, targetID, 1) }
                .onSuccess { page ->
                    setBusy(false)
                    renderDetail(activeSession, page)
                    val detail = page.packet
                    if (XingDunRedpacketPresentationPolicy.shouldPresentEnvelope(detail.status, detail.hasClaimed)) {
                        showEnvelope(activeSession, page)
                    }
                }
                .onFailure(::showFailure)
        }
    }

    private data class AccountPageResult(val balance: Int?, val items: List<Any>, val total: Int)

    private var renderConditionalRows: () -> Unit = {}
    private var sendButtonState: (Boolean) -> Unit = {}
    private var selectAccountSection: (Int) -> Unit = {}
    private var loadAccountPage: (Boolean) -> Unit = {}

    private suspend fun fetchAccountPage(session: XingDunStoredSession, section: Int, page: Int): AccountPageResult {
        val api = XingDunSessionManager.apiClient()
        val balance = api.get<XingDunRedpacketBalancePayload>(
            session, "redpacket/myBalance", emptyMap(), XingDunRedpacketBalancePayload::class.java,
        ).redpacketBalance
        val query = mapOf("page" to page.toString(), "page_size" to PAGE_SIZE.toString())
        return when (section) {
            0 -> api.get<XingDunRedpacketPage>(session, "redpacket/mySent", query, XingDunRedpacketPage::class.java)
                .let { AccountPageResult(balance, it.list, it.total) }
            1 -> api.get<XingDunReceivedRedpacketPage>(session, "redpacket/myReceived", query, XingDunReceivedRedpacketPage::class.java)
                .let { AccountPageResult(balance, it.list, it.total) }
            else -> api.get<XingDunRedpacketBalanceLogPage>(session, "redpacket/myBalanceLogs", query, XingDunRedpacketBalanceLogPage::class.java)
                .let { AccountPageResult(it.redpacketBalance, it.list, it.total) }
        }
    }

    private fun fixtureAccountPage(section: Int, page: Int): AccountPageResult {
        if (page > 1) return AccountPageResult(5_500, emptyList(), 3)
        val packets = listOf(
            fixturePacket("RP20260805171258353168", 100, 1, "2026-08-05 17:12:58"),
            fixturePacket("RP20260805171241703532", 600, 3, "2026-08-05 17:12:41"),
            fixturePacket("RP20260805171224485782", 100, 1, "2026-08-05 17:12:24"),
        )
        val items: List<Any> = when (section) {
            0 -> packets
            1 -> listOf(
                XingDunReceivedRedpacketItem(
                    packet = packets.first().copy(sender = XingDunRedpacketUserPayload(timUserId = "t008", nickname = "t008")),
                    claim = XingDunRedpacketClaimPayload(id = 1, packetNo = packets.first().packetNo, claimAmount = 100, claimTime = "2026-08-05 17:13:08"),
                )
            )
            else -> listOf(
                fixtureLog(3, "RP20260805171258353168", 100, 5_500, "2026-08-06 17:15:00", true),
                fixtureLog(2, "RP20260805171241703532", -600, 4_800, "2026-08-05 17:12:41", false),
                fixtureLog(1, "RP20260805171224485782", -100, 4_700, "2026-08-05 17:12:24", false),
            )
        }
        return AccountPageResult(5_500, items, items.size)
    }

    private fun fixturePacket(packetNo: String, amount: Int, count: Int, date: String) = XingDunRedpacketListItem(
        packetNo = packetNo,
        status = XingDunRedpacketPresentationPolicy.STATUS_EXPIRED,
        statusName = activity.getString(R.string.xingdun_redpacket_expired),
        packetType = if (count > 1) "team_random" else "single",
        totalAmount = amount,
        count = count,
        greeting = activity.getString(R.string.xingdun_redpacket_default_greeting),
        createTime = date,
    )

    private fun fixtureLog(id: Int, packetNo: String, amount: Int, after: Int, date: String, refund: Boolean) = XingDunRedpacketBalanceLog(
        id = id,
        changeTypeText = activity.getString(if (refund) R.string.xingdun_redpacket_refund else R.string.xingdun_redpacket_send_log),
        changeAmount = amount,
        afterBalance = after,
        packetNo = packetNo,
        createTime = date,
    )

    private fun fixtureDetailPage(packetNo: String): XingDunRedpacketClaimRecordsPage = XingDunRedpacketClaimRecordsPage(
        packet = XingDunRedpacketDetailPayload(
            packetNo = packetNo,
            status = XingDunRedpacketPresentationPolicy.STATUS_EXPIRED,
            statusName = activity.getString(R.string.xingdun_redpacket_expired),
            packetType = "single",
            totalAmount = 100,
            count = 1,
            greeting = activity.getString(R.string.xingdun_redpacket_default_greeting),
            sender = XingDunRedpacketUserPayload(timUserId = "t008", nickname = "t008"),
            isSender = true,
            expireTime = "2026-08-06 17:12:58",
        ),
    )

    private suspend fun fetchDetailPage(session: XingDunStoredSession, packetNo: String, page: Int): XingDunRedpacketClaimRecordsPage {
        val api = XingDunSessionManager.apiClient()
        return runCatching {
            api.get<XingDunRedpacketClaimRecordsPage>(
                session,
                "redpacket/claimRecords",
                mapOf("packet_no" to packetNo, "page" to page.toString(), "page_size" to DETAIL_PAGE_SIZE.toString()),
                XingDunRedpacketClaimRecordsPage::class.java,
            )
        }.getOrElse {
            val detail = api.get<XingDunRedpacketDetailPayload>(
                session,
                "redpacket/detail",
                mapOf("packet_no" to packetNo),
                XingDunRedpacketDetailPayload::class.java,
            )
            XingDunRedpacketClaimRecordsPage(packet = detail)
        }
    }

    private fun renderDetail(
        session: XingDunStoredSession,
        page: XingDunRedpacketClaimRecordsPage,
        nextPage: Int = 2,
    ) {
        val detail = page.packet
        content.removeAllViews()
        val header = verticalCard().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20.dp(), 22.dp(), 20.dp(), 22.dp())
        }
        header.addView(avatar(detail.sender, 62))
        header.addView(centeredText(
            activity.getString(R.string.xingdun_redpacket_from_sender, detail.sender?.displayName?.ifBlank { null }
                ?: activity.getString(R.string.xingdun_enterprise_member)),
            18f,
            TEXT_PRIMARY,
            bold = true,
        ), matchWrap(top = 10))
        header.addView(centeredText(detail.greeting.ifBlank { activity.getString(R.string.xingdun_redpacket_default_greeting) }, 15f, TEXT_SECONDARY), matchWrap(top = 5))
        val received = detail.myClaim?.claimAmount
        if (received != null || detail.isSender) {
            header.addView(centeredText(money(received ?: detail.totalAmount), 28f, DANGER, bold = true), matchWrap(top = 14))
            if (received != null) header.addView(centeredText(activity.getString(R.string.xingdun_redpacket_deposited), 13f, TEXT_SECONDARY), matchWrap(top = 3))
        }
        header.addView(statusBadge(detailStatus(detail)), matchWrap(top = 12))
        content.addView(header, matchWrap())
        content.addView(secondaryButton(R.string.xingdun_redpacket_view_in_chat) {
            openSourceMessage(detail)
        }, matchWrap(top = 12))

        val sectionTitle = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                setText(R.string.xingdun_redpacket_claim_records)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_PRIMARY)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(activity).apply {
                text = activity.resources.getQuantityString(
                    R.plurals.xingdun_redpacket_claim_count,
                    detail.count,
                    detail.claimedCount,
                    detail.count,
                )
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
            })
        }
        content.addView(sectionTitle, matchWrap(top = 18, bottom = 8, horizontal = 2))
        if (page.list.isEmpty()) {
            content.addView(emptyCard(if (detail.status == XingDunRedpacketPresentationPolicy.STATUS_ACTIVE) {
                R.string.xingdun_redpacket_no_claims_yet
            } else R.string.xingdun_redpacket_no_claim_records))
        } else {
            page.list.forEach { claim -> content.addView(claimRecord(claim), matchWrap(bottom = 8)) }
        }
        if (page.list.size < page.total) {
            content.addView(secondaryButton(R.string.xingdun_load_more) {
                loadMoreClaims(session, page, nextPage)
            }, matchWrap(top = 4))
        }
    }

    private fun loadMoreClaims(session: XingDunStoredSession, current: XingDunRedpacketClaimRecordsPage, page: Int) {
        setBusy(true)
        activity.lifecycleScope.launch {
            runCatching { fetchDetailPage(session, targetID, page) }
                .onSuccess { next ->
                    setBusy(false)
                    val merged = XingDunRedpacketPresentationPolicy.mergeClaims(current.list, next.list)
                    renderDetail(
                        session,
                        next.copy(list = merged),
                        nextPage = page + 1,
                    )
                }
                .onFailure(::showFailure)
        }
    }

    private fun showEnvelope(session: XingDunStoredSession, page: XingDunRedpacketClaimRecordsPage) {
        val detail = page.packet
        val dialog = Dialog(activity).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCanceledOnTouchOutside(false)
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18.dp(), 10.dp(), 18.dp(), 24.dp())
            background = gradient(intArrayOf(0xFFD9292B.toInt(), 0xFF981014.toInt()), 28f, vertical = true)
        }
        card.addView(TextView(activity).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = activity.getString(R.string.xingdun_close)
            setOnClickListener { dialog.dismiss(); activity.finish() }
        }, LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { gravity = Gravity.START })
        card.addView(avatar(detail.sender, 62))
        card.addView(centeredText(
            activity.getString(R.string.xingdun_redpacket_sent_by, detail.sender?.displayName?.ifBlank { null }
                ?: activity.getString(R.string.xingdun_enterprise_member)),
            17f,
            Color.WHITE,
            bold = true,
        ), matchWrap(top = 10))
        card.addView(centeredText(detail.greeting.ifBlank { activity.getString(R.string.xingdun_redpacket_default_greeting) }, 23f, GOLD, bold = true), matchWrap(top = 8, horizontal = 12))
        val actionArea = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = 190.dp()
        }
        val canClaim = XingDunRedpacketPresentationPolicy.canClaim(detail.status, detail.hasClaimed, detail.canClaim)
        if (canClaim) {
            actionArea.addView(TextView(activity).apply {
                setText(R.string.xingdun_redpacket_open)
                textSize = 30f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(0xFF8C1A1A.toInt())
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GOLD) }
                contentDescription = activity.getString(R.string.xingdun_redpacket_receive)
                setOnClickListener { button ->
                    button.isEnabled = false
                    button.alpha = DISABLED_ALPHA
                    claim(session, dialog)
                }
            }, LinearLayout.LayoutParams(82.dp(), 82.dp()))
        } else {
            actionArea.addView(centeredText(envelopeStatus(detail), 16f, 0xD8FFFFFF.toInt(), bold = true))
            actionArea.addView(TextView(activity).apply {
                setText(R.string.xingdun_redpacket_view_claim_detail)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(GOLD)
                setPadding(0, 16.dp(), 0, 8.dp())
                setOnClickListener { dialog.dismiss() }
            })
        }
        card.addView(actionArea, matchWrap(top = 24))
        dialog.setContentView(card)
        dialog.show()
        dialog.window?.apply {
            setLayout(330.dp(), WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.58f }
        }
    }

    private fun claim(session: XingDunStoredSession, dialog: Dialog) {
        setBusy(true)
        activity.lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().post<XingDunRedpacketClaimResultPayload>(
                    session,
                    "redpacket/claim",
                    mapOf(
                        "packet_no" to targetID,
                        "client_type" to "android",
                        "device_id" to XingDunSessionManager.deviceId().take(128),
                    ),
                    XingDunRedpacketClaimResultPayload::class.java,
                )
            }.onSuccess { result ->
                dialog.dismiss()
                setBusy(false)
                XingDunRedpacketStatusLoader.publish(
                    targetID,
                    XingDunRedpacketClaimStatusPolicy.values(result),
                )
                Toast.makeText(activity, activity.getString(R.string.xingdun_redpacket_claim_success, money(result.claimAmount)), Toast.LENGTH_SHORT).show()
                activity.lifecycleScope.launch {
                    val refreshed = runCatching { fetchDetailPage(session, targetID, 1) }
                        .getOrDefault(XingDunRedpacketClaimRecordsPage(packet = result.detail))
                    renderDetail(session, refreshed)
                }
            }.onFailure {
                dialog.dismiss()
                showFailure(it)
            }
        }
    }

    private suspend fun sendRedpacket(
        session: XingDunStoredSession,
        isGroup: Boolean,
        draft: XingDunValidatedRedpacketDraft,
    ) {
        val body = linkedMapOf<String, Any>(
            "scene" to if (isGroup) "team" else "p2p",
            "packet_type" to draft.type.wireValue,
            "total_amount" to draft.totalAmount,
            "count" to draft.count,
            "greeting" to draft.greeting,
            "conversation_id" to targetID,
        ).apply {
            if (isGroup) put("tim_group_id", targetID.removePrefix("group_"))
            else put("receiver_tim_user_id", targetID.removePrefix("c2c_"))
            draft.exclusiveReceiverTimUserId?.let { put("exclusive_receiver_tim_user_id", it) }
        }
        val api = XingDunSessionManager.apiClient()
        val prepared = api.post<com.google.gson.JsonObject>(session, "redpacket/prepareSend", body, com.google.gson.JsonObject::class.java)
        val packetNo = prepared.get("packet_no")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException(activity.getString(R.string.xingdun_redpacket_prepare_failed))
        try {
            api.post<com.google.gson.JsonObject>(
                session, "redpacket/confirmSent", mapOf("packet_no" to packetNo), com.google.gson.JsonObject::class.java,
            )
        } catch (error: Throwable) {
            runCatching {
                api.postEmpty(
                    session,
                    "redpacket/cancelPending",
                    mapOf("packet_no" to packetNo, "cancel_reason" to "Android confirm failed"),
                )
            }
            throw error
        }
    }

    private suspend fun loadMembers(groupID: String): List<MemberChoice> {
        val store = GroupMemberStore.create(groupID)
        suspendUntilLoaded { completion -> store.loadMembers(listOf(GroupMemberFilterRole.ALL), completion) }
        while (store.state.hasMoreMembers.value) suspendUntilLoaded(store::loadMoreMembers)
        val self = LoginStore.shared.loginState.loginUserInfo.value?.userID
        return store.state.memberList.value
            .filter { it.userID.isNotBlank() && it.userID != self }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
            .map { MemberChoice(it.userID, it.displayName) }
    }

    private suspend fun suspendUntilLoaded(load: (CompletionHandler) -> Unit) =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            load(object : CompletionHandler {
                override fun onSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onFailure(code: Int, desc: String) {
                    if (continuation.isActive) continuation.resumeWith(
                        Result.failure(IllegalStateException(desc.ifBlank { "Group member load failed: $code" })),
                    )
                }
            })
        }

    private fun sentRecord(item: XingDunRedpacketListItem): View = recordCard(
        title = item.greeting.ifBlank { activity.getString(R.string.xingdun_redpacket_default_greeting) },
        identifier = item.packetNo,
        date = item.createTime,
        amount = "−${money(item.totalAmount)}",
        amountColor = DANGER,
        detail = activity.getString(
            R.string.xingdun_redpacket_sent_record_detail,
            item.claimedCount,
            item.count,
            listStatus(item.status, item.statusName),
        ),
        packetNo = item.packetNo,
    )

    private fun receivedRecord(item: XingDunReceivedRedpacketItem): View = recordCard(
        title = item.packet.greeting.ifBlank { activity.getString(R.string.xingdun_redpacket_default_greeting) },
        identifier = item.packet.packetNo,
        date = item.claim.claimTime ?: item.packet.createTime,
        amount = "+${money(item.claim.claimAmount)}",
        amountColor = INFO,
        detail = activity.getString(
            R.string.xingdun_redpacket_from_record_detail,
            item.packet.sender?.displayName?.ifBlank { null } ?: activity.getString(R.string.xingdun_enterprise_member),
        ),
        packetNo = item.packet.packetNo,
    )

    private fun balanceLogRecord(item: XingDunRedpacketBalanceLog): View = recordCard(
        title = balanceLogTitle(item),
        identifier = item.packetNo.ifBlank { "#${item.id}" },
        date = item.createTime,
        amount = "${if (item.changeAmount >= 0) "+" else "−"}${money(kotlin.math.abs(item.changeAmount))}",
        amountColor = if (item.changeAmount >= 0) INFO else DANGER,
        detail = activity.getString(R.string.xingdun_redpacket_balance_after, money(item.afterBalance)),
        packetNo = item.packetNo.takeIf(String::isNotBlank),
    )

    private fun balanceLogTitle(item: XingDunRedpacketBalanceLog): String = when (item.changeType) {
        "admin_recharge" -> activity.getString(R.string.xingdun_redpacket_log_admin_recharge)
        "admin_deduct" -> activity.getString(R.string.xingdun_redpacket_log_admin_deduct)
        "send_packet" -> activity.getString(R.string.xingdun_redpacket_send_log)
        "claim_packet" -> activity.getString(R.string.xingdun_redpacket_log_claim)
        "refund_packet" -> activity.getString(R.string.xingdun_redpacket_refund)
        "manual_adjust" -> activity.getString(R.string.xingdun_redpacket_log_manual_adjust)
        else -> item.changeTypeText.ifBlank {
            item.remark.ifBlank { activity.getString(R.string.xingdun_redpacket_balance_change) }
        }
    }

    private fun recordCard(
        title: String,
        identifier: String,
        date: String?,
        amount: String,
        amountColor: Int,
        detail: String,
        packetNo: String?,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 13.dp(), 16.dp(), 13.dp())
        background = rounded(Color.WHITE, 14f)
        val left = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16f, TEXT_PRIMARY, true))
            addView(label(identifier, 12f, TEXT_SECONDARY), matchWrap(top = 3))
            date?.takeIf(String::isNotBlank)?.let { addView(label(it, 12f, TEXT_TERTIARY), matchWrap(top = 2)) }
        }
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(label(amount, 16f, amountColor, true).apply { gravity = Gravity.END })
            addView(label(detail, 12f, TEXT_SECONDARY).apply { gravity = Gravity.END; maxLines = 2 }, matchWrap(top = 5))
        })
        if (!packetNo.isNullOrBlank()) {
            isClickable = true
            isFocusable = true
            setOnClickListener { openSourceMessage(packetNo) }
        }
    }.also { it.layoutParams = matchWrap(bottom = 8) }

    private fun openSourceMessage(packetNo: String) {
        val activeSession = session
        if (activeSession == null) {
            Toast.makeText(activity, R.string.xingdun_session_expired, Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true)
        activity.lifecycleScope.launch {
            runCatching {
                if (fixtureEnabled) fixtureDetailPage(packetNo).packet else XingDunSessionManager.apiClient().get<XingDunRedpacketDetailPayload>(
                    activeSession,
                    "redpacket/detail",
                    mapOf("packet_no" to packetNo),
                    XingDunRedpacketDetailPayload::class.java,
                )
            }.onSuccess { detail ->
                setBusy(false)
                openSourceMessage(detail)
            }.onFailure(::showFailure)
        }
    }

    private fun openSourceMessage(detail: XingDunRedpacketDetailPayload) {
        val destination = XingDunRedpacketMessageDestinationPolicy.resolve(detail)
        if (destination == null) {
            Toast.makeText(activity, R.string.xingdun_redpacket_source_message_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        ChatActivity.startForMessageID(
            activity,
            destination.conversationId,
            destination.messageId,
            destination.messageSequence,
        )
        activity.finish()
    }

    private fun claimRecord(claim: XingDunRedpacketClaimPayload): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        background = rounded(Color.WHITE, 14f)
        addView(avatar(claim.user, 42), LinearLayout.LayoutParams(42.dp(), 42.dp()))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(claim.user?.displayName?.ifBlank { null } ?: activity.getString(R.string.xingdun_enterprise_member), 15f, TEXT_PRIMARY))
            claim.claimTime?.takeIf(String::isNotBlank)?.let { addView(label(it, 12f, TEXT_SECONDARY), matchWrap(top = 2)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12.dp() })
        addView(label(money(claim.claimAmount), 15f, TEXT_PRIMARY, true))
    }

    private fun avatar(user: XingDunRedpacketUserPayload?, size: Int): ImageView = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFF4C469.toInt()) }
        setImageResource(R.drawable.xingdun_ic_gift_white)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = user?.displayName
        user?.avatar?.takeIf(String::isNotBlank)?.let { url ->
            imageTintList = null
            Glide.with(this).load(url).circleCrop().into(this)
        }
    }.also { it.layoutParams = LinearLayout.LayoutParams(size.dp(), size.dp()) }

    private fun detailStatus(detail: XingDunRedpacketDetailPayload): String {
        if (detail.hasClaimed) return activity.getString(R.string.xingdun_redpacket_claimed)
        return when (detail.status) {
            XingDunRedpacketPresentationPolicy.STATUS_PENDING -> activity.getString(R.string.xingdun_redpacket_sending)
            XingDunRedpacketPresentationPolicy.STATUS_ACTIVE -> activity.getString(
                if (detail.isSender) R.string.xingdun_redpacket_waiting_claim else R.string.xingdun_redpacket_unavailable_to_claim,
            )
            XingDunRedpacketPresentationPolicy.STATUS_EXHAUSTED -> activity.getString(R.string.xingdun_redpacket_exhausted)
            XingDunRedpacketPresentationPolicy.STATUS_EXPIRED, XingDunRedpacketPresentationPolicy.STATUS_REFUNDED -> activity.getString(R.string.xingdun_redpacket_expired)
            XingDunRedpacketPresentationPolicy.STATUS_CANCELLED -> activity.getString(R.string.xingdun_redpacket_cancelled)
            else -> detail.statusName.ifBlank { activity.getString(R.string.xingdun_redpacket_status_loading) }
        }
    }

    private fun envelopeStatus(detail: XingDunRedpacketDetailPayload): String = when {
        detail.hasClaimed -> activity.getString(R.string.xingdun_redpacket_claimed)
        detail.isSender && detail.status == XingDunRedpacketPresentationPolicy.STATUS_ACTIVE -> activity.getString(R.string.xingdun_redpacket_waiting_other_claim)
        detail.status == XingDunRedpacketPresentationPolicy.STATUS_PENDING -> activity.getString(R.string.xingdun_redpacket_sending)
        detail.status == XingDunRedpacketPresentationPolicy.STATUS_ACTIVE -> activity.getString(R.string.xingdun_redpacket_unavailable_to_claim)
        detail.status == XingDunRedpacketPresentationPolicy.STATUS_EXHAUSTED -> activity.getString(R.string.xingdun_redpacket_envelope_exhausted)
        detail.status == XingDunRedpacketPresentationPolicy.STATUS_EXPIRED || detail.status == XingDunRedpacketPresentationPolicy.STATUS_REFUNDED -> activity.getString(R.string.xingdun_redpacket_envelope_expired)
        detail.status == XingDunRedpacketPresentationPolicy.STATUS_CANCELLED -> activity.getString(R.string.xingdun_redpacket_envelope_cancelled)
        else -> detail.statusName.ifBlank { activity.getString(R.string.xingdun_redpacket_status_loading) }
    }

    private fun listStatus(status: Int, fallback: String): String = when (status) {
        XingDunRedpacketPresentationPolicy.STATUS_PENDING -> activity.getString(R.string.xingdun_redpacket_sending)
        XingDunRedpacketPresentationPolicy.STATUS_ACTIVE -> activity.getString(R.string.xingdun_redpacket_claiming)
        XingDunRedpacketPresentationPolicy.STATUS_EXHAUSTED -> activity.getString(R.string.xingdun_redpacket_fully_claimed)
        XingDunRedpacketPresentationPolicy.STATUS_EXPIRED, XingDunRedpacketPresentationPolicy.STATUS_REFUNDED -> activity.getString(R.string.xingdun_redpacket_expired)
        XingDunRedpacketPresentationPolicy.STATUS_CANCELLED -> activity.getString(R.string.xingdun_redpacket_cancelled)
        else -> fallback
    }

    private fun validationMessage(error: XingDunRedpacketValidationError): Int = when (error) {
        XingDunRedpacketValidationError.INVALID_AMOUNT -> R.string.xingdun_redpacket_error_invalid_amount
        XingDunRedpacketValidationError.AMOUNT_TOO_LARGE -> R.string.xingdun_redpacket_error_amount_too_large
        XingDunRedpacketValidationError.INSUFFICIENT_BALANCE -> R.string.xingdun_redpacket_error_insufficient_balance
        XingDunRedpacketValidationError.INVALID_COUNT -> R.string.xingdun_redpacket_error_invalid_count
        XingDunRedpacketValidationError.AMOUNT_BELOW_COUNT -> R.string.xingdun_redpacket_error_amount_below_count
        XingDunRedpacketValidationError.MISSING_EXCLUSIVE_RECEIVER -> R.string.xingdun_redpacket_error_missing_receiver
        XingDunRedpacketValidationError.INVALID_GREETING -> R.string.xingdun_redpacket_error_invalid_greeting
    }

    private fun ensureEnabled(): Boolean {
        if (session?.features?.redpacket == true) return true
        info(R.string.xingdun_redpacket_closed_detail)
        return false
    }

    private fun preparePage() {
        content.removeAllViews()
        content.setBackgroundColor(PAGE_BG)
        scrollView.setBackgroundColor(PAGE_BG)
        statusView.setTextColor(DANGER)
    }

    private fun info(message: Int) {
        preparePage()
        content.addView(emptyCard(message))
    }

    private fun sectionLabel(label: Int) {
        content.addView(TextView(activity).apply {
            setText(label)
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
        }, matchWrap(top = 14, bottom = 4, horizontal = 4))
    }

    private fun labeledInputRow(label: Int, input: EditText, suffix: Int): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 10.dp(), 14.dp(), 10.dp())
        addView(TextView(activity).apply {
            setText(label)
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
        })
        addView(input, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        addView(TextView(activity).apply {
            setText(suffix)
            textSize = 15f
            setTextColor(TEXT_SECONDARY)
        })
    }

    private fun clickableValueRow(label: Int, value: TextView, action: () -> Unit): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16.dp(), 9.dp(), 12.dp(), 9.dp())
        addView(TextView(activity).apply {
            setText(label)
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
        })
        addView(value, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 12.dp() })
        addView(label("›", 24f, TEXT_TERTIARY))
        setOnClickListener { action() }
    }

    private fun verticalCard(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 14f)
    }

    private fun segmentedContainer(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        background = rounded(0xFFE9EAEE.toInt(), 14f)
    }

    private fun segmentButton(label: Int, action: () -> Unit): Button = Button(activity).apply {
        setText(label)
        textSize = 13f
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(4.dp(), 7.dp(), 4.dp(), 7.dp())
        setOnClickListener { action() }
    }

    private fun refreshSegments(buttons: List<Button>, selected: Int) {
        buttons.forEachIndexed { index, button ->
            button.setTextColor(if (index == selected) TEXT_PRIMARY else TEXT_SECONDARY)
            button.typeface = if (index == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            button.background = rounded(if (index == selected) Color.WHITE else Color.TRANSPARENT, 11f)
            button.stateListAnimator = null
        }
    }

    private fun destructiveButton(label: Int, action: () -> Unit): Button = Button(activity).apply {
        setText(label)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(DANGER)
        setOnClickListener { action() }
        minHeight = 54.dp()
    }

    private fun secondaryButton(label: Int, action: () -> Unit): Button = Button(activity).apply {
        setText(label)
        textSize = 14f
        isAllCaps = false
        setTextColor(TEAL)
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        setOnClickListener { action() }
    }

    private fun compactButton(textValue: String, action: () -> Unit): Button = Button(activity).apply {
        text = textValue
        textSize = 20f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(TEXT_PRIMARY)
        backgroundTintList = ColorStateList.valueOf(0xFFF1F2F5.toInt())
        setOnClickListener { action() }
    }

    private fun emptyCard(message: Int): View = TextView(activity).apply {
        setText(message)
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(TEXT_SECONDARY)
        setPadding(16.dp(), 24.dp(), 16.dp(), 24.dp())
        background = rounded(Color.WHITE, 14f)
    }

    private fun statusBadge(text: String): View = TextView(activity).apply {
        this.text = text
        textSize = 13f
        setTextColor(TEXT_SECONDARY)
        gravity = Gravity.CENTER
        setPadding(12.dp(), 6.dp(), 12.dp(), 6.dp())
        background = rounded(0xFFF0F1F4.toInt(), 13f)
    }

    private fun centeredText(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        label(text, size, color, bold).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(activity).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun divider(): View = View(activity).apply { setBackgroundColor(0xFFE8E8EA.toInt()) }
        .also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply { marginStart = 16.dp() } }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.dp().toFloat()
    }

    private fun gradient(colors: IntArray, radius: Float, vertical: Boolean = false): GradientDrawable =
        GradientDrawable(if (vertical) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = radius.dp().toFloat()
        }

    private fun matchWrap(
        top: Int = 0,
        bottom: Int = 0,
        horizontal: Int = 0,
        vertical: Int = 0,
    ) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (top + vertical).dp()
        bottomMargin = (bottom + vertical).dp()
        marginStart = horizontal.dp()
        marginEnd = horizontal.dp()
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun recordKey(item: Any): String = when (item) {
        is XingDunRedpacketListItem -> "packet:${item.packetNo}"
        is XingDunReceivedRedpacketItem -> "received:${item.packet.packetNo}"
        is XingDunRedpacketBalanceLog -> "log:${item.id}"
        else -> item.hashCode().toString()
    }

    private fun money(cents: Int): String = activity.getString(R.string.xingdun_currency_amount, cents / 100.0)

    private fun setBusy(busy: Boolean) {
        if (busy) statusView.setText(R.string.xingdun_loading) else statusView.text = ""
    }

    private fun showFailure(error: Throwable) {
        setBusy(false)
        statusView.text = error.localizedMessage ?: activity.getString(R.string.xingdun_action_failed)
    }

    private fun Int.dp(): Int = (this * activity.resources.displayMetrics.density).toInt()
    private fun Float.dp(): Float = this * activity.resources.displayMetrics.density

    private companion object {
        const val PAGE_SIZE = 20
        const val DETAIL_PAGE_SIZE = 30
        const val DISABLED_ALPHA = 0.48f
        val PAGE_BG = 0xFFF5F6FA.toInt()
        val TEXT_PRIMARY = 0xFF17181A.toInt()
        val TEXT_SECONDARY = 0xFF7E8086.toInt()
        val TEXT_TERTIARY = 0xFFB5B7BC.toInt()
        val DANGER = 0xFFD63336.toInt()
        val INFO = 0xFF2E6EC8.toInt()
        val TEAL = 0xFF20B79A.toInt()
        val GOLD = 0xFFFFD473.toInt()
    }
}
