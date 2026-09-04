package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiTextDeletionPolicyTest {
    @Test
    fun previousClusterStart_deletesUnicodeEmojiAsOneVisibleCharacter() {
        assertDeletesLastCluster("A😀", "A")
        assertDeletesLastCluster("A❤️", "A")
        assertDeletesLastCluster("A✌️", "A")
        assertDeletesLastCluster("A👨‍👩‍👧‍👦", "A")
        assertDeletesLastCluster("A👍🏽", "A")
        assertDeletesLastCluster("A🇨🇳", "A")
    }

    @Test
    fun previousClusterStart_keepsEarlierTextAndEmoji() {
        assertDeletesLastCluster("你好😀😁", "你好😀")
        assertDeletesLastCluster("e\u0301x", "e\u0301")
    }

    private fun assertDeletesLastCluster(source: String, expected: String) {
        val start = EmojiTextDeletionPolicy.previousClusterStart(source, source.length)
        assertEquals(expected, source.substring(0, start))
    }
}
