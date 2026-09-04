package io.trtc.tuikit.chat.uikit.components.emojipicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPickerUnicodeCatalogTest {
    @Test
    fun pages_matchIosComposerShapeAndOrder() {
        assertEquals(6, EmojiPickerUnicodeCatalog.pages.size)
        assertTrue(
            EmojiPickerUnicodeCatalog.pages.all {
                it.size == EmojiPickerUnicodeCatalog.EMOJI_COUNT_PER_PAGE
            }
        )
        assertEquals("😀", EmojiPickerUnicodeCatalog.pages.first().first())
        assertEquals("🤪", EmojiPickerUnicodeCatalog.pages.first().last())
        assertEquals("🍔", EmojiPickerUnicodeCatalog.pages.last().first())
        assertEquals("🌊", EmojiPickerUnicodeCatalog.pages.last().last())
        assertEquals(
            EmojiPickerUnicodeCatalog.pages.flatten(),
            EmojiPickerUnicodeCatalog.group.emojis.map { it.key }
        )
    }
}
