package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAuthResponse
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import java.util.Locale

/** The client-side tenant boundary is the server tenant ID, public code, and Tencent SDKAppID. */
internal data class XingDunTenantIdentity(
    val companyId: Int,
    val companyCode: String,
    val sdkAppId: Int,
) {
    val key: String = "$companyId:${companyCode.lowercase(Locale.ROOT)}:$sdkAppId"

    fun matches(other: XingDunTenantIdentity): Boolean =
        companyId == other.companyId &&
            companyCode.equals(other.companyCode, ignoreCase = true) &&
            sdkAppId == other.sdkAppId
}

internal object XingDunTenantBoundary {
    fun identity(configuration: XingDunBootstrapConfiguration?): XingDunTenantIdentity? {
        configuration ?: return null
        val code = configuration.companyCode.trim()
        val companyCode = configuration.company?.code?.trim().orEmpty()
        if (code.isEmpty() || companyCode.isEmpty() || !code.equals(companyCode, ignoreCase = true)) return null
        val companyId = configuration.company?.id?.takeIf { it > 0 } ?: return null
        if (configuration.sdkAppId <= 0) return null
        return XingDunTenantIdentity(companyId, code.lowercase(Locale.ROOT), configuration.sdkAppId)
    }

    fun identity(session: XingDunStoredSession?): XingDunTenantIdentity? {
        session ?: return null
        if (session.tenantSchemaVersion != CURRENT_SCHEMA_VERSION) return null
        val code = session.companyCode.trim()
        val companyId = session.companyId?.takeIf { it > 0 } ?: return null
        if (code.isEmpty() || session.sdkAppId <= 0) return null
        return XingDunTenantIdentity(companyId, code.lowercase(Locale.ROOT), session.sdkAppId)
    }

    fun matches(session: XingDunStoredSession?, configuration: XingDunBootstrapConfiguration?): Boolean {
        val sessionIdentity = identity(session) ?: return false
        val enterpriseIdentity = identity(configuration) ?: return false
        return sessionIdentity.matches(enterpriseIdentity)
    }

    fun responseMatches(
        response: XingDunAuthResponse,
        expected: XingDunTenantIdentity,
        expectedCompanyId: Int?,
    ): Boolean {
        val responseCode = response.company?.code?.trim()
            ?: response.companyCode?.trim()
        if (responseCode.isNullOrEmpty() || !responseCode.equals(expected.companyCode, ignoreCase = true)) return false
        val responseUserCode = response.user?.companyCode?.trim()
        if (!responseUserCode.isNullOrEmpty() && !responseUserCode.equals(expected.companyCode, ignoreCase = true)) return false

        val responseSDKAppId = response.imCredential?.sdkAppId?.takeIf { it > 0 }
            ?: response.timSdkAppId?.takeIf { it > 0 }
        if (responseSDKAppId != null && responseSDKAppId != expected.sdkAppId) return false
        val credentialUserID = response.imCredential?.userId?.trim()
        val responseUserID = response.timUserId?.trim() ?: response.user?.timUserId?.trim()
        if (!credentialUserID.isNullOrEmpty() && !responseUserID.isNullOrEmpty() && credentialUserID != responseUserID) return false

        val responseCompanyId = response.company?.id?.takeIf { it > 0 } ?: return false
        if (responseCompanyId != expected.companyId) return false
        if (expectedCompanyId != null && expectedCompanyId > 0 && responseCompanyId != expectedCompanyId) return false
        return true
    }

    const val CURRENT_SCHEMA_VERSION = 2
}
