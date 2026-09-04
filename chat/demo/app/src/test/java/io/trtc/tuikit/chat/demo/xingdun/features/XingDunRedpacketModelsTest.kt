package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunRedpacketModelsTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun `decodes iOS aligned detail and claim fields`() {
        val detail = gson.fromJson(
            """{
                "packet_no":"RP001","status":2,"status_name":"Active","packet_type":"team_exclusive",
                "total_amount":888,"count":1,"claimed_count":0,"greeting":"Best wishes",
                "conversation_id":"group_team-1","tim_msg_key":"key-1","tim_msg_seq":88,
                "sender":{"user_id":7,"tim_user_id":"u7","nickname":"Alice"},
                "is_sender":false,"has_claimed":false,"can_claim":true,
                "expire_time":"2026-09-04 12:00:00"
            }""".trimIndent(),
            XingDunRedpacketDetailPayload::class.java,
        )

        assertEquals("RP001", detail.packetNo)
        assertEquals("Alice", detail.sender?.displayName)
        assertEquals(888, detail.totalAmount)
        assertEquals("key-1", detail.timMsgKey)
        assertEquals(88L, detail.timMsgSeq)
        assertTrue(XingDunRedpacketPresentationPolicy.canClaim(detail.status, detail.hasClaimed, detail.canClaim))
        assertTrue(XingDunRedpacketPresentationPolicy.shouldPresentEnvelope(detail.status, detail.hasClaimed))
    }

    @Test
    fun `resolves c2c and group source message destinations`() {
        assertEquals(
            XingDunRedpacketMessageDestination("c2c_u8", "message-key", null),
            XingDunRedpacketMessageDestinationPolicy.resolve(
                XingDunRedpacketDetailPayload(conversationId = "c2c_u8", timMsgKey = "message-key"),
            ),
        )
        assertEquals(
            XingDunRedpacketMessageDestination("group_team-1", "", 88L),
            XingDunRedpacketMessageDestinationPolicy.resolve(
                XingDunRedpacketDetailPayload(conversationId = "group_team-1", timMsgSeq = 88L),
            ),
        )
        assertNull(
            XingDunRedpacketMessageDestinationPolicy.resolve(
                XingDunRedpacketDetailPayload(conversationId = "c2c_u8"),
            ),
        )
    }

    @Test
    fun `decodes received wrapper and balance logs`() {
        val received = gson.fromJson(
            """{"total":1,"page":1,"page_size":20,"list":[{
                "packet":{"packet_no":"RP002","greeting":"Hi","sender":{"tim_user_id":"u8","nickname":"Bob"}},
                "claim":{"id":9,"packet_no":"RP002","claim_amount":123,"claim_time":"2026-09-03 10:00:00"}
            }]}""",
            XingDunReceivedRedpacketPage::class.java,
        )
        val logs = gson.fromJson(
            """{"user_id":1,"redpacket_balance":5500,"total":1,"list":[{
                "id":3,"change_type_text":"Refund","change_amount":100,"after_balance":5500,"packet_no":"RP002"
            }]}""",
            XingDunRedpacketBalanceLogPage::class.java,
        )

        assertEquals(123, received.list.single().claim.claimAmount)
        assertEquals("Bob", received.list.single().packet.sender?.displayName)
        assertEquals(5_500, logs.redpacketBalance)
        assertEquals(100, logs.list.single().changeAmount)
    }

    @Test
    fun `expired and claimed packets are terminal and not claimable`() {
        assertTrue(XingDunRedpacketPresentationPolicy.isTerminal(4, false))
        assertTrue(XingDunRedpacketPresentationPolicy.isTerminal(2, true))
        assertFalse(XingDunRedpacketPresentationPolicy.canClaim(4, false, true))
        assertFalse(XingDunRedpacketPresentationPolicy.canClaim(2, true, true))
        assertTrue(XingDunRedpacketPresentationPolicy.shouldPresentEnvelope(4, false))
        assertFalse(XingDunRedpacketPresentationPolicy.shouldPresentEnvelope(3, false))
    }

    @Test
    fun `claim pagination advances without duplicating records`() {
        val first = XingDunRedpacketClaimPayload(id = 1, packetNo = "RP003", claimAmount = 100)
        val duplicate = XingDunRedpacketClaimPayload(id = 2, packetNo = "RP003", claimAmount = 200)
        val last = XingDunRedpacketClaimPayload(id = 3, packetNo = "RP003", claimAmount = 300)

        assertEquals(
            listOf(first, duplicate, last),
            XingDunRedpacketPresentationPolicy.mergeClaims(listOf(first, duplicate), listOf(duplicate, last)),
        )
    }
}
