package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunRedpacketClaimStatusPolicyTest {
    @Test
    fun `claim result publishes an immediate terminal state`() {
        val values = XingDunRedpacketClaimStatusPolicy.values(
            XingDunRedpacketClaimResultPayload(
                status = 2,
                statusName = "ACTIVE",
                detail = XingDunRedpacketDetailPayload(
                    status = 3,
                    statusName = "EXHAUSTED",
                    claimedCount = 1,
                    count = 1,
                    remainCount = 0,
                ),
            ),
        )

        assertEquals("3", values["status"])
        assertEquals("EXHAUSTED", values["status_name"])
        assertEquals("true", values["has_claimed"])
        assertEquals("0", values["remain_count"])
    }
}
