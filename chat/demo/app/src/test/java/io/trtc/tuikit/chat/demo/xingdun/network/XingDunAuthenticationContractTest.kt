package io.trtc.tuikit.chat.demo.xingdun.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                userAgreementVersion = "2026.08.13",
                privacyPolicyVersion = "2026.08.13",
                consentEvidenceId = "android:test:evidence"
            )
        )

        assertTrue(json.contains("\"company_code\":\"xc2026\""))
        assertTrue(json.contains("\"consent_source\":\"android_registration\""))
        assertFalse(json.contains("\"ios_registration\""))
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
    fun registrationAuthenticationResponseMayOmitRefreshTokenLikeIosContract() {
        val response = gson.fromJson(
            """{"access_token":"access","expires_in":7200,"company_code":"xc2026"}""",
            XingDunAuthResponse::class.java
        )

        assertEquals("access", response.accessToken)
        assertEquals(null, response.refreshToken)
    }

    @Test
    fun deletionStatusUsesPublicReceiptContract() {
        val json = gson.toJson(mapOf("deletion_receipt" to "signed-receipt"))
        assertEquals(setOf("deletion_receipt"), gson.fromJson(json, Map::class.java).keys)
    }
}
