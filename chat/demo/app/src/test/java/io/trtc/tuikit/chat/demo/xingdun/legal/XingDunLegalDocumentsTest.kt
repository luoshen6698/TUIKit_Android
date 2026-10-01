package io.trtc.tuikit.chat.demo.xingdun.legal

import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunLegalDocumentsTest {
    @Test
    fun androidEntryPointsUseSeparateChineseWebsiteDocuments() {
        assertEquals("https://www.xingdunim.com/xingdun-privacy-policy-android.html", XingDunLegalDocuments.url(true))
        assertEquals("https://www.xingdunim.com/xingdun-user-agreement-android.html", XingDunLegalDocuments.url(false))
        assertEquals(XingDunLegalDocuments.VERSION, XingDunSessionManager.PRIVACY_VERSION)
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
            "https://www.xingdunim.com/xingdun-privacy-policy-en.html",
            "http://www.xingdunim.com/xingdun-privacy-policy-android.html",
            "https://www.xingdunim.com.evil.test/xingdun-privacy-policy-android.html",
            "https://www.xingdunim.com@evil.test/xingdun-privacy-policy-android.html",
            XingDunLegalDocuments.url(true) + "?redirect=https://evil.test",
            "file:///android_asset/legal/xingdun-privacy-policy-android.html",
            "javascript:alert(1)"
        )) assertNull(value, XingDunLegalDocuments.documentType(value))
    }
}
