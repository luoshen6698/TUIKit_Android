package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunAutoDeletePolicyTest {
    private val effective = "2026-09-05T18:00:00+08:00"
    private val start = requireNotNull(XingDunAutoDeletePolicy.effectiveTimeMillis(effective))

    @Test
    fun `remote deletion accepts only explicit message IDs and deduplicates them`() {
        assertEquals(setOf("M1", "M2"), XingDunAutoDeletePolicy.remoteDeletedIDs("""["M1"," M2 ","M1","",null,3,{}]"""))
        assertTrue(XingDunAutoDeletePolicy.remoteDeletedIDs("invalid").isEmpty())
        assertTrue(XingDunAutoDeletePolicy.remoteDeletedIDs(null).isEmpty())
    }

    @Test
    fun `expires at TTL boundary but protects messages before activation`() {
        assertFalse(XingDunAutoDeletePolicy.isExpired(start / 1000 - 1, 120, true, effective, start + 500_000))
        assertFalse(XingDunAutoDeletePolicy.isExpired(start / 1000, 120, true, effective, start + 119_999))
        assertTrue(XingDunAutoDeletePolicy.isExpired(start / 1000, 120, true, effective, start + 120_000))
        assertTrue(XingDunAutoDeletePolicy.isExpired(start / 1000, 120, true, effective, start + 600_000))
    }

    @Test
    fun `disabled or invalid metadata never deletes messages`() {
        assertFalse(XingDunAutoDeletePolicy.isExpired(start / 1000, 120, false, effective, start + 500_000))
        assertFalse(XingDunAutoDeletePolicy.isExpired(start / 1000, 0, true, effective, start + 500_000))
        for (date in listOf(null, "", "invalid", effective + "junk")) {
            assertFalse(XingDunAutoDeletePolicy.isExpired(start / 1000, 120, true, date, start + 500_000))
        }
        assertFalse(XingDunAutoDeletePolicy.isExpired(null, 120, true, effective, start + 500_000))
        assertEquals(start, XingDunAutoDeletePolicy.effectiveTimeMillis("2026-09-05T10:00:00Z"))
    }


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
