package io.trtc.tuikit.chat.uikit.components.emojipicker

import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup

/** Unicode emoji pages kept in the same order as the XingDun iOS composer. */
internal object EmojiPickerUnicodeCatalog {
    const val GROUP_ID = "xingdun_unicode_emoji"
    const val COLUMN_COUNT = 7
    const val EMOJI_COUNT_PER_PAGE = 27

    val pages: List<List<String>> = listOf(
        listOf(
            "😀", "😁", "😂", "😃", "😄", "😅", "😆",
            "😉", "😊", "😋", "😎", "😍", "😘", "😗",
            "😙", "😚", "😇", "🙂", "🤗", "🤩", "🥳",
            "😏", "😌", "😛", "😜", "😝", "🤪"
        ),
        listOf(
            "🤔", "🤨", "😐", "😑", "😶", "🙄", "😬",
            "😴", "😪", "🤤", "😷", "🤒", "🤕", "🤢",
            "🤮", "🥵", "🥶", "😵", "😰", "😨", "😱",
            "😢", "😭", "😤", "😠", "😡", "😲"
        ),
        listOf(
            "👍", "👎", "👏", "🙌", "🙏", "👌", "✌️",
            "🤞", "🤟", "🤘", "👊", "✊", "🤝", "💪",
            "👋", "🤚", "✋", "🖐️", "❤️", "🩷", "🧡",
            "💛", "💚", "💙", "💜", "🖤", "💔"
        ),
        listOf(
            "🎉", "🎊", "🎁", "🎈", "🌹", "🌺", "🌸",
            "🌻", "🌼", "🌷", "🔥", "⭐", "🌟", "✨",
            "💫", "💯", "☀️", "🌈", "☕", "🎂", "🍰",
            "🍻", "🥂", "🎵", "🎶", "🎮", "📷"
        ),
        listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻",
            "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸",
            "🐵", "🙈", "🙉", "🙊", "🐔", "🐧", "🐦",
            "🐤", "🐙", "🐠", "🍎", "🍉", "🍓"
        ),
        listOf(
            "🍔", "🍟", "🍕", "🌭", "🍿", "🍜", "🍣",
            "🍤", "🍩", "🍪", "🍫", "🍬", "🍭", "🚗",
            "🚕", "🚄", "✈️", "🚀", "🚲", "🚌", "🌍",
            "🌙", "⛅️", "⚡️", "❄️", "☔️", "🌊"
        )
    )

    val group = EmojiGroup(
        id = GROUP_ID,
        name = "UnicodeEmoji",
        emojiGroupIconUrl = "",
        emojis = pages.flatten().map { emoji -> Emoji(emoji, emoji) },
        isLittleEmoji = true,
        supportReaction = false
    )
}
