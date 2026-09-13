package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecalledMessageDisplayPolicyTest {
    @Test
    fun customerServiceManagementRecallUsesBusinessRole() {
        assertEquals(
            ManagementRecallActor.CUSTOMER_SERVICE,
            RecalledMessageDisplayPolicy.managementRecallActor(
                "XingDun management recall: customer_service",
                "administrator",
            ),
        )
    }

    @Test
    fun groupManagerManagementRecallUsesBusinessRole() {
        assertEquals(
            ManagementRecallActor.GROUP_MANAGER,
            RecalledMessageDisplayPolicy.managementRecallActor(
                "XingDun management recall: group_manager",
                "administrator",
            ),
        )
    }

    @Test
    fun legacyServerRecallHidesTencentAdministratorAccount() {
        assertEquals(
            ManagementRecallActor.SYSTEM_ADMINISTRATOR,
            RecalledMessageDisplayPolicy.managementRecallActor(null, "administrator"),
        )
    }

    @Test
    fun ordinaryUserRecallKeepsExistingPresentation() {
        assertNull(
            RecalledMessageDisplayPolicy.managementRecallActor("", "xd_xc2026_266"),
        )
    }
}
