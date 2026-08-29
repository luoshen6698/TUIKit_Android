package io.trtc.tuikit.chat.demo.search

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.uikit.components.search.ui.SearchMessageInConversationPage
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageInConversationViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class ConversationSearchActivity : BaseActivity() {

    override val requiresLogin: Boolean get() = !isDebugPreview

    private val isDebugPreview get() = intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)
    private val themeStore by lazy { ThemeStore.shared(this) }
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: ImageView
    private lateinit var divider: View
    private lateinit var page: SearchMessageInConversationPage

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_AVATAR_URL = "avatar_url"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_chat_history_preview"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val BRAND_SOFT = 0x1F23B39C
        private const val TEXT_TAG = "xingdun_chat_history_category_label"

        fun start(
            context: Context,
            conversationID: String,
            displayName: String,
            avatarURL: String? = null
        ) {
            context.startActivity(Intent(context, ConversationSearchActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_AVATAR_URL, avatarURL)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        val conversationID = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        if (conversationID.isBlank()) {
            finish()
            return
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.demo_activity_chat_setting)
        root = findViewById(R.id.demo_chatSettingRootContainer)
        header = findViewById(R.id.demo_chatHeaderContainer)
        title = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        divider = findViewById(R.id.demo_headerDivider)
        title.setText(R.string.xingdun_chat_history_title)
        findViewById<ImageView>(R.id.demo_btnMore).visibility = View.GONE
        findViewById<FrameLayout>(R.id.demo_badgeContainer).visibility = View.GONE
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = systemBars.top)
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        page = SearchMessageInConversationPage(
            context = this,
            viewModel = ViewModelProvider(this)[SearchMessageInConversationViewModel::class.java]
        ).apply {
            onBack = { finish() }
            onMessageClick = { message ->
                ChatActivity.start(this@ConversationSearchActivity, conversationID, message)
                finish()
            }
        }
        val isGroup = conversationID.startsWith("group_")
        page.configureHistoryLanding(
            buildCategoryLanding(conversationID, isGroup),
            getString(R.string.xingdun_chat_history_search_hint),
        )
        findViewById<FrameLayout>(R.id.demo_chatSettingContainer).addView(
            page,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        page.start(
            conversationID = conversationID,
            displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty(),
            avatarURL = intent.getStringExtra(EXTRA_AVATAR_URL)
        )
        lifecycleScope.launch {
            themeStore.themeState.collectLatest { applyColors(it.currentTheme.tokens.color) }
        }
    }

    private fun buildCategoryLanding(conversationID: String, isGroup: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(8.dp(), 12.dp(), 8.dp(), 22.dp())
        val row = LinearLayout(this@ConversationSearchActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val categories = buildList {
            if (isGroup) add(Category(R.string.xingdun_chat_history_member, android.R.drawable.ic_menu_myplaces, XingDunChatHistoryCategoryActivity.MODE_MEMBER))
            add(Category(R.string.xingdun_chat_history_image, android.R.drawable.ic_menu_gallery, XingDunChatHistoryCategoryActivity.MODE_IMAGE))
            add(Category(R.string.xingdun_chat_history_video, android.R.drawable.ic_menu_slideshow, XingDunChatHistoryCategoryActivity.MODE_VIDEO))
            add(Category(R.string.xingdun_chat_history_file, android.R.drawable.ic_menu_save, XingDunChatHistoryCategoryActivity.MODE_FILE))
            add(Category(R.string.xingdun_chat_history_date, android.R.drawable.ic_menu_today, XingDunChatHistoryCategoryActivity.MODE_DATE))
        }
        categories.forEach { category ->
            row.addView(categoryButton(category) {
                XingDunChatHistoryCategoryActivity.start(
                    this@ConversationSearchActivity,
                    conversationID,
                    category.mode,
                    debugPreview = isDebugPreview,
                )
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun categoryButton(category: Category, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = 86.dp()
        isClickable = true
        isFocusable = true
        contentDescription = getString(category.label)
        setOnClickListener { onClick() }
        addView(ImageView(this@ConversationSearchActivity).apply {
            setImageResource(category.icon)
            imageTintList = ColorStateList.valueOf(BRAND)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(BRAND_SOFT)
            }
        }, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        addView(TextView(this@ConversationSearchActivity).apply {
            setText(category.label)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.NORMAL)
            tag = TEXT_TAG
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 6.dp()
        })
    }

    private fun applyColors(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        title.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
        recolor(page, colors)
    }

    private fun recolor(view: View, colors: ColorTokens) {
        if (view.tag == TEXT_TAG && view is TextView) view.setTextColor(colors.textColorPrimary)
        if (view is ViewGroup) for (index in 0 until view.childCount) recolor(view.getChildAt(index), colors)
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private data class Category(val label: Int, val icon: Int, val mode: String)
}
