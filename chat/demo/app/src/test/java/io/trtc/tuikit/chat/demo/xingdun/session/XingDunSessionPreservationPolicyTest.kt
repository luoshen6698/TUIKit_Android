package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunCompany
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class XingDunSessionPreservationPolicyTest {
    @Test
    fun unreadableEnterpriseDoesNotExposeOrDeleteRetainedSession() {
        var cleared = false
        assertNull(XingDunSessionBindingPolicy.resolve(session(), null) { cleared = true })
        assertEquals(false, cleared)
    }

    @Test
    fun incompleteEnterpriseDoesNotExposeOrDeleteRetainedSession() {
        var cleared = false
        val incomplete = enterprise().copy(company = null)
        assertNull(XingDunSessionBindingPolicy.resolve(session(), incomplete) { cleared = true })
        assertEquals(false, cleared)
    }

    @Test
    fun unreadableOrLegacySessionIsNotDeletedWhileReadingTenantBinding() {
        listOf(null, session().copy(tenantSchemaVersion = 0)).forEach { stored ->
            var cleared = false
            assertNull(XingDunSessionBindingPolicy.resolve(stored, enterprise()) { cleared = true })
            assertEquals(false, cleared)
        }
    }

    @Test
    fun confirmedTenantMismatchStillRejectsAndClearsSession() {
        listOf(
            enterprise().copy(company = XingDunCompany(id = 2, code = "xc2026", name = "Other")),
            enterprise().copy(sdkAppId = 2000),
            enterprise().copy(companyCode = "other", company = XingDunCompany(id = 1, code = "other", name = "Other")),
        ).forEach { selected ->
            var clears = 0
            assertNull(XingDunSessionBindingPolicy.resolve(session(), selected) { clears++ })
            assertEquals(1, clears)
        }
    }

    @Test
    fun matchingEnterpriseReturnsOriginalSessionWithoutClearing() {
        val stored = session()
        var clears = 0
        assertSame(stored, XingDunSessionBindingPolicy.resolve(stored, enterprise()) { clears++ })
        assertEquals(0, clears)
    }

    @Test
    fun queuedOldRejectionCannotLogOutNewLoginOfSameAccount() {
        val rejected = session()
        var current = rejected
        var rejects = 0
        val postedAction = {
            XingDunSessionRejectionPolicy.rejectIfCurrent(rejected, { current }) { rejects++ }
        }
        current = rejected.copy(accessToken = "replacement-access")
        postedAction()
        assertEquals(0, rejects)
    }

    @Test
    fun queuedOldRejectionCannotLogOutDifferentAccountOrTenant() {
        val rejected = session()
        listOf(
            rejected.copy(timUserId = "another-user"),
            rejected.copy(companyId = 2),
            rejected.copy(companyCode = "other"),
            rejected.copy(sdkAppId = 2000),
        ).forEach { replacement ->
            var rejects = 0
            XingDunSessionRejectionPolicy.rejectIfCurrent(rejected, { replacement }) { rejects++ }
            assertEquals(0, rejects)
        }
    }

    @Test
    fun authoritativeRejectionStillRejectsMatchingCurrentCredential() {
        val rejected = session()
        var rejects = 0
        XingDunSessionRejectionPolicy.rejectIfCurrent(rejected, { rejected }) { rejects++ }
        assertEquals(1, rejects)
        XingDunSessionRejectionPolicy.rejectIfCurrent(rejected, { null }) { rejects++ }
        assertEquals(1, rejects)
    }

    private fun enterprise() = XingDunBootstrapConfiguration(
        configured = true,
        imProvider = "tencent",
        companyCode = "xc2026",
        sdkAppId = 1000,
        company = XingDunCompany(id = 1, code = "xc2026", name = "XingDun"),
    )

    private fun session() = XingDunStoredSession(
        accessToken = "access",
        tokenType = "Bearer",
        accessExpiresAtMillis = Long.MAX_VALUE,
        refreshToken = "refresh",
        refreshExpiresAtMillis = Long.MAX_VALUE,
        companyCode = "xc2026",
        companyId = 1,
        companyName = "XingDun",
        apiBaseUrl = "https://example.com/prod/im/v1",
        sdkAppId = 1000,
        timUserId = "user",
        userSig = "sig",
        userSigExpiresAtMillis = Long.MAX_VALUE,
        nickname = "User",
    )
}
