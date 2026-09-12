package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object XingDunFriendSearchPayloadPolicy {
    fun profile(payload: JsonElement?): JsonObject? = when {
        payload == null || payload.isJsonNull -> null
        payload.isJsonObject -> payload.asJsonObject.takeUnless { it.entrySet().isEmpty() }
        payload.isJsonArray && payload.asJsonArray.size() == 0 -> null
        else -> throw IllegalArgumentException("Unexpected friend search response data")
    }
}
