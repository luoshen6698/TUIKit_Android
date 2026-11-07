package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.*
import org.junit.Test

class XingDunMineDrawerContractTest {
    @Test fun drawerNeverUsesPreviousAccountsImIdentity() {
        assertTrue(mineCanUseIMData("account-a","account-a"));assertFalse(mineCanUseIMData("account-a","account-b"));assertFalse(mineCanUseIMData(null,"account-a"));assertFalse(mineCanUseIMData("account-a",null));assertFalse(mineCanUseIMData("",""))
    }
    @Test fun referenceStatsFormattingHandlesBoundaries() {
        assertEquals("0",mineMetric(-1));assertEquals("999",mineMetric(999));assertEquals("1.0k",mineMetric(1000));assertEquals("10.0k",mineMetric(9999));assertEquals("9999+",mineMetric(10000))
    }
    @Test fun appealRequiresRealAccountContactAndExplanationBeforeAnyNetworkWrite() {
        val valid=MineAppeal("xyf01","test@example.invalid","This is a test account appeal",0)
        assertTrue(valid.valid());assertFalse(valid.copy(account=" ").valid());assertFalse(valid.copy(contact=" ").valid());assertFalse(valid.copy(reason="short").valid());assertFalse(valid.copy(type=4).valid());assertFalse(valid.copy(reason="x".repeat(601)).valid());assertFalse(valid.copy(contact="x".repeat(129)).valid());assertFalse(valid.copy(account="x".repeat(81)).valid())
    }
    @Test fun nativeAccountFeedbackRetainsAllAppealDetailsWithoutClaimingIndependentHistory() {
        val value=MineAppeal(" affected-account "," user@example.invalid "," Detailed explanation here ",2)
        val content=value.content("Deleted by mistake")
        assertTrue(content.contains("Account: affected-account"));assertTrue(content.contains("Contact: user@example.invalid"));assertTrue(content.endsWith("Detailed explanation here"));assertTrue(content.contains("Deleted by mistake"))
    }
}
