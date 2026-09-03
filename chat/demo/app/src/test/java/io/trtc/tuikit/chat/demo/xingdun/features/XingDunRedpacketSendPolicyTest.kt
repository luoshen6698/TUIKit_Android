package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class XingDunRedpacketSendPolicyTest {
    @Test
    fun `converts exact decimal amount to cents`() {
        assertEquals(1234, XingDunRedpacketSendPolicy.cents("12.34"))
        assertNull(XingDunRedpacketSendPolicy.cents("12.345"))
        assertNull(XingDunRedpacketSendPolicy.cents("0"))
    }

    @Test
    fun `direct chat always prepares a single packet`() {
        val result = XingDunRedpacketSendPolicy.validate(
            amountText = "8.88",
            requestedType = XingDunRedpacketType.TEAM_RANDOM,
            requestedCount = 5,
            greeting = " ",
            isGroup = false,
            availableBalance = 1_000,
            groupMemberCount = 0,
            exclusiveReceiverTimUserId = null,
            defaultGreeting = "Best wishes",
        )

        assertEquals(XingDunRedpacketType.SINGLE, result.type)
        assertEquals(1, result.count)
        assertEquals("Best wishes", result.greeting)
    }

    @Test
    fun `group packet validates count and exclusive recipient`() {
        val countError = assertThrows(XingDunRedpacketValidationException::class.java) {
            XingDunRedpacketSendPolicy.validate(
                "0.01", XingDunRedpacketType.TEAM_RANDOM, 2, "Hi", true, 100, 5, null, "Best wishes",
            )
        }
        assertEquals(XingDunRedpacketValidationError.AMOUNT_BELOW_COUNT, countError.reason)

        val receiverError = assertThrows(XingDunRedpacketValidationException::class.java) {
            XingDunRedpacketSendPolicy.validate(
                "1", XingDunRedpacketType.TEAM_EXCLUSIVE, 1, "Hi", true, 100, 5, null, "Best wishes",
            )
        }
        assertEquals(XingDunRedpacketValidationError.MISSING_EXCLUSIVE_RECEIVER, receiverError.reason)
    }

    @Test
    fun `rejects amount beyond balance`() {
        val error = assertThrows(XingDunRedpacketValidationException::class.java) {
            XingDunRedpacketSendPolicy.validate(
                "10", XingDunRedpacketType.SINGLE, 1, "Hi", false, 999, 0, null, "Best wishes",
            )
        }

        assertEquals(XingDunRedpacketValidationError.INSUFFICIENT_BALANCE, error.reason)
    }

    @Test
    fun `reports empty balance before validating draft fields`() {
        val error = assertThrows(XingDunRedpacketValidationException::class.java) {
            XingDunRedpacketSendPolicy.validate(
                "", XingDunRedpacketType.SINGLE, 1, "Hi", false, 0, 0, null, "Best wishes",
            )
        }

        assertEquals(XingDunRedpacketValidationError.INSUFFICIENT_BALANCE, error.reason)
    }
}
