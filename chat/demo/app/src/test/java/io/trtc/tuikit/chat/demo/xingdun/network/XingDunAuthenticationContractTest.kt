package io.trtc.tuikit.chat.demo.xingdun.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunAuthenticationContractTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun phoneRegistrationCarriesAndroidConsentAndTenantBoundary() {
        val json = gson.toJson(
            XingDunPhoneRegisterRequest(
                phone = "13800138000",
                code = "123456",
                password = "Safe#Entry9A",
                confirmPassword = "Safe#Entry9A",
                nickname = null,
                inviteCode = null,
                companyCode = "xc2026",
                adultDeclaration = true,
                consent = true,
                userAgreementVersion = XingDunSessionManager.USER_AGREEMENT_VERSION,
                privacyPolicyVersion = XingDunSessionManager.PRIVACY_VERSION,
                consentEvidenceId = "android:test:evidence"
            )
        )

        assertTrue(json.contains("\"company_code\":\"xc2026\""))
        assertTrue(json.contains("\"consent_source\":\"android_registration\""))
        assertTrue(json.contains("\"user_agreement_version\":\"2026.10.01\""))
        assertTrue(json.contains("\"privacy_policy_version\":\"2026.10.04\""))
        assertFalse(json.contains("\"ios_registration\""))
    }

    @Test
    fun accountRegistrationCarriesDistinctPublishedDocumentVersions() {
        val request = XingDunRegisterRequest(
            username = "t0010", password = "test-only", confirmPassword = "test-only",
            nickname = "", inviteCode = null, companyCode = "xc2026",
            adultDeclaration = true, consent = true,
            userAgreementVersion = XingDunSessionManager.USER_AGREEMENT_VERSION,
            privacyPolicyVersion = XingDunSessionManager.PRIVACY_VERSION,
            consentEvidenceId = "android:test:evidence"
        )
        val fields = gson.fromJson(gson.toJson(request), Map::class.java)
        assertEquals("2026.10.01", fields["user_agreement_version"])
        assertEquals("2026.10.04", fields["privacy_policy_version"])
        assertEquals("android_registration", fields["consent_source"])
        assertEquals("", fields["nickname"])
        assertFalse(fields.containsKey("invite_code"))
    }

    @Test
    fun resetContractsMatchIosAndServerFieldNames() {
        val sendJson = gson.toJson(XingDunSendResetCodeRequest("email", "member@example.com", "xc2026"))
        val resetJson = gson.toJson(
            XingDunResetPasswordRequest(
                "email",
                "member@example.com",
                "123456",
                "Safe#Entry9A",
                "Safe#Entry9A",
                "xc2026"
            )
        )

        assertEquals(
            setOf("verify_type", "target", "company_code"),
            gson.fromJson(sendJson, Map::class.java).keys
        )
        assertTrue(resetJson.contains("\"new_password\""))
        assertTrue(resetJson.contains("\"confirm_password\""))
        assertTrue(resetJson.contains("\"company_code\":\"xc2026\""))
    }

    @Test
    fun registrationAuthenticationResponseParses180DayRefreshCredential() {
        val response = gson.fromJson(
            """{
                "access_token":"access",
                "expires_in":604800,
                "refresh_token":"refresh",
                "refresh_expires_in":15552000,
                "company_code":"xc2026"
            }""".trimIndent(),
            XingDunAuthResponse::class.java
        )

        assertEquals("access", response.accessToken)
        assertEquals("refresh", response.refreshToken)
        assertEquals(15_552_000L, response.refreshExpiresIn)
    }

    @Test
    fun deletionStatusUsesPublicReceiptContract() {
        val json = gson.toJson(mapOf("deletion_receipt" to "signed-receipt"))
        assertEquals(setOf("deletion_receipt"), gson.fromJson(json, Map::class.java).keys)
    }

    @Test
    fun legacyStoredSessionWithoutUsernameRemainsCopySafe() {
        val legacy = gson.fromJson(
            """{
                "access_token":"access",
                "token_type":"Bearer",
                "access_expires_at_millis":1,
                "company_code":"xc2026",
                "company_name":"XingDunIM",
                "api_base_url":"https://example.com/prod/im/v1",
                "sdk_app_id":1001,
                "tim_user_id":"xd_xc2026_1",
                "user_sig":"sig",
                "user_sig_expires_at_millis":2,
                "nickname":"User",
                "push":{},
                "features":{},
                "privacy":{}
            }""".trimIndent(),
            XingDunStoredSession::class.java
        )

        assertNull(legacy.username)
        assertEquals("Updated", legacy.copy(nickname = "Updated").nickname)
    }
}
