package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunReportFormPolicyTest {
    @Test
    fun `fixed report target keeps its actual type`() {
        assertEquals("message", XingDunReportFormPolicy.initialTargetType("message", true))
        assertEquals("team", XingDunReportFormPolicy.initialTargetType("team", true))
        assertEquals("user", XingDunReportFormPolicy.initialTargetType("user", true))
    }

    @Test
    fun `free form and invalid targets default to user`() {
        assertEquals("user", XingDunReportFormPolicy.initialTargetType("message", false))
        assertEquals("user", XingDunReportFormPolicy.initialTargetType("post", true))
    }
}
