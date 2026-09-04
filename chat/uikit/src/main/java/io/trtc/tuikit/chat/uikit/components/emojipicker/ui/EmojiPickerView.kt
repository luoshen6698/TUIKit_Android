package io.trtc.tuikit.chat.uikit.components.emojipicker.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiPickerUnicodeCatalog
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null
    private val viewPager: ViewPager2
    private val pageIndicator: LinearLayout
    private val pageAdapters = linkedSetOf<UnicodeEmojiPageAdapter>()

    private var onEmojiClick: (EmojiGroup, Emoji) -> Unit = { _, _ -> }
    private var onDeleteClick: () -> Unit = {}

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }

        viewPager = ViewPager2(context).apply {
            id = View.generateViewId()
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            offscreenPageLimit = 1
        }
        rootLayout.addView(
            viewPager,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        pageIndicator = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        rootLayout.addView(
            pageIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(24f).toInt()
            )
        )

        addView(
            rootLayout,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        viewPager.adapter = EmojiPagerAdapter(EmojiPickerUnicodeCatalog.pages.size) { position ->
            createEmojiPage(position)
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position)
            }
        })

        buildPageIndicator()
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    /**
     * onSendClick is retained for source compatibility. Sending stays in the composer toolbar,
     * matching the iOS emoji panel.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setup(
        onEmojiClick: (EmojiGroup, Emoji) -> Unit = { _, _ -> },
        onDeleteClick: () -> Unit = {},
        onSendClick: () -> Unit = {}
    ) {
        this.onEmojiClick = onEmojiClick
        this.onDeleteClick = onDeleteClick
    }

    private fun createEmojiPage(position: Int): RecyclerView {
        val emojis = EmojiPickerUnicodeCatalog.pages.getOrElse(position) { emptyList() }
        val adapter = UnicodeEmojiPageAdapter(
            emojis = emojis,
            onEmojiClick = { emoji ->
                onEmojiClick(
                    EmojiPickerUnicodeCatalog.group,
                    Emoji(key = emoji, emojiName = emoji)
                )
            },
            onDeleteClick = { onDeleteClick() }
        )
        pageAdapters += adapter

        return RecyclerView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutManager = GridLayoutManager(context, EmojiPickerUnicodeCatalog.COLUMN_COUNT)
            this.adapter = adapter
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            val horizontalPadding = dpToPx(2f).toInt()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            clipToPadding = false
        }
    }

    private fun buildPageIndicator() {
        pageIndicator.removeAllViews()
        repeat(EmojiPickerUnicodeCatalog.pages.size) {
            pageIndicator.addView(
                View(context),
                LinearLayout.LayoutParams(dpToPx(5f).toInt(), dpToPx(5f).toInt()).apply {
                    marginStart = dpToPx(3f).toInt()
                    marginEnd = dpToPx(3f).toInt()
                }
            )
        }
        updatePageIndicator(viewPager.currentItem)
    }

    private fun updatePageIndicator(selectedPage: Int) {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        for (index in 0 until pageIndicator.childCount) {
            pageIndicator.getChildAt(index).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    if (index == selectedPage) {
                        colors.buttonColorPrimaryDefault
                    } else {
                        colors.textColorTertiary
                    }
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope
        scope.launch {
            themeStore.themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorBubbleReciprocal)
        pageIndicator.setBackgroundColor(colors.bgColorBubbleReciprocal)
        pageAdapters.forEach { it.notifyDataSetChanged() }
        updatePageIndicator(viewPager.currentItem)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
