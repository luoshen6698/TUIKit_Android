package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import org.junit.Assert.*
import org.junit.Test

class XingDunUiSessionCacheTest {
    private fun session(user: String = "first") = XingDunStoredSession(
        accessToken = "fixture-access", tokenType = "Bearer", accessExpiresAtMillis = 1000,
        companyCode = "test", companyId = 1, companyName = "Fixture",
        apiBaseUrl = "https://example.test/api", sdkAppId = 123, timUserId = user,
        userSig = "fixture-sig", userSigExpiresAtMillis = 1000, username = user, nickname = user,
    )

    @Test fun repeatedUiReadsDoNotDecryptAgain() {
        var reads = 0
        val cache = XingDunUiSessionCache({ XingDunSessionReadStamp(1, 1) }) { reads++; session() }
        repeat(1000) { assertEquals("first", cache.current()?.timUserId) }
        assertEquals(1, reads)
    }

    @Test fun tokenRotationRefreshesDisplayButKeepsBusinessScope() {
        var revision = 1L
        var current = session()
        val cache = XingDunUiSessionCache({ XingDunSessionReadStamp(revision, 1) }) { current }
        val scope = cache.current()!!.scopeKey
        revision++
        current = current.copy(accessToken = "rotated-fixture", nickname = "Updated")
        assertEquals("Updated", cache.current()?.nickname)
        assertEquals(scope, cache.current()?.scopeKey)
    }

    @Test fun accountAndAuthenticationChangesCannotReuseOldDisplay() {
        var stamp = XingDunSessionReadStamp(1, 1)
        var current = session()
        val cache = XingDunUiSessionCache({ stamp }) { current }
        val oldScope = cache.current()!!.scopeKey
        stamp = XingDunSessionReadStamp(2, 2)
        current = session("second").copy(companyId = 2)
        assertEquals("second", cache.current()?.timUserId)
        assertNotEquals(oldScope, cache.current()?.scopeKey)
    }

    @Test fun writeInProgressAndLogoutHideCachedIdentity() {
        var stamp: XingDunSessionReadStamp? = XingDunSessionReadStamp(1, 1)
        var current: XingDunStoredSession? = session()
        val cache = XingDunUiSessionCache({ stamp }) { current }
        assertNotNull(cache.current())
        stamp = null
        assertNull(cache.current())
        stamp = XingDunSessionReadStamp(2, 1)
        current = null
        assertNull(cache.current())
    }

    @Test fun readRacingWithAuthenticationCannotPublishMixedIdentity() {
        var stamp = XingDunSessionReadStamp(1, 1)
        var race = true
        val cache = XingDunUiSessionCache({ stamp }) {
            if (race) { stamp = XingDunSessionReadStamp(2, 2); race = false }
            session("second")
        }
        assertNull(cache.current())
        assertEquals("second", cache.current()?.timUserId)
        assertEquals(2L, cache.current()?.authenticationGeneration)
    }
    @Test fun invalidTenantIdentityCannotEnterDisplayCache() {
        val cache = XingDunUiSessionCache({ XingDunSessionReadStamp(1, 1) }) {
            session().copy(tenantSchemaVersion = 1)
        }
        assertNull(cache.current())
    }

    @Test fun displayScopeUsesTheExistingDurableMessageNamespace() {
        val current = session().copy(apiBaseUrl = "https://example.test/api/", companyCode = "TEST")
        val cache = XingDunUiSessionCache({ XingDunSessionReadStamp(1, 3) }) { current }
        assertEquals(
            io.trtc.tuikit.chat.demo.xingdun.features.XingDunC2CDeletionRepository.accountKey(current),
            cache.current()?.accountKey,
        )
        assertEquals("1:test:123", cache.current()?.tenantKey)
    }

}
