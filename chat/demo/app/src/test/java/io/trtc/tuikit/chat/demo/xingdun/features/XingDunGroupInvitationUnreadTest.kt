package io.trtc.tuikit.chat.demo.xingdun.features
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class XingDunGroupInvitationUnreadTest {
    @Test fun unreadCountIsVisibleOnlyToItsAccountAndLoginInstance() {
        val state = XingDunGroupInvitationUnread("api|tenant|b002|generation-2", 1)
        assertEquals(1, state.forOwner("api|tenant|b002|generation-2"))
        assertEquals(0, state.forOwner("api|tenant|t008|generation-2"))
        assertEquals(0, state.forOwner("api|tenant|b002|generation-3"))
        assertEquals(0, state.forOwner(null))
    }
    @Test fun onlyBoundedIntegerCountsAreAccepted() {
        for (count in listOf("0", "1", "100")) assertEquals(count.toInt(),
            XingDunGroupInvitationUnread.parse("owner", JsonParser.parseString("{\"unread_count\":$count}").asJsonObject).count)
        for (count in listOf("-1", "101", "1.5", "null", "true", "\"1\"", "9999999999999999")) {
            assertThrows(IllegalArgumentException::class.java) {
                XingDunGroupInvitationUnread.parse("owner", JsonParser.parseString("{\"unread_count\":$count}").asJsonObject)
            }
        }
    }
}
