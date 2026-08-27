package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeParser
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XingDunGroupQRCodePayloadTest {
    @Test
    fun payloadMatchesIOSContractAndRoundTripsThroughScanner() {
        val payload = XingDunGroupQRCodePayload.make(" @TGS#group-100 ")

        assertEquals(
            "{\"app\":\"XingDun\",\"group_id\":\"@TGS#group-100\",\"type\":\"group\",\"version\":1}",
            payload,
        )
        assertEquals(XingDunQRCodeRoute.Group("@TGS#group-100"), XingDunQRCodeParser.parse(payload))
        assertFalse(payload.contains("token", ignoreCase = true))
        assertFalse(payload.contains("secret", ignoreCase = true))
    }

    @Test
    fun blankGroupIDProducesNoPayload() {
        assertEquals("", XingDunGroupQRCodePayload.make("  "))
    }
}
