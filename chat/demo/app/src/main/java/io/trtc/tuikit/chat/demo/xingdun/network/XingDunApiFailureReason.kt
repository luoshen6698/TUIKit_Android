package io.trtc.tuikit.chat.demo.xingdun.network

import com.google.gson.JsonObject

/** Stable error metadata only; unknown server text is still presented through localized fallbacks. */
internal object XingDunApiFailureReason {
    fun from(envelope: JsonObject?): String? = envelope?.get("data")?.takeIf { it.isJsonObject }
        ?.asJsonObject?.get("reason")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.takeIf(String::isNotBlank)
}
