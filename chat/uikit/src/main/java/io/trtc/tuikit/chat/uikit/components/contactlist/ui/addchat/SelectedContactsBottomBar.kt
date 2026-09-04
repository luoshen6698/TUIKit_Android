package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar

internal class SelectedContactsBottomBar(
    context: Context,
    selectedContacts: List<ContactInfo>,
    private val lockedUserIDs: Set<String>,
    private val onContactRemove: (ContactInfo) -> Unit,
) : LinearLayout(context) {

    private val selectedAvatarsScrollView: HorizontalScrollView
    private val selectedAvatarsContainer: LinearLayout

    init {
        val colors = getColors()
        val dm = context.resources.displayMetrics

        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setBackgroundColor(colors.bgColorOperate)
        setPadding(
            dp2px(12f, dm).toInt(),
            dp2px(8f, dm).toInt(),
            dp2px(12f, dm).toInt(),
            dp2px(8f, dm).toInt(),
        )

        selectedAvatarsScrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        selectedAvatarsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selectedAvatarsScrollView.addView(selectedAvatarsContainer)
        addView(selectedAvatarsScrollView)
        addView(View(context).apply {
            setBackgroundColor(colors.strokeColorSecondary)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp2px(0.5f, dm).toInt().coerceAtLeast(1),
            ).apply {
                topMargin = dp2px(8f, dm).toInt()
            }
        })

        update(selectedContacts)
    }

    fun update(selectedContacts: List<ContactInfo>) {
        visibility = if (selectedContacts.isEmpty()) View.GONE else View.VISIBLE
        populateSelectedAvatars(selectedContacts)
    }

    private fun populateSelectedAvatars(selectedContacts: List<ContactInfo>) {
        val dm = context.resources.displayMetrics
        selectedAvatarsContainer.removeAllViews()

        val itemSize = dp2px(48f, dm).toInt()
        val avatarSize = dp2px(40f, dm).toInt()
        val removeSize = dp2px(18f, dm).toInt()
        val itemSpacing = dp2px(8f, dm).toInt()

        selectedContacts.forEachIndexed { index, contact ->
            val item = FrameLayout(context).apply {
                layoutParams = LayoutParams(itemSize, itemSize).apply {
                    if (index > 0) marginStart = itemSpacing
                }
            }
            val avatarView = Avatar(context).apply {
                layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.START or Gravity.BOTTOM)
                setSize(Avatar.AvatarSize.L)
                setShape(Avatar.AvatarShape.RoundRectangle)
                val avatarUrl = contact.avatarURL
                if (avatarUrl.isNullOrEmpty()) {
                    setContent(Avatar.AvatarContent.Text(contact.displayName))
                } else {
                    setContent(Avatar.AvatarContent.Image(avatarUrl, contact.displayName))
                }
            }
            item.addView(avatarView)

            if (contact.userID !in lockedUserIDs) {
                item.addView(TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(removeSize, removeSize, Gravity.END or Gravity.TOP)
                    gravity = Gravity.CENTER
                    text = "×"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.rgb(216, 67, 67))
                    }
                    contentDescription = context.getString(
                        R.string.contact_list_remove_selected_contact,
                        contact.displayName,
                    )
                    setOnClickListener { onContactRemove(contact) }
                })
            }
            selectedAvatarsContainer.addView(item)
        }

        selectedAvatarsScrollView.post {
            selectedAvatarsScrollView.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
