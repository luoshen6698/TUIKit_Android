package io.trtc.tuikit.chat.demo.xingdun.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunQRCodeParserTest {
    @Test
    fun parsesExactUserAndGroupPayloads() {
        assertEquals(
            XingDunQRCodeRoute.User("user-001"),
            XingDunQRCodeParser.parse("""{"app":"XingDun","type":"user","user_id":"user-001","version":1}""")
        )
        assertEquals(
            XingDunQRCodeRoute.Group("group-001"),
            XingDunQRCodeParser.parse("""{"app":"XingDun","type":"group","group_id":"group-001","version":1}""")
        )
    }

    @Test
    fun parsesInvitationJsonAndApprovedLinks() {
        val expected = XingDunQRCodeRoute.Invitation("ab23cd", "COMP01")
        assertEquals(
            expected,
            XingDunQRCodeParser.parse(
                """{"type":"xingdun_invite","code":"AB23CD","company_code":"COMP01","version":1}"""
            )
        )
        assertEquals(
            expected,
            XingDunQRCodeParser.parse(
                "https://api.xingdunim.com/prod/xingdun/share.html?code=ab23cd&company_code=COMP01"
            )
        )
        assertEquals(
            XingDunQRCodeRoute.Invitation("ab23cd", null),
            XingDunQRCodeParser.parse("xingdun://invite?invite_code=ab23cd")
        )
    }

    @Test
    fun rejectsSecretFieldsWrongVersionsAndUnapprovedHosts() {
        val inputs = listOf(
            """{"app":"XingDun","type":"user","user_id":"user-001","token":"secret","version":1}""",
            """{"app":"XingDun","type":"group","group_id":"group-001","version":2}""",
            "https://example.com/prod/xingdun/share.html?code=ab23cd",
            "https://api.xingdunim.com/prod/xingdun/share.html?code=ab23cd&token=secret"
        )
        inputs.forEach { payload ->
            assertTrue(payload, runCatching { XingDunQRCodeParser.parse(payload) }.isFailure)
        }
    }
}
