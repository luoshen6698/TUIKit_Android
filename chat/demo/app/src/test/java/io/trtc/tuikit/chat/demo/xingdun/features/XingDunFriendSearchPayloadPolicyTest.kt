package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunFriendSearchPayloadPolicyTest {
    @Test
    fun missingPayloadShapesResolveToNoUser() {
        assertNull(XingDunFriendSearchPayloadPolicy.profile(null))
        assertNull(XingDunFriendSearchPayloadPolicy.profile(JsonNull.INSTANCE))
        assertNull(XingDunFriendSearchPayloadPolicy.profile(JsonObject()))
        assertNull(XingDunFriendSearchPayloadPolicy.profile(JsonArray()))
    }

    @Test
    fun objectPayloadResolvesToProfile() {
        val profile = JsonObject().apply { addProperty("tim_user_id", "t008") }

        assertEquals(profile, XingDunFriendSearchPayloadPolicy.profile(profile))
    }

    @Test(expected = IllegalArgumentException::class)
    fun primitivePayloadRemainsAFormatError() {
        XingDunFriendSearchPayloadPolicy.profile(JsonPrimitive("invalid"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonEmptyArrayRemainsAFormatError() {
        XingDunFriendSearchPayloadPolicy.profile(JsonArray().apply { add(JsonObject()) })
    }
}
