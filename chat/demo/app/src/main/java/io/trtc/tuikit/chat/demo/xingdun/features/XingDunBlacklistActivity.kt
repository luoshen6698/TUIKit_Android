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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Android counterpart of iOS `XingDunBlacklistView`. */
class XingDunBlacklistActivity : BaseActivity() {
    private val contactStore = ContactStore.shared
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var list: RecyclerView
    private lateinit var status: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var statusProgress: ProgressBar
    private lateinit var warning: TextView
    private lateinit var retry: TextView
    private val adapter = BlacklistAdapter(::openDetail, ::confirmUnblock)
    private var contacts: List<ContactInfo> = emptyList()
    private var loading = true
    private var loadError: String? = null
    private var operationError: String? = null
    private val operatingUserIDs = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildPage()
        lifecycleScope.launch {
            contactStore.state.blackList.collectLatest {
                contacts = it
                render()
            }
        }
        refresh()
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAGE_BACKGROUND) }
        root.addView(header(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))
        val body = FrameLayout(this)
        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(BRAND)
            setOnRefreshListener { refresh() }
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAGE_BACKGROUND) }
        warning = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF8A6215.toInt())
            background = rounded(0xFFFFF4D6.toInt(), 10f)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            visibility = View.GONE
        }
        column.addView(warning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = 16.dp(); marginEnd = 16.dp(); topMargin = 10.dp()
        })
        column.addView(TextView(this).apply {
            setText(R.string.xingdun_blacklist_explanation); textSize = 13f; setTextColor(TEXT_SECONDARY)
            setPadding(20.dp(), 16.dp(), 20.dp(), 12.dp())
        })
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@XingDunBlacklistActivity)
            adapter = this@XingDunBlacklistActivity.adapter
            itemAnimator = null
            setBackgroundColor(Color.WHITE)
        }
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val contact = adapter.itemAt(viewHolder.bindingAdapterPosition) ?: return 0
                return if (operatingUserIDs.contains(contact.userID)) 0 else super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val contact = adapter.itemAt(position)
                adapter.notifyItemChanged(position)
                if (contact != null) confirmUnblock(contact)
            }
        }).attachToRecyclerView(list)
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(TextView(this).apply {
            setText(R.string.xingdun_blacklist_footer); textSize = 13f; setTextColor(TEXT_SECONDARY)
            setPadding(20.dp(), 12.dp(), 20.dp(), 18.dp())
        })
        swipeRefresh.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(swipeRefresh, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        statusIcon = ImageView(this).apply {
            setImageResource(R.drawable.xingdun_ic_contacts_blacklist)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        statusProgress = ProgressBar(this).apply { isIndeterminate = true }
        statusTitle = TextView(this).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY) }
        statusMessage = TextView(this).apply { textSize = 13f; gravity = Gravity.CENTER; setTextColor(TEXT_SECONDARY); setPadding(28.dp(), 8.dp(), 28.dp(), 16.dp()) }
        retry = button(getString(R.string.xingdun_retry), true) { refresh() }
        status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 190.dp(), 24.dp(), 24.dp())
            addView(statusIcon, LinearLayout.LayoutParams(72.dp(), 72.dp()).apply { bottomMargin = 18.dp() })
            addView(statusProgress, LinearLayout.LayoutParams(36.dp(), 36.dp()).apply { bottomMargin = 18.dp() })
            addView(statusTitle); addView(statusMessage); addView(retry, LinearLayout.LayoutParams(150.dp(), 42.dp()))
        }
        body.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(
            XingDunChildBottomNavigation(this).apply {
                bind(this@XingDunBlacklistActivity, MainActivity.TAB_CONTACTS)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        render()
    }

    private fun header() = FrameLayout(this).apply {
        setBackgroundColor(Color.WHITE)
        addView(TextView(this@XingDunBlacklistActivity).apply {
            text = "‹"; textSize = 38f; gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY); setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(52.dp(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        addView(TextView(this@XingDunBlacklistActivity).apply {
            setText(R.string.xingdun_blacklist_title); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(TEXT_PRIMARY)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addView(TextView(this@XingDunBlacklistActivity).apply {
            text = "↻"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(BRAND); contentDescription = getString(R.string.xingdun_refresh_blacklist)
            background = rounded(0xFFF0F8F6.toInt(), 22f)
            setOnClickListener { refresh() }
        }, FrameLayout.LayoutParams(42.dp(), 42.dp(), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = 5.dp() })
    }

    private fun refresh() {
        loading = contacts.isEmpty()
        loadError = null
        render()
        contactStore.loadBlackList(object : CompletionHandler {
            override fun onSuccess() { loading = false; swipeRefresh.isRefreshing = false; render() }
            override fun onFailure(code: Int, desc: String) {
                loading = false; swipeRefresh.isRefreshing = false
                loadError = desc.ifBlank { getString(R.string.xingdun_blacklist_load_failed_message) }
                render()
            }
        })
    }

    private fun render() {
        adapter.submit(contacts)
        val showList = contacts.isNotEmpty()
        val visibleWarning = operationError ?: loadError?.takeIf { showList }
        warning.text = visibleWarning.orEmpty()
        warning.visibility = if (visibleWarning.isNullOrBlank()) View.GONE else View.VISIBLE
        swipeRefresh.visibility = if (showList) View.VISIBLE else View.GONE
        status.visibility = if (showList) View.GONE else View.VISIBLE
        statusProgress.visibility = if (loading) View.VISIBLE else View.GONE
        statusIcon.visibility = if (loading) View.GONE else View.VISIBLE
        retry.visibility = if (loadError != null) View.VISIBLE else View.GONE
        statusTitle.text = when {
            loading -> getString(R.string.xingdun_loading_blacklist)
            loadError != null -> getString(R.string.xingdun_blacklist_load_failed)
            else -> getString(R.string.xingdun_blacklist_empty)
        }
        statusMessage.text = when {
            loading -> ""
            loadError != null -> loadError
            else -> getString(R.string.xingdun_blacklist_empty_message)
        }
    }

    private fun openDetail(contact: ContactInfo) = XingDunContactDetailActivity.start(this, contact)

    private fun confirmUnblock(contact: ContactInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_unblock_title)
            .setMessage(R.string.xingdun_unblock_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_unblock) { _, _ -> unblock(contact) }
            .show()
    }

    private fun unblock(contact: ContactInfo) {
        if (!operatingUserIDs.add(contact.userID)) return
        operationError = null
        adapter.notifyDataSetChanged()
        val handled = BusinessActionRegistry.dispatch(
            BusinessAction.SetFriendBlacklist(contact.userID, false),
            object : BusinessActionCompletion {
                override fun onSuccess(result: BusinessActionResult) {
                    operatingUserIDs.remove(contact.userID)
                    contactStore.loadBlackList()
                    render()
                    Toast.makeText(this@XingDunBlacklistActivity, R.string.xingdun_unblock_success, Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(code: Int, description: String) = showOperationFailure(contact, description)
            }
        )
        if (handled) return
        contactStore.removeFromBlacklist(contact.userID, object : CompletionHandler {
            override fun onSuccess() {
                operatingUserIDs.remove(contact.userID)
                contactStore.loadBlackList()
                render()
                Toast.makeText(this@XingDunBlacklistActivity, R.string.xingdun_unblock_success, Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(code: Int, desc: String) = showOperationFailure(contact, desc)
        })
    }

    private fun showOperationFailure(contact: ContactInfo, message: String) {
        operatingUserIDs.remove(contact.userID)
        operationError = message.ifBlank { getString(R.string.xingdun_unblock_failed) }
        render()
    }

    private fun showError(message: String) = Toast.makeText(
        this,
        message.ifBlank { getString(R.string.xingdun_unblock_failed) },
        Toast.LENGTH_LONG
    ).show()

    private fun button(text: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else BRAND)
        background = rounded(if (primary) BRAND else Color.WHITE, 8f, if (primary) null else BRAND)
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = radius.dp(); setColor(color); stroke?.let { setStroke(1.dp(), it) }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp() = this * resources.displayMetrics.density

    private inner class BlacklistAdapter(
        private val onOpen: (ContactInfo) -> Unit,
        private val onUnblock: (ContactInfo) -> Unit
    ) : RecyclerView.Adapter<BlacklistAdapter.Holder>() {
        private var items: List<ContactInfo> = emptyList()
        fun submit(value: List<ContactInfo>) { items = value; notifyDataSetChanged() }
        fun itemAt(position: Int): ContactInfo? = items.getOrNull(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp()); setBackgroundColor(Color.WHITE)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val avatar = Avatar(parent.context)
            row.addView(avatar, LinearLayout.LayoutParams(48.dp(), 48.dp()))
            val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(parent.context).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(TEXT_PRIMARY); maxLines = 1 }
            val account = TextView(parent.context).apply { textSize = 13f; setTextColor(TEXT_SECONDARY); maxLines = 1 }
            texts.addView(name); texts.addView(account)
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12.dp(); marginEnd = 10.dp() })
            val unblock = button(getString(R.string.xingdun_unblock), false) {}
            row.addView(unblock, LinearLayout.LayoutParams(72.dp(), 34.dp()))
            return Holder(row, avatar, name, account, unblock)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.avatar.setContent(
                if (item.avatarURL.isNullOrBlank()) Avatar.AvatarContent.Text(item.displayName)
                else Avatar.AvatarContent.Image(item.avatarURL, item.displayName)
            )
            holder.name.text = item.displayName
            holder.account.text = item.userID
            holder.itemView.setOnClickListener { onOpen(item) }
            holder.unblock.setOnClickListener { onUnblock(item) }
            val operating = operatingUserIDs.contains(item.userID)
            holder.unblock.isEnabled = !operating
            holder.unblock.alpha = if (operating) 0.55f else 1f
        }

        override fun getItemCount() = items.size
        inner class Holder(itemView: View, val avatar: Avatar, val name: TextView, val account: TextView, val unblock: TextView) : RecyclerView.ViewHolder(itemView)
    }

    companion object {
        private const val PAGE_BACKGROUND = 0xFFF5F6FA.toInt()
        private const val BRAND = 0xFF23B39C.toInt()
        private const val TEXT_PRIMARY = 0xFF15191D.toInt()
        private const val TEXT_SECONDARY = 0xFF7A8088.toInt()
        fun start(context: Context) = context.startActivity(Intent(context, XingDunBlacklistActivity::class.java))
    }
}
