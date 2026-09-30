package io.trtc.tuikit.chat.demo.xingdun.features.home

import org.junit.Assert.*
import org.junit.Test

class XingDunArticleContentTest {
    @Test fun richTextLinksOnlyOpenSafeWebAddresses() {
        assertEquals("https://api.example.com/storage/a.jpg", XingDunArticleContent.safeUrl("/storage/a.jpg", "https://api.example.com/prod/im/v1"))
        for (url in listOf("javascript:alert(1)", "file:///a", "content://a", "//evil.test/a", "https://user:pass@evil.test/a", "https://evil.test\\a", "http://example.com/a")) {
            assertNull(url, XingDunArticleContent.safeUrl(url))
        }
    }

    @Test fun articleTitlesCannotBecomeHtml() {
        assertEquals("&lt;img src=&quot;x&quot;&gt;&amp;", XingDunArticleContent.escape("<img src=\"x\">&"))
    }

    @Test fun newPagesDoNotDuplicateAnArticleAlreadyDisplayed() {
        val first = XingDunArticle(id = 1, title = "First")
        val second = XingDunArticle(id = 2, title = "Second")
        assertEquals(listOf(first, second), XingDunArticleContent.append(listOf(first), listOf(first, second)))
    }
}
