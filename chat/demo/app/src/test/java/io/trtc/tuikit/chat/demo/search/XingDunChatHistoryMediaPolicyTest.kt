package io.trtc.tuikit.chat.demo.search

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunChatHistoryMediaPolicyTest {
    @Test
    fun `uses existing local thumbnail before remote source`() {
        val source = XingDunChatHistoryMediaPolicy.firstUsableSource(
            "/cache/thumb.jpg",
            "https://example.com/thumb.jpg",
            localFileExists = { it == "/cache/thumb.jpg" },
        )

        assertEquals("/cache/thumb.jpg", source)
    }

    @Test
    fun `skips stale local thumbnail and falls back to remote source`() {
        val source = XingDunChatHistoryMediaPolicy.firstUsableSource(
            "/cache/missing.jpg",
            "https://example.com/thumb.jpg",
            localFileExists = { false },
        )

        assertEquals("https://example.com/thumb.jpg", source)
    }

    @Test
    fun `accepts content uri without file-system lookup`() {
        val source = XingDunChatHistoryMediaPolicy.firstUsableSource(
            "content://media/external/images/1",
            localFileExists = { error("content URI must not use file lookup") },
        )

        assertEquals("content://media/external/images/1", source)
    }

    @Test
    fun `accepts existing file uri`() {
        val file = File.createTempFile("history-media-", ".jpg")
        val uri = "file://${file.absolutePath}"
        try {
            assertEquals(uri, XingDunChatHistoryMediaPolicy.firstUsableSource(uri))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `returns null when every local source is stale`() {
        val source = XingDunChatHistoryMediaPolicy.firstUsableSource(
            "",
            "/cache/missing.jpg",
            null,
            localFileExists = { false },
        )

        assertNull(source)
    }
}
