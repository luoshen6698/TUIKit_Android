package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject

/** The business server is authoritative. Missing or malformed flags never reveal cached fields. */
internal object XingDunProfileVisibility {
    val fields = listOf("account", "phone", "email", "birthday", "signature")

    fun visible(policy: JsonObject?, field: String): Boolean {
        val value = policy?.get(field) ?: return false
        return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
    }

    fun parseSettings(response: JsonObject): Pair<JsonObject, Boolean> {
        val policy = response.get("visibility")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("Invalid visibility response")
        require(fields.all { key -> policy[key]?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean } == true })
        val synced = response.get("synced")
        require(synced != null && synced.isJsonPrimitive && synced.asJsonPrimitive.isBoolean)
        return policy.deepCopy() to synced.asBoolean
    }
}
