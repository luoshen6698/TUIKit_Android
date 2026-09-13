package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunGroupMessageRecallPolicyTest {
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
    ) = XingDunGroupMessageRecallAuthorization(
        currentUserRole = currentRole,
        currentUserIsAssignedCustomerService = isAssignedCustomerService,
        assignedCustomerServiceUserID = "cs",
        memberRolesByUserID = mapOf(
            "owner" to "owner",
            "admin" to "administrator",
            "member" to "member",
            "cs" to "administrator",
        ),
    )
}
