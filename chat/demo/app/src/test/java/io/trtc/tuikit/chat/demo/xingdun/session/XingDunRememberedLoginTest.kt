package io.trtc.tuikit.chat.demo.xingdun.session

import org.junit.Assert.*
import org.junit.Test

class XingDunRememberedLoginTest {
    private val scope = rememberedLoginScope("1:company:123", "https://api.example.test/prod/im/v1")!!
    private val record = XingDunRememberedLogin(scope, "test_account", "fixture_password", "terms1|privacy1")

    @Test fun sameTenantAndEndpointCanRestoreValidatedCredentials() {
        assertEquals(record, record.forScope(scope))
        assertTrue(record.canReuseConsent("terms1|privacy1"))
    }

    @Test fun differentCompanySdkOrServerNeverReceivesSavedPassword() {
        for (other in listOf(
            rememberedLoginScope("2:other:123", "https://api.example.test/prod/im/v1"),
            rememberedLoginScope("1:company:456", "https://api.example.test/prod/im/v1"),
            rememberedLoginScope("1:company:123", "https://other.example.test/prod/im/v1"),
            rememberedLoginScope("1:company:123", "https://api.example.test/other/im/v1"),
            null,
        )) assertNull(record.forScope(other))
    }

    @Test fun endpointNormalizationPreservesSecurityBoundary() {
        assertEquals(scope, rememberedLoginScope("1:company:123", "HTTPS://API.EXAMPLE.TEST:443/prod/im/v1/"))
        assertNotEquals(scope, rememberedLoginScope("1:company:123", "https://api.example.test/PROD/im/v1"))
    }

    @Test fun unsafeOrIncompleteEndpointsCannotSelectCredentials() {
        for (url in listOf("http://api.example.test", "https://user@api.example.test", "https://api.example.test?a=1", "https://api.example.test#fragment", "invalid", "")) {
            assertNull(rememberedLoginScope("tenant", url))
        }
        assertNull(rememberedLoginScope(null, "https://api.example.test"))
    }

    @Test fun passwordChangeKeepsAccountButForgetsPasswordAndConsentAutofill() {
        val cleared = record.withoutPassword()
        assertEquals("test_account", cleared.account)
        assertEquals("", cleared.password)
        assertFalse(cleared.canReuseConsent("terms1|privacy1"))
    }

    @Test fun changedDocumentsRequireConsentAgain() {
        assertFalse(record.canReuseConsent("terms2|privacy1"))
        assertFalse(record.canReuseConsent("terms1|privacy2"))
    }

    @Test fun incompleteRecordsFailClosedWithoutCrashingLoginForm() {
        assertNull(record.copy(account = null).forScope(scope))
        assertNull(record.copy(scope = null).forScope(scope))
        assertFalse(record.copy(password = null).canReuseConsent("terms1|privacy1"))
        assertFalse(record.copy(consentVersion = null).canReuseConsent("terms1|privacy1"))
    }

    @Test fun diagnosticsNeverExposeStoredAccountOrPassword() {
        assertFalse(record.toString().contains("test_account"))
        assertFalse(record.toString().contains("fixture_password"))
    }
}
