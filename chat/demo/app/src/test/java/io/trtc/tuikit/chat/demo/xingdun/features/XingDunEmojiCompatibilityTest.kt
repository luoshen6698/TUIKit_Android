package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XingDunEmojiCompatibilityTest {
    @Test
    fun `converts official TUIKit tokens to Unicode without changing text`() {
        assertEquals(
            "before 😀 middle 👍 after",
            XingDunEmojiCompatibility.transformOutgoingText(
                "before [TUIEmoji_Smile] middle [TUIEmoji_Like] after"
            )
        )
    }

    @Test
    fun `converts every official built in token`() {
        val officialTokens = listOf(
            "Smile", "Expect", "Blink", "Guffaw", "KindSmile", "Haha", "Cheerful",
            "Speechless", "Amazed", "Sorrow", "Complacent", "Silly", "Lustful", "Giggle",
            "Kiss", "Wail", "TearsLaugh", "Trapped", "Mask", "Fear", "BareTeeth", "FlareUp",
            "Yawn", "Tact", "Stareyes", "ShutUp", "Sigh", "Hehe", "Silent", "Surprised",
            "Askance", "Ok", "Shit", "Monster", "Daemon", "Rage", "Fool", "Pig", "Cow",
            "Ai", "Skull", "Bombs", "Coffee", "Cake", "Beer", "Flower", "Watermelon", "Rich",
            "Heart", "Moon", "Sun", "Star", "RedPacket", "Celebrate", "Bless", "Fortune",
            "Convinced", "Prohibit", "666", "857", "Knife", "Like",
        ).joinToString(separator = "") { "[TUIEmoji_$it]" }

        val transformed = XingDunEmojiCompatibility.transformOutgoingText(officialTokens)

        assertFalse(transformed.contains("[TUIEmoji_"))
    }
}
