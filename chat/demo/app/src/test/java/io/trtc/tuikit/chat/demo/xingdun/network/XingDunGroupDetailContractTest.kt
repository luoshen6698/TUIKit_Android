package io.trtc.tuikit.chat.demo.xingdun.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunGroupDetailContractTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun businessDisplayIdWinsAndTencentInternalIdIsNeverExposed() {
        val detail = gson.fromJson(
            """{
                "group_id":"@TGS#tenant-private",
                "display_group_id":"100284",
                "name":"Project Group"
            }""".trimIndent(),
            XingDunGroupDetail::class.java,
        )
        assertEquals("100284", detail.publicGroupId)

        assertNull(detail.copy(displayGroupId = null).publicGroupId)
        assertNull(detail.copy(displayGroupId = "public-code").publicGroupId)
        assertEquals("100285", detail.copy(displayGroupId = null, groupId = "100285").publicGroupId)
    }

    @Test
    fun businessPolicyAllowsAllMembersManagersAndAssignedCustomerService() {
        val member = XingDunGroupDetail(currentUserRole = "member", updateTeamMode = 2)
        assertFalse(member.canEditGroupInfo)
        assertTrue(member.copy(updateTeamMode = 1).canEditGroupInfo)
        assertTrue(member.copy(currentUserRole = "administrator").canEditGroupInfo)
        assertTrue(member.copy(currentUserRole = "owner").canEditGroupInfo)
        assertTrue(member.copy(currentUserIsAssignedCs = true).canEditGroupInfo)
    }
}
