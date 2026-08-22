package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAuthResponse
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunCompany
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunIMCredential
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPushConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunTenantBoundaryTest {

    @Test
    fun `session is bound to company id code sdk app id and push configuration`() {
        assertTrue(XingDunTenantBoundary.matches(session("company_a", 1001), enterprise("company_a", 1001)))
        assertFalse(XingDunTenantBoundary.matches(session("company_a", 1001, companyId = 2), enterprise("company_a", 1001)))
        assertFalse(XingDunTenantBoundary.matches(session("company_a", 1001), enterprise("company_b", 1001)))
        assertFalse(XingDunTenantBoundary.matches(session("company_a", 1001), enterprise("company_a", 2002)))
        assertFalse(
            XingDunTenantBoundary.matches(
                session("company_a", 1001).copy(push = XingDunPushConfiguration(businessIdProd = "other")),
                enterprise("company_a", 1001)
            )
        )
    }

    @Test
    fun `bootstrap rejects mismatched company payload`() {
        val invalid = XingDunBootstrapConfiguration(
            configured = true,
            imProvider = "tencent",
            companyCode = "company_a",
            sdkAppId = 1001,
            company = XingDunCompany(id = 1, code = "company_b", name = "B")
        )
        assertTrue(XingDunTenantBoundary.identity(invalid) == null)
        assertTrue(XingDunTenantBoundary.identity(enterprise("company_a", 1001).copy(company = XingDunCompany())) == null)
    }

    @Test
    fun `authentication response cannot cross tenant boundary`() {
        val expected = requireNotNull(XingDunTenantBoundary.identity(enterprise("company_a", 1001)))
        assertTrue(
            XingDunTenantBoundary.responseMatches(
                response("company_a", 1, 1001),
                expected,
                expectedCompanyId = 1
            )
        )
        assertFalse(XingDunTenantBoundary.responseMatches(response("company_b", 1, 1001), expected, 1))
        assertFalse(XingDunTenantBoundary.responseMatches(response("company_a", 1, 2002), expected, 1))
        assertFalse(XingDunTenantBoundary.responseMatches(response("company_a", 2, 1001), expected, 1))
        assertFalse(
            XingDunTenantBoundary.responseMatches(
                response("company_a", 1, 1001).copy(company = null, companyCode = null),
                expected,
                1
            )
        )
    }

    private fun enterprise(companyCode: String, sdkAppId: Int) = XingDunBootstrapConfiguration(
        configured = true,
        imProvider = "tencent",
        companyCode = companyCode,
        sdkAppId = sdkAppId,
        company = XingDunCompany(id = 1, code = companyCode, name = companyCode)
    )

    private fun session(companyCode: String, sdkAppId: Int, companyId: Int = 1) = XingDunStoredSession(
        accessToken = "access",
        tokenType = "Bearer",
        accessExpiresAtMillis = Long.MAX_VALUE,
        refreshToken = "refresh",
        refreshExpiresAtMillis = Long.MAX_VALUE,
        companyCode = companyCode,
        companyId = companyId,
        companyName = companyCode,
        apiBaseUrl = "https://example.com/prod/im/v1",
        sdkAppId = sdkAppId,
        timUserId = "xd_${companyCode}_1",
        userSig = "sig",
        userSigExpiresAtMillis = Long.MAX_VALUE,
        nickname = "User"
    )

    private fun response(companyCode: String, companyId: Int, sdkAppId: Int) = XingDunAuthResponse(
        company = XingDunCompany(id = companyId, code = companyCode, name = companyCode),
        companyCode = companyCode,
        timSdkAppId = sdkAppId,
        imCredential = XingDunIMCredential(sdkAppId = sdkAppId, userId = "xd_${companyCode}_1", userSig = "sig")
    )
}
