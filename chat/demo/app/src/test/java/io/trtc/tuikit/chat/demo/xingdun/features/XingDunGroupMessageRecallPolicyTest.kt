package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunGroupMessageRecallPolicyTest {
    @Test fun `all member recall mode permits ordinary members only against ordinary member messages`() {
        val policy = authorization("member", mode = 1)
        assertTrue(policy.canRecallOther("member"))
        assertFalse(policy.canRecallOther("owner"))
        assertFalse(policy.canRecallOther("admin"))
        assertFalse(policy.canRecallOther("cs"))
        assertFalse(policy.canRecallOther("unknown-sender"))
    }
    @Test fun `missing or invalid recall mode never grants management recall`() {
        listOf(null, 0, 3).forEach { mode ->
            assertFalse(authorization("owner", mode = mode).canRecallOther("member"))
            assertFalse(authorization("administrator", isAssignedCustomerService = true, mode = mode).canRecallOther("owner"))
        }
        assertFalse(authorization("unknown-role", mode = 1).canRecallOther("member"))
    }
    @Test
    fun `assigned customer service can recall every other role`() {
        val authorization = authorization("administrator", isAssignedCustomerService = true)
        assertTrue(authorization.canRecallOther("owner"))
        assertTrue(authorization.canRecallOther("admin"))
        assertTrue(authorization.canRecallOther("member"))
    }

    @Test
    fun `owner and administrator can recall only ordinary members`() {
        assertTrue(authorization("owner").canRecallOther("member"))
        assertTrue(authorization("administrator").canRecallOther("member"))
        assertFalse(authorization("administrator").canRecallOther("owner"))
        assertFalse(authorization("owner").canRecallOther("admin"))
    }

    @Test
    fun `assigned customer service messages are protected`() {
        assertFalse(authorization("owner").canRecallOther("cs"))
        assertFalse(authorization("administrator").canRecallOther("cs"))
        assertFalse(authorization("member").canRecallOther("member"))
    }

    private fun authorization(
        currentRole: String,
        isAssignedCustomerService: Boolean = false,
        mode: Int? = 2,
    ) = XingDunGroupMessageRecallAuthorization(
        currentUserRole = currentRole,
        currentUserIsAssignedCustomerService = isAssignedCustomerService,
        deleteMessageMode = mode,
        assignedCustomerServiceUserID = "cs",
        memberRolesByUserID = mapOf(
            "owner" to "owner",
            "admin" to "administrator",
            "member" to "member",
            "cs" to "administrator",
        ),
    )
}
