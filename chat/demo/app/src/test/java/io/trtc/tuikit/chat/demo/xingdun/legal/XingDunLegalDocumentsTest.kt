package io.trtc.tuikit.chat.demo.xingdun.legal

import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class XingDunLegalDocumentsTest {
    @Test
    fun androidEntryPointsUseSeparateChineseWebsiteDocuments() {
        assertEquals("https://www.xuyinfang365.bid/xingdun-privacy-policy-android.html", XingDunLegalDocuments.url(true))
        assertEquals("https://www.xuyinfang365.bid/xingdun-user-agreement-android.html", XingDunLegalDocuments.url(false))
        assertEquals("2026.10.01", XingDunSessionManager.USER_AGREEMENT_VERSION)
        assertEquals("2026.11.16", XingDunSessionManager.PRIVACY_VERSION)
        assertEquals(XingDunLegalDocuments.USER_AGREEMENT_VERSION, XingDunSessionManager.USER_AGREEMENT_VERSION)
        assertEquals(XingDunLegalDocuments.PRIVACY_POLICY_VERSION, XingDunSessionManager.PRIVACY_VERSION)
        assertEquals(XingDunLegalDocuments.PRIVACY_POLICY_VERSION, XingDunPrivacyConsentStore.CURRENT_VERSION)
    }

    @Test
    fun completeAndroidDocumentsArePackagedForReadingBeforeNetworkAccess() {
        for (privacy in listOf(false, true)) {
            val html = File("src/main/assets/" + XingDunLegalDocuments.assetPath(privacy)).readText()
            val title = if (privacy) "叙音坊隐私政策" else "叙音坊用户协议"
            assertTrue(html.contains("<h1>$title</h1>"))
            assertTrue(html.contains("鹰潭得时利商贸有限公司"))
            assertTrue(html.contains("id=\"section-${if (privacy) 9 else 10}\""))
            assertTrue(!Regex("<(script|link|img)\\b", RegexOption.IGNORE_CASE).containsMatchIn(html))
            if (privacy) assertTrue(html.contains(XingDunLegalDocuments.PRIVACY_POLICY_VERSION.replace('.', '-')))
        }
    }

    @Test
    fun documentLinksAndTableOfContentsStayInsideTheReader() {
        assertEquals(true, XingDunLegalDocuments.documentType(XingDunLegalDocuments.url(true)))
        assertEquals(false, XingDunLegalDocuments.documentType(XingDunLegalDocuments.url(false)))
        assertEquals(true, XingDunLegalDocuments.documentType(XingDunLegalDocuments.url(true) + "#section-5"))
        assertEquals(false, XingDunLegalDocuments.documentType(XingDunLegalDocuments.url(false) + "#section-12"))
    }

    @Test
    fun iosAndUntrustedDestinationsCannotReplaceAndroidDocuments() {
        for (value in listOf(
            "https://www.xuyinfang365.bid/xingdun-privacy-policy-en.html",
            "http://www.xuyinfang365.bid/xingdun-privacy-policy-android.html",
            "https://www.xuyinfang365.bid.evil.test/xingdun-privacy-policy-android.html",
            "https://www.xuyinfang365.bid@evil.test/xingdun-privacy-policy-android.html",
            XingDunLegalDocuments.url(true) + "?redirect=https://evil.test",
            "file:///android_asset/legal/xingdun-privacy-policy-android.html",
            "javascript:alert(1)"
        )) assertNull(value, XingDunLegalDocuments.documentType(value))
    }
}
