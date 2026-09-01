package io.trtc.tuikit.chat.demo.xingdun.features

/**
 * Keeps the official TUIKit emoji picker and legacy token rendering while using
 * Unicode on the wire, matching the iOS composer and every standards-based IM client.
 */
object XingDunEmojiCompatibility {
    private val outgoingReplacements = linkedMapOf(
        "[TUIEmoji_Smile]" to "😀",
        "[TUIEmoji_Expect]" to "🤗",
        "[TUIEmoji_Blink]" to "😉",
        "[TUIEmoji_Guffaw]" to "😂",
        "[TUIEmoji_KindSmile]" to "😊",
        "[TUIEmoji_Haha]" to "😄",
        "[TUIEmoji_Cheerful]" to "😁",
        "[TUIEmoji_Speechless]" to "😶",
        "[TUIEmoji_Amazed]" to "😲",
        "[TUIEmoji_Sorrow]" to "😢",
        "[TUIEmoji_Complacent]" to "😏",
        "[TUIEmoji_Silly]" to "😜",
        "[TUIEmoji_Lustful]" to "😍",
        "[TUIEmoji_Giggle]" to "🤭",
        "[TUIEmoji_Kiss]" to "😘",
        "[TUIEmoji_Wail]" to "😭",
        "[TUIEmoji_TearsLaugh]" to "🤣",
        "[TUIEmoji_Trapped]" to "😖",
        "[TUIEmoji_Mask]" to "😷",
        "[TUIEmoji_Fear]" to "😱",
        "[TUIEmoji_BareTeeth]" to "😬",
        "[TUIEmoji_FlareUp]" to "😡",
        "[TUIEmoji_Yawn]" to "🥱",
        "[TUIEmoji_Tact]" to "😌",
        "[TUIEmoji_Stareyes]" to "🤩",
        "[TUIEmoji_ShutUp]" to "🤐",
        "[TUIEmoji_Sigh]" to "😮‍💨",
        "[TUIEmoji_Hehe]" to "😅",
        "[TUIEmoji_Silent]" to "😑",
        "[TUIEmoji_Surprised]" to "😮",
        "[TUIEmoji_Askance]" to "🙄",
        "[TUIEmoji_Ok]" to "👌",
        "[TUIEmoji_Shit]" to "💩",
        "[TUIEmoji_Monster]" to "👾",
        "[TUIEmoji_Daemon]" to "👿",
        "[TUIEmoji_Rage]" to "😤",
        "[TUIEmoji_Fool]" to "🤡",
        "[TUIEmoji_Pig]" to "🐷",
        "[TUIEmoji_Cow]" to "🐮",
        "[TUIEmoji_Ai]" to "🤖",
        "[TUIEmoji_Skull]" to "💀",
        "[TUIEmoji_Bombs]" to "💣",
        "[TUIEmoji_Coffee]" to "☕",
        "[TUIEmoji_Cake]" to "🎂",
        "[TUIEmoji_Beer]" to "🍻",
        "[TUIEmoji_Flower]" to "🌹",
        "[TUIEmoji_Watermelon]" to "🍉",
        "[TUIEmoji_Rich]" to "🤑",
        "[TUIEmoji_Heart]" to "❤️",
        "[TUIEmoji_Moon]" to "🌙",
        "[TUIEmoji_Sun]" to "☀️",
        "[TUIEmoji_Star]" to "⭐",
        "[TUIEmoji_RedPacket]" to "🧧",
        "[TUIEmoji_Celebrate]" to "🎉",
        "[TUIEmoji_Bless]" to "🙏",
        "[TUIEmoji_Fortune]" to "💰",
        "[TUIEmoji_Convinced]" to "🤝",
        "[TUIEmoji_Prohibit]" to "🚫",
        "[TUIEmoji_666]" to "💯",
        "[TUIEmoji_857]" to "🎵",
        "[TUIEmoji_Knife]" to "🔪",
        "[TUIEmoji_Like]" to "👍",
    )

    fun transformOutgoingText(text: String): String {
        var transformed = text
        outgoingReplacements.forEach { (token, unicode) ->
            transformed = transformed.replace(token, unicode)
        }
        return transformed
    }
}
