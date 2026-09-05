package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class GroupMemberPreviewSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerRow: SettingRowNavigate
    private val gridContainer: LinearLayout
    private val row1: LinearLayout
    private val row2: LinearLayout

    private var currentMembers: List<GroupMember> = emptyList()
    private var showAddButton: Boolean = false
    private var viewScope: CoroutineScope? = null

    var onHeaderClick: (() -> Unit)? = null
    var onMemberClick: ((GroupMember) -> Unit)? = null
    var onAddClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        headerRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_members))
            setShowArrow(true)
            setOnClickListener { onHeaderClick?.invoke() }
        }
        addView(
            headerRow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        gridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val horizontalPadding = dp2px(16f, resources.displayMetrics).toInt()
            setPadding(
                horizontalPadding,
                dp2px(4f, resources.displayMetrics).toInt(),
                horizontalPadding,
                dp2px(16f, resources.displayMetrics).toInt()
            )
        }
        row1 = createRow()
        row2 = createRow().apply {
            val topMargin = dp2px(12f, resources.displayMetrics).toInt()
            (layoutParams as LayoutParams).topMargin = topMargin
        }
        gridContainer.addView(row1)
        gridContainer.addView(row2)
        addView(
            gridContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun updateContent(
        members: List<GroupMember>,
        memberCount: Int,
        showAddButton: Boolean
    ) {
        currentMembers = members
        this.showAddButton = showAddButton
        headerRow.setTitle(context.getString(R.string.chat_setting_group_members_count, memberCount))
        headerRow.setValue("")
        renderPreviewItems()
    }

    private fun createRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun renderPreviewItems() {
        row1.removeAllViews()
        row2.removeAllViews()

        val items = mutableListOf<View>()
        if (showAddButton) {
            items.add(createInviteItem())
        }
        currentMembers.take(MEMBER_PREVIEW_COUNT).forEach { member ->
            items.add(createMemberItem(member))
        }

        items.forEachIndexed { index, item ->
            val target = if (index < COLUMNS_PER_ROW) row1 else row2
            target.addView(wrapInSlot(item))
        }

        val firstRowCount = minOf(items.size, COLUMNS_PER_ROW)
        repeat(COLUMNS_PER_ROW - firstRowCount) {
            row1.addView(createPlaceholderSlot())
        }

        val secondRowCount = (items.size - COLUMNS_PER_ROW).coerceAtLeast(0)
        if (secondRowCount > 0) {
            row2.visibility = View.VISIBLE
            repeat(COLUMNS_PER_ROW - secondRowCount) {
                row2.addView(createPlaceholderSlot())
            }
        } else {
            row2.visibility = View.GONE
        }
    }

    private fun wrapInSlot(item: View): View {
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(
                item,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
        }
    }

    private fun createPlaceholderSlot(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
    }

    private fun createMemberItem(member: GroupMember): View {
        val colors = getColors()
        val dm = resources.displayMetrics
        val itemWidth = dp2px(40f, dm).toInt()
        val avatarSize = dp2px(40f, dm).toInt()
        val displayName = member.displayName

        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(itemWidth, LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { onMemberClick?.invoke(member) }

            addView(
                Avatar(context).apply {
                    layoutParams = LayoutParams(avatarSize, avatarSize)
                    setContent(
                        Avatar.AvatarContent.Image(
                            url = member.avatarURL,
                            fallbackName = displayName
                        )
                    )
                }
            )

            addView(
                TextView(context).apply {
                    text = displayName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(colors.textColorPrimary)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp2px(3f, dm).toInt()
                    }
                }
            )
        }
    }

    private fun createInviteItem(): View {
        val colors = getColors()
        val dm = resources.displayMetrics
        val itemWidth = dp2px(40f, dm).toInt()
        val iconSize = dp2px(40f, dm).toInt()
        val iconCornerRadius = dp2px(4f, dm)
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(itemWidth, LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onAddClick?.invoke()
            }

            addView(
                FrameLayout(context).apply {
                    layoutParams = LayoutParams(iconSize, iconSize)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(colors.bgColorInput)
                        cornerRadius = iconCornerRadius
                    }
                    addView(
                        android.widget.ImageView(context).apply {
                            setImageResource(R.drawable.chat_setting_invite_member_icon)
                            imageTintList = android.content.res.ColorStateList.valueOf(colors.textColorSecondary)
                            scaleType = android.widget.ImageView.ScaleType.CENTER
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    )
                }
            )

            addView(TextView(context).apply {
                text = context.getString(R.string.chat_setting_invite)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(colors.textColorSecondary)
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp2px(3f, dm).toInt() }
            })
        }
    }

    private fun applyThemeColors(colors: ColorTokens) {
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(colors.bgColorOperate)
            cornerRadius = dp2px(18f, resources.displayMetrics)
        }
        clipToOutline = true
        gridContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        row1.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        row2.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        renderPreviewItems()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private companion object {
        const val COLUMNS_PER_ROW = 5
        const val MEMBER_PREVIEW_COUNT = 4
    }
}
