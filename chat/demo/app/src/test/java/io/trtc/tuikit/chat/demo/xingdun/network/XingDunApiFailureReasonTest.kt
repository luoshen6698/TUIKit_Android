package io.trtc.tuikit.chat.demo.xingdun.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class XingDunApiFailureReasonTest {
    @Test fun `IP policy rejection retains the machine reason for localized recovery guidance`() {
        val envelope = JsonParser.parseString("""{"code":403,"message":"denied","data":{"reason":"login_ip_restricted"}}""").asJsonObject
        val reason = XingDunApiFailureReason.from(envelope)
        assertEquals("login_ip_restricted", reason)
        assertFalse(XingDunApiException(403, 403, "denied", reason = reason).isUnauthorized)
    }
    @Test fun `non object data and non string or empty reasons remain unspecified`() {
        assertNull(XingDunApiFailureReason.from(null))
        listOf("{}", """{"data":[]}""", """{"data":null}""", """{"data":{"reason":true}}""",
            """{"data":{"reason":403}}""", """{"data":{"reason":""}}""").forEach {
            assertNull(XingDunApiFailureReason.from(JsonParser.parseString(it).asJsonObject))
        }
    }
    @Test fun `existing phone reset payload remains tenant scoped and uses the reset channel`() {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        val request = gson.toJsonTree(XingDunSendResetCodeRequest("phone", "13800000001", "test-tenant")).asJsonObject
        assertEquals("phone", request["verify_type"].asString)
        assertEquals("test-tenant", request["company_code"].asString)
        assertEquals("13800000001", request["target"].asString)
        val response = gson.fromJson("""{"verify_type":"phone","expires_in":300}""", XingDunResetCodeResponse::class.java)
        assertEquals("phone", response.verifyType)
        assertEquals(300L, response.expiresIn)
    }
}
