package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiPickerUnicodeCatalog
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import io.trtc.tuikit.chat.uikit.components.emojipicker.ui.EmojiPickerView
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelState

internal class MessageInputPanelContentController private constructor(
    private val createEmojiPicker: () -> EmojiPickerView,
    private val createMorePanel: () -> MoreActionsPanelView,
    private val panelContainer: FrameLayout,
    private val currentPanel: () -> PanelState?
) {
    private var emojiPickerView: EmojiPickerView? = null
    private var moreActionsPanel: MoreActionsPanelView? = null
    private var actions: List<MessageInputMenuAction> = emptyList()

    // Closed panels do not belong on the chat-entry inflation path.
    private fun emoji(): EmojiPickerView = emojiPickerView ?: createEmojiPicker().also { emojiPickerView = it }
    private fun more(): MoreActionsPanelView = moreActionsPanel ?: createMorePanel().also {
        moreActionsPanel = it
        it.setActions(actions)
    }

    fun refreshMorePanelActions(actions: List<MessageInputMenuAction>) {
        this.actions = actions.toList()
        moreActionsPanel?.setActions(this.actions)
    }

    fun cancelAnimations() {
        emojiPickerView?.animate()?.cancel()
        moreActionsPanel?.animate()?.cancel()
    }

    fun hidePanelContent() {
        Log.d(TAG, "hidePanelContent: GONE emojiPicker + moreActionsPanel")
        cancelAnimations()
        emojiPickerView?.apply { alpha = 1f; visibility = View.GONE }
        moreActionsPanel?.apply { alpha = 1f; visibility = View.GONE }
    }

    fun showPanelContent(panel: PanelState, crossfade: Boolean = false) {
        Log.d(TAG, "showPanelContent: $panel | crossfade=$crossfade | containerH=${panelContainer.layoutParams?.height}")
        val incoming: View = if (panel == PanelState.EMOJI_PANEL) emoji() else more()
        val outgoing = if (panel == PanelState.EMOJI_PANEL) moreActionsPanel else emojiPickerView

        incoming.animate()?.cancel()
        outgoing?.animate()?.cancel()

        if (crossfade) {
            incoming.alpha = 0f
            incoming.visibility = View.VISIBLE
            incoming.animate().alpha(1f).setDuration(PANEL_ANIM_DURATION_MS).start()
            outgoing?.let { old ->
                old.animate().alpha(0f).setDuration(PANEL_ANIM_DURATION_MS).withEndAction {
                    if (currentPanel() != panelOf(old)) old.visibility = View.GONE
                    old.alpha = 1f
                }.start()
            }
        } else {
            emojiPickerView?.apply { alpha = 1f; visibility = if (panel == PanelState.EMOJI_PANEL) View.VISIBLE else View.GONE }
            moreActionsPanel?.apply { alpha = 1f; visibility = if (panel == PanelState.MORE_PANEL) View.VISIBLE else View.GONE }
        }
        panelContainer.visibility = View.VISIBLE
    }

    private fun panelOf(view: View): PanelState? {
        return when (view) {
            emojiPickerView -> PanelState.EMOJI_PANEL
            moreActionsPanel -> PanelState.MORE_PANEL
            else -> null
        }
    }

    companion object {
        private const val TAG = "MsgInput.PanelContent"
        private const val PANEL_ANIM_DURATION_MS = 250L

        fun attach(
            context: Context,
            panelContainer: FrameLayout,
            editText: AtomicEditText,
            currentPanel: () -> PanelState?,
            onSendClick: () -> Unit,
            onCustomEmojiClick: (EmojiGroup, Emoji) -> Unit = { _, _ -> }
        ): MessageInputPanelContentController {
            // Factories keep panel callbacks intact, but allocate the views only when requested.
            return MessageInputPanelContentController(
                createEmojiPicker = {
                    EmojiPickerView(context).apply {
                        setup(
                            onEmojiClick = { group, emoji ->
                                if (group.id == EmojiPickerUnicodeCatalog.GROUP_ID) {
                                    editText.insertText(emoji.key)
                                } else if (group.isLittleEmoji) {
                                    editText.insertText(emoji.key)
                                    EmojiSpanHelper.processEditTextEmoji(editText)
                                } else {
                                    onCustomEmojiClick(group, emoji)
                                }
                            },
                            onSendClick = onSendClick,
                            onDeleteClick = { editText.deleteAtCursor() }
                        )
                        visibility = View.GONE
                        panelContainer.addView(this, FrameLayout.LayoutParams(-1, -1))
                    }
                },
                createMorePanel = {
                    MoreActionsPanelView(context).apply {
                        visibility = View.GONE
                        panelContainer.addView(this, FrameLayout.LayoutParams(-1, -1))
                    }
                },
                panelContainer = panelContainer,
                currentPanel = currentPanel
            )
        }
    }
}
