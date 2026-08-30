package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class XingDunLocalMessageMarkRepositoryTest {
    @Test
    fun `scope key isolates tenant user and conversation`() {
        val baseline = XingDunLocalMessageMarkRepository.scopeKey("1:alpha:100", "u1", "c2c_u2")
        assertEquals(baseline, XingDunLocalMessageMarkRepository.scopeKey("1:alpha:100", "u1", "c2c_u2"))
        assertNotEquals(baseline, XingDunLocalMessageMarkRepository.scopeKey("2:beta:200", "u1", "c2c_u2"))
        assertNotEquals(baseline, XingDunLocalMessageMarkRepository.scopeKey("1:alpha:100", "u3", "c2c_u2"))
        assertNotEquals(baseline, XingDunLocalMessageMarkRepository.scopeKey("1:alpha:100", "u1", "group_g1"))
    }
}
