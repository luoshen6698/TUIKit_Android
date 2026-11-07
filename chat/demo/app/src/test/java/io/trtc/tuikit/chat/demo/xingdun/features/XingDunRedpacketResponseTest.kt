package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import org.junit.Assert.*
import org.junit.Test

class XingDunRedpacketResponseTest {
    private val gson=GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    @Test fun balanceResponseUsesIntegerMinorUnitsAndTenantIdentity() {
        val result=gson.fromJson("""{"user_id":360,"nickname":"test","avatar":null,"tim_user_id":"test-user","tim_sdk_app_id":1600146630,"redpacket_balance":12345,"currency_unit":"fen"}""",XingDunRedpacketBalancePayload::class.java)
        assertEquals(12345,result.redpacketBalance);assertEquals(360,result.userId);assertEquals("test-user",result.timUserId);assertEquals("fen",result.currencyUnit)
    }
    @Test fun emptySentAndReceivedListsRemainActualEmptyLists() {
        val body="""{"total":0,"list":[],"page":1,"page_size":20}"""
        val sent=gson.fromJson(body,XingDunRedpacketPage::class.java);val received=gson.fromJson(body,XingDunReceivedRedpacketPage::class.java)
        assertEquals(0,sent.total);assertTrue(sent.list.isEmpty());assertTrue(received.list.isEmpty());assertEquals(20,sent.pageSize)
    }
    @Test fun balanceLogResponseRetainsAmountsAndRecords() {
        val result=gson.fromJson("""{"user_id":360,"redpacket_balance":12345,"total":1,"list":[{"id":7,"change_type":"refund","change_amount":99,"before_balance":12246,"after_balance":12345}],"page":1,"page_size":20}""",XingDunRedpacketBalanceLogPage::class.java)
        assertEquals(12345,result.redpacketBalance);assertEquals(99,result.list.single().changeAmount);assertEquals(12345,result.list.single().afterBalance)
    }
}
