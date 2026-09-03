package io.trtc.tuikit.chat.uikit.components.userpicker.ui

import io.trtc.tuikit.chat.uikit.components.userpicker.adapter.FlatItem
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPickerDataSourcePolicyTest {
    @Test
    fun `recent presentation preserves conversation order without section headers`() {
        val source = listOf(
            pickerData("recent-2", "B User"),
            pickerData("recent-1", "A User"),
        )

        val items = UserPickerDataSourcePolicy.preserveSourceOrder(source)

        assertTrue(items.none { it is FlatItem.SectionHeader })
        assertEquals(
            listOf("recent-2", "recent-1"),
            items.map { (it as FlatItem.UserItem).data.key },
        )
    }

    private fun pickerData(key: String, label: String): UserPickerData<Any?> {
        return UserPickerData(key = key, label = label, avatarUrl = null, extraData = null)
    }
}
