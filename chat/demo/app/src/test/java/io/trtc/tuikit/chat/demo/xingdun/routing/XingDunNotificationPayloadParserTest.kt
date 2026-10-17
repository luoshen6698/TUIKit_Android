package io.trtc.tuikit.chat.demo.xingdun.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunNotificationPayloadParserTest {
    private val scope = XingDunNotificationScope(1, "xc2026", 1001, "https://example.com/prod/im/v1", "xd_xc2026_1")

    @Test
    fun `existing XingDun private and group targets remain supported`() {
        assertConversation("c2c_xd_xc2026_2", """{"xd_push_version":1,"conversation_type":"c2c","target":"xd_xc2026_2"}""")
        assertConversation("group_@TGS#group1", """{"xd_push_version":1,"conversation_type":"group","target":"@TGS#group1"}""")
        assertConversation("c2c_xd_xc2026_2", """{"xd_push_version":1,"conversation_type":"customer_service","target":"xd_xc2026_2"}""")
    }

    @Test
    fun `existing non conversation routes and VoIP fallback remain supported`() {
        assertEquals(XingDunNotificationDestination.Tab.CONTACTS, parse("""{"route":"friend_application"}"""))
        assertEquals(XingDunNotificationDestination.Tab.WORKSPACE, parse("""{"route":"customer_service"}"""))
        assertEquals(XingDunNotificationDestination.Tab.PROFILE, parse("""{"xd_push_version":1,"route":"profile"}"""))
        assertEquals(XingDunNotificationDestination.Tab.MESSAGES, parse("""{"xd_push_version":1,"conversation_type":"system","target":"notice"}"""))
        assertEquals(XingDunNotificationDestination.Tab.MESSAGES, parse("""{"xd_voip_version":1}"""))
        assertConversation("c2c_xd_xc2026_2", """{"xd_voip_version":1,"conversation_id":"c2c_xd_xc2026_2"}""")
        assertNull(parse("""{"xd_voip_version":1,"conversation_id":"https://example.com"}"""))
    }

    @Test
    fun `TIMPush entity object is mapped to the correct private or group conversation`() {
        assertConversation("c2c_xd_xc2026_2", """{"entity":{"sender":"xd_xc2026_2","chatType":1,"action":1,"version":1,"nickName":"Test"}}""")
        assertConversation("group_@TGS#group1", """{"entity":{"sender":"@TGS#group1","chatType":2}}""")
    }

    @Test
    fun `OPPO string entity and SDK unwrapped entity are supported`() {
        assertConversation("c2c_xd_xc2026_2", """{"entity":"{\"sender\":\"xd_xc2026_2\",\"chatType\":1}"}""")
        assertConversation("c2c_xd_xc2026_2", """{"sender":"xd_xc2026_2","chatType":1}""")
    }

    @Test
    fun `ordinary legacy routing never consumes call signaling`() {
        assertNull(parse("""{"entity":{"sender":"xd_xc2026_2","chatType":1,"action":2}}"""))
        assertNull(parse("""{"sender":"xd_xc2026_2","chatType":1,"action":2}"""))
        assertNull(parse("""{"entity":{"sender":"xd_xc2026_2","chatType":1,"action":"1"}}"""))
        assertNull(parse("""{"entity":{"sender":"xd_xc2026_2","chatType":1,"version":2}}"""))
    }

    @Test
    fun `ChatKey accepts only the explicit SDK C2C and GROUP formats`() {
        assertConversation("c2c_xd_xc2026_2", """{"ChatKey":"C2Cxd_xc2026_2"}""")
        assertConversation("group_@TGS#group1", """{"ChatKey":"GROUP@TGS#group1"}""")
        listOf("c2c_xd_xc2026_2", "group_abc", "C2C", "GROUP", "UNKNOWNabc").forEach {
            assertNull(parse("""{"ChatKey":"$it"}"""))
        }
    }

    @Test
    fun `multiple known legacy representations must agree`() {
        assertConversation("c2c_xd_xc2026_2", """{"ChatKey":"C2Cxd_xc2026_2","entity":{"sender":"xd_xc2026_2","chatType":1}}""")
        assertNull(parse("""{"ChatKey":"C2Cxd_xc2026_3","entity":{"sender":"xd_xc2026_2","chatType":1}}"""))
        assertNull(parse("""{"ChatKey":"GROUPxd_xc2026_2","entity":{"sender":"xd_xc2026_2","chatType":1}}"""))
        assertNull(parse("""{"ChatKey":"C2Cxd_xc2026_2","entity":null}"""))
    }

    @Test
    fun `unknown schemas are not guessed and invalid versions do not fall through to legacy`() {
        listOf(
            """{"chatKey":"C2Cxd_xc2026_2"}""",
            """{"to":"xd_xc2026_2","type":"c2c"}""",
            """{"route":"unknown"}""",
            """{"route":"unknown","conversation_type":"c2c","target":"xd_xc2026_2"}""",
            """{"xd_push_version":2,"ChatKey":"C2Cxd_xc2026_2"}""",
            """{"xd_push_version":"1","conversation_type":"c2c","target":"xd_xc2026_2"}""",
            """{"xd_push_version":1,"xd_voip_version":1}""",
        ).forEach { assertNull(it, parse(it)) }
    }

    @Test
    fun `wrong routing field types and unsupported chat types are rejected without throwing`() {
        listOf(
            """{"ChatKey":12}""",
            """{"entity":[]}""",
            """{"entity":{"sender":{},"chatType":1}}""",
            """{"entity":{"sender":"xd_xc2026_2","chatType":"1"}}""",
            """{"entity":{"sender":"xd_xc2026_2","chatType":true}}""",
            """{"entity":{"sender":"xd_xc2026_2","chatType":1.5}}""",
            """{"entity":{"sender":"xd_xc2026_2","chatType":99999999999999999999}}""",
            """{"entity":{"sender":"xd_xc2026_2","chatType":3}}""",
            """{"xd_push_version":1,"route":{},"target":"xd_xc2026_2"}""",
        ).forEach { assertNull(it, parse(it)) }
    }

    @Test
    fun `malformed duplicate lenient and deeply nested JSON is rejected`() {
        listOf(
            "", "null", "[]", "{", """{"ChatKey":"C2Cxd_xc2026_2"} trailing""",
            """{'ChatKey':'C2Cxd_xc2026_2'}""",
            """{ChatKey:"C2Cxd_xc2026_2"}""",
            """{"ChatKey":"C2Cxd_xc2026_2","ChatKey":"C2Cxd_xc2026_3"}""",
            """{"ChatKey":"C2Cxd_xc2026_2","extra":[[[[[[[[[[1]]]]]]]]]]}""",
        ).forEach { assertNull(it, parse(it)) }
    }

    @Test
    fun `payload byte limit and identifier restrictions prevent arbitrary navigation`() {
        assertNull(parse("""{"ChatKey":"C2Cxd_xc2026_2","extra":"${"中".repeat(400)}"}"""))
        assertNull(parse("""{"ChatKey":"C2C${"a".repeat(129)}"}"""))
        listOf("https://example.com", "../abc", "a/b", "a?b", "a b", " xd_xc2026_2", "c2c_xd_xc2026_2").forEach {
            assertNull(parse("""{"entity":{"sender":"$it","chatType":1}}"""))
        }
        assertNull(parse("""{"entity":{"sender":"xd_xc2026_2\n","chatType":1}}"""))
    }

    @Test
    fun `only normalized route information is retained and reparses identically`() {
        val destination = requireNotNull(parse("""{"entity":{"sender":"xd_xc2026_2","chatType":1,"content":"private text","faceUrl":"https://example.com"}}"""))
        val canonical = XingDunNotificationPayloadParser.encode(destination)
        assertEquals(destination, parse(canonical))
        assertFalse(canonical.contains("private text"))
        assertFalse(canonical.contains("faceUrl"))
        XingDunNotificationDestination.Tab.values().forEach { assertEquals(it, parse(XingDunNotificationPayloadParser.encode(it))) }
    }

    @Test
    fun `C2C destinations must follow the current server tenant namespace`() {
        assertTrue(scope.allows(XingDunNotificationDestination.Conversation("c2c_xd_xc2026_2")))
        listOf("xd_other_2", "xd_xc20260_2", "xd_xc2026_0", "xd_xc2026_-1", "xd_xc2026_2_more", "plain_user").forEach {
            assertFalse(it, scope.allows(XingDunNotificationDestination.Conversation("c2c_$it")))
        }
        assertTrue(scope.copy(companyCode = "XC2026").allows(XingDunNotificationDestination.Conversation("c2c_xd_xc2026_2")))
    }

    @Test
    fun `pending route scope includes account tenant SDK and server identity`() {
        assertEquals(XingDunNotificationConsumption.OPEN, decision(scope))
        listOf(
            scope.copy(companyId = 2), scope.copy(companyCode = "other"), scope.copy(sdkAppId = 2002),
            scope.copy(apiBaseUrl = "https://other.example.com/prod/im/v1"), scope.copy(userId = "xd_xc2026_3"),
        ).forEach { assertEquals(XingDunNotificationConsumption.DISCARD, decision(it)) }
        assertEquals(XingDunNotificationConsumption.DISCARD, decision(null))
        assertEquals(XingDunNotificationConsumption.DISCARD,
            XingDunNotificationConsumptionPolicy.decide("old tenant-only binding", scope, 1001, scope.userId, true, true))
    }

    @Test
    fun `authentication and missing IM state defer rather than consume the pending route`() {
        assertEquals(XingDunNotificationConsumption.WAIT,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 1001, scope.userId, false, true))
        assertEquals(XingDunNotificationConsumption.WAIT,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 0, null, true, true))
        assertEquals(XingDunNotificationConsumption.WAIT,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 1001, "", true, true))
    }

    @Test
    fun `ready but different IM identity discards the pending route`() {
        assertEquals(XingDunNotificationConsumption.DISCARD,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 2002, scope.userId, true, true))
        assertEquals(XingDunNotificationConsumption.DISCARD,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 1001, "xd_xc2026_3", true, true))
    }

    @Test
    fun `authenticated IM waits until Main navigation is ready before consuming`() {
        assertEquals(XingDunNotificationConsumption.WAIT,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 1001, scope.userId, true, false))
        assertEquals(XingDunNotificationConsumption.OPEN,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 1001, scope.userId, true, true))
    }

    @Test
    fun `account mismatch discards pending even when authentication or navigation is not ready`() {
        assertEquals(XingDunNotificationConsumption.DISCARD,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope.copy(userId = "xd_xc2026_3"), 0, null, false, false))
        assertEquals(XingDunNotificationConsumption.DISCARD,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 1001, "xd_xc2026_3", false, false))
        assertEquals(XingDunNotificationConsumption.DISCARD,
            XingDunNotificationConsumptionPolicy.decide(scope.key, scope, 2002, scope.userId, false, false))
    }

    private fun parse(raw: String) = XingDunNotificationPayloadParser.parse(raw)

    private fun assertConversation(expected: String, raw: String) =
        assertEquals(XingDunNotificationDestination.Conversation(expected), parse(raw))

    private fun decision(current: XingDunNotificationScope?) =
        XingDunNotificationConsumptionPolicy.decide(scope.key, current, 1001, scope.userId, true, true)
}
