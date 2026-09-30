package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunVerificationResponseContractTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun friendApplicationsDecodeServerPaginationAndNestedUser() {
        val page = decode(
            "FriendApplicationPage",
            """{
                "total":1,"page":1,"pageSize":20,"unread_count":1,
                "list":[{
                    "id":42,"from_user_id":7,"to_user_id":8,"apply_msg":"hello",
                    "create_time":"2026-09-30 17:00:00","status":0,"is_read":0,
                    "from_user":{"id":7,"custom_id":"tester","nickname":"Tester","tim_user_id":"tenant_7"}
                }]
            }""".trimIndent(),
        )
        assertEquals(1, field(page, "unreadCount"))
        assertEquals(20, field(page, "pageSize"))
        val application = (field(page, "list") as List<*>).single()!!
        assertEquals(42, field(application, "id"))
        assertEquals("hello", field(application, "applyMsg"))
        assertEquals("tenant_7", field(field(application, "fromUser")!!, "timUserId"))
    }

    @Test
    fun groupInvitationDecodesServerIdentifiers() {
        val invitation = decode(
            "ServerGroupInvitation",
            """{
                "id":13,"group_id":"group_1","group_name":"Test group",
                "inviter_user_id":"tenant_7","inviter_name":"Tester",
                "inviter_avatar":"https://example.test/avatar.png","message":"Join us","status":0
            }""".trimIndent(),
        )
        assertEquals("group_1", field(invitation, "groupId"))
        assertEquals("Test group", field(invitation, "groupName"))
        assertEquals("tenant_7", field(invitation, "inviterUserId"))
    }

    private fun decode(model: String, json: String): Any {
        val type = Class.forName("${XingDunVerificationMessagesActivity::class.java.name}\$$model")
        return gson.fromJson(json, type)
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
