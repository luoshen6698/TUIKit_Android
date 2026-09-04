package io.trtc.tuikit.chat.uikit.components.emojipicker.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.R

internal class UnicodeEmojiPageAdapter(
    private val emojis: List<String>,
    private val onEmojiClick: (String) -> Unit,
    private val onDeleteClick: () -> Unit
) : RecyclerView.Adapter<UnicodeEmojiPageAdapter.CellViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val density = parent.resources.displayMetrics.density
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (CELL_HEIGHT_DP * density).toInt()
            )
        }

        val content = if (viewType == TYPE_DELETE) {
            ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER
                setImageResource(R.drawable.emoji_picker_delete_icon)
                contentDescription = context.getString(R.string.emoji_picker_delete)
            }
        } else {
            TextView(parent.context).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = EMOJI_TEXT_SIZE_SP
            }
        }
        val margin = (CELL_MARGIN_DP * density).toInt()
        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(margin, margin, margin, margin) }
        )
        return CellViewHolder(container, content)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        val context = holder.itemView.context
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        val background = GradientDrawable().apply {
            setColor(colors.bgColorOperate)
            cornerRadius = CORNER_RADIUS_DP * context.resources.displayMetrics.density
        }
        holder.content.background = background

        if (position == emojis.size) {
            val deleteView = holder.content as ImageView
            deleteView.setColorFilter(colors.textColorSecondary)
            holder.itemView.setOnClickListener { onDeleteClick() }
        } else {
            val emoji = emojis[position]
            val emojiView = holder.content as TextView
            emojiView.text = emoji
            emojiView.contentDescription = context.getString(R.string.emoji_picker_insert, emoji)
            holder.itemView.setOnClickListener { onEmojiClick(emoji) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == emojis.size) TYPE_DELETE else TYPE_EMOJI
    }

    override fun getItemCount(): Int = emojis.size + 1

    class CellViewHolder(itemView: View, val content: View) : RecyclerView.ViewHolder(itemView)

    private companion object {
        const val TYPE_EMOJI = 0
        const val TYPE_DELETE = 1
        const val CELL_HEIGHT_DP = 48f
        const val CELL_MARGIN_DP = 4f
        const val CORNER_RADIUS_DP = 12f
        const val EMOJI_TEXT_SIZE_SP = 26f
    }
}
