package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunAutoDeletePolicyTest {

    @Test
    fun `server option order is retained and unsupported values are removed`() {
        assertEquals(
            listOf(0, 3_600, 86_400),
            XingDunAutoDeletePolicy.normalizedOptions(listOf(0, 3_600, 7, 86_400, 3_600))
        )
        assertEquals(
            XingDunAutoDeletePolicy.DEFAULT_TTL_SECONDS,
            XingDunAutoDeletePolicy.normalizedOptions(emptyList())
        )
    }

    @Test
    fun `remote configuration cannot roll back a newer version`() {
        assertTrue(XingDunAutoDeletePolicy.shouldApplyRemote(null, 1))
        assertTrue(XingDunAutoDeletePolicy.shouldApplyRemote(3, 3))
        assertFalse(XingDunAutoDeletePolicy.shouldApplyRemote(4, 3))
        assertFalse(XingDunAutoDeletePolicy.shouldApplyRemote(0, 0))
    }
}
