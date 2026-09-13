package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData

internal object ContactPickerSourcePolicy {

    fun <T> recent(
        allContacts: List<UserPickerData<T>>,
        recentUserIds: List<String>
    ): List<UserPickerData<T>> {
        val contactsById = allContacts.associateBy { it.key }
        return recentUserIds.distinct().mapNotNull(contactsById::get)
    }

    fun <T> filter(
        contacts: List<UserPickerData<T>>,
        query: String,
        matches: (UserPickerData<T>, String) -> Boolean
    ): List<UserPickerData<T>> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return contacts
        return contacts.filter { matches(it, keyword) }
    }
}
