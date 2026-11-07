package io.trtc.tuikit.chat.demo.xingdun.session

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunServerAddressMigrationTest {
    @Test fun retiredApiMovesToNewHttpsOrigin() {
        for (value in listOf("https://api.xingdunim.com/prod/im/v1", "https://API.XINGDUNIM.COM:443/prod/im/v1/", "http://api.xingdunim.com/prod/im/v1")) {
            assertEquals("https://api.xuyinfang365.bid/prod/im/v1", XingDunServerAddressMigration.api(value))
        }
    }
    @Test fun tenantAndAmbiguousUrlsAreNeverRedirected() {
        for (value in listOf("https://tenant.example/prod/im/v1", "https://api.xingdunim.com.evil.test/prod/im/v1",
            "https://api.xingdunim.com@evil.test/prod/im/v1", "https://user@api.xingdunim.com/prod/im/v1",
            "https://api.xingdunim.com:8443/prod/im/v1", "https://api.xingdunim.com/other", "https://api.xingdunim.com/prod/im/v1?token=x")) {
            assertEquals(value, XingDunServerAddressMigration.api(value))
        }
    }
    @Test fun cachedLegalLinksMoveWithoutChangingDocumentPath() {
        assertEquals("https://www.xuyinfang365.bid/xingdun-privacy-policy-android.html",
            XingDunServerAddressMigration.website("https://www.xingdunim.com/xingdun-privacy-policy-android.html"))
        val newUrl = "https://www.xuyinfang365.bid/xingdun-user-agreement-android.html"
        assertEquals(newUrl, XingDunServerAddressMigration.website(newUrl))
    }
}
