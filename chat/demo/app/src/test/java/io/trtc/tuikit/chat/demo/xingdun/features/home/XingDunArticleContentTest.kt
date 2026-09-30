package io.trtc.tuikit.chat.demo.xingdun.features.home

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class XingDunArticleContentTest {
    @Test fun homepageDecodesTheServerArticleContract() {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        val home = gson.fromJson("""{"categories":[{"id":2,"category_name":"本地生活"}],"featured":[],"headlines":[],"articles":{"list":[{"id":3,"category_id":2,"category_name":"本地生活","title":"周末指南","author":"运营","image":"","summary":"摘要","update_time":"2026-09-30 12:00:00"}],"page":1,"total":11,"has_more":true}}""", XingDunArticleHome::class.java)
        assertEquals("本地生活", home.categories.single().categoryName)
        assertEquals(2, home.articles.list.single().categoryId)
        assertTrue(home.articles.hasMore)
        assertEquals("2026-09-30 12:00:00", home.articles.list.single().updateTime)
    }

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
