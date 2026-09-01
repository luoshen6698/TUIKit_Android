package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact

import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import org.junit.Assert.assertEquals
import org.junit.Test

class AddContactNavigatorTest {

    @Test
    fun `missing preset always starts with search`() {
        assertEquals(AddContactFlowStep.SEARCH, AddContactNavigator.initialStep(AddType.CONTACT, false))
        assertEquals(AddContactFlowStep.SEARCH, AddContactNavigator.initialStep(AddType.GROUP, false))
    }

    @Test
    fun `preset contact opens contact detail`() {
        assertEquals(AddContactFlowStep.CONTACT_DETAIL, AddContactNavigator.initialStep(AddType.CONTACT, true))
    }

    @Test
    fun `preset group opens group join form`() {
        assertEquals(AddContactFlowStep.GROUP_JOIN_FORM, AddContactNavigator.initialStep(AddType.GROUP, true))
    }
}
