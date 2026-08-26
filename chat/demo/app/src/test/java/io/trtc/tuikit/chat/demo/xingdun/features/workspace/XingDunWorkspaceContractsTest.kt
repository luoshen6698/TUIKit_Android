package io.trtc.tuikit.chat.demo.xingdun.features.workspace

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunWorkspaceContractsTest {
    @Test
    fun parsesServerDrivenTypesAndAvailability() {
        val payload = JsonParser.parseString(
            """[
              {"type":"leave","category":"attendance","name":"Leave","requires_time":true,"available":true,"sort_order":2,"approver":{"name":"Alice"}},
              {"type":"reimburse","category":"finance","name":"Reimburse","requires_amount":true,"enabled":false,"unavailable_reason":"No approver","sort_order":1,"approver":null},
              {"type":"unknown","category":"other","name":"Ignore"}
            ]"""
        ).asJsonArray

        val types = XingDunWorkspaceContracts.parseTypes(payload)

        assertEquals(listOf("leave", "reimburse"), types.map { it.type })
        assertTrue(types[0].requiresTime)
        assertEquals("Alice", types[0].approverName)
        assertFalse(types[1].available)
        assertEquals("No approver", types[1].unavailableReason)
    }

    @Test
    fun validatesOnlyFieldsRequiredByServerType() {
        val timed = XingDunWorkspaceType(
            type = "leave", category = "attendance", name = "Leave",
            requiresTime = true, requiresAmount = false, available = true,
            unavailableReason = null, approverName = null, sortOrder = 0
        )
        assertEquals(
            XingDunWorkspaceSubmissionError.TIME,
            XingDunWorkspaceSubmissionValidator.validate(timed, "Leave", "", "2026-08-22 10:00:00", "2026-08-22 09:00:00", "")
        )
        assertEquals(
            null,
            XingDunWorkspaceSubmissionValidator.validate(timed, "Leave", "", "2026-08-22 09:00:00", "2026-08-22 10:00:00", "")
        )

        val amount = timed.copy(type = "reimburse", category = "finance", requiresTime = false, requiresAmount = true)
        assertEquals(
            XingDunWorkspaceSubmissionError.AMOUNT,
            XingDunWorkspaceSubmissionValidator.validate(amount, "Expense", "", "", "", "0")
        )
    }
}
