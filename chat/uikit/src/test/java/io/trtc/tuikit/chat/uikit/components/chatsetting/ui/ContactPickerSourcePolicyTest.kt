package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPickerSourcePolicyTest {

    private val contacts = listOf(
        pickerData("a001", "Alice"),
        pickerData("b001", "Bob"),
        pickerData("c001", "Carol")
    )

    @Test
    fun recent_keepsConversationOrderAndOnlyAvailableContacts() {
        val result = ContactPickerSourcePolicy.recent(
            allContacts = contacts,
            recentUserIds = listOf("c001", "missing", "a001", "c001")
        )

        assertEquals(listOf("c001", "a001"), result.map { it.key })
    }

    @Test
    fun filter_trimsQueryAndKeepsMatchingContacts() {
        val result = ContactPickerSourcePolicy.filter(contacts, "  bo ") { item, query ->
            item.label.contains(query, ignoreCase = true) || item.key.contains(query, ignoreCase = true)
        }

        assertEquals(listOf("b001"), result.map { it.key })
    }

    @Test
    fun filter_emptyQueryKeepsSourceOrder() {
        assertEquals(contacts, ContactPickerSourcePolicy.filter(contacts, "   ") { _, _ -> false })
    }

    private fun pickerData(key: String, label: String) = UserPickerData(
        key = key,
        label = label,
        avatarUrl = null,
        extraData = key
    )
}
