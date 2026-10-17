package io.trtc.tuikit.chat.demo.xingdun.routing

import android.content.Intent
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunIncomingCallLaunchPolicy

/** OPPO's "open application" action reaches the launcher rather than the SDK click callback. */
internal object XingDunNotificationIntentReader {
    private val payloadKeys = listOf("ext", "entity", "ChatKey")
    private const val MAX_EXTRA_BYTES = 1024

    fun readAndStage(intent: Intent?, source: String) {
        if (intent == null) return
        val values = payloadKeys.associateWith { key ->
            runCatching { intent.getStringExtra(key) }.getOrNull()
        }
        val payload = payloadFromExtras(values)
        val queued = payload != null && XingDunRouter.stageNotification(payload)
        val fields = payloadKeys.foldIndexed(0) { index, mask, key ->
            if (intent.hasExtra(key)) mask or (1 shl index) else mask
        }
        if (fields != 0) {
            // Only a fixed schema bitmask and outcome: never log ext, IDs, message text or tokens.
            Log.i("XingDunPush", "notification_entry source=$source fields=$fields queued=$queued")
        }
    }

    internal fun payloadFromExtras(values: Map<String, String?>): String? {
        val extension = values["ext"]?.trim()?.takeIf(String::isNotEmpty)
        val raw = if (extension != null) {
            if (!withinLimit(extension)) return null
            extension
        } else {
            val entity = values["entity"]?.trim()?.takeIf(String::isNotEmpty)
            val chatKey = values["ChatKey"]?.trim()?.takeIf(String::isNotEmpty)
            if (entity == null && chatKey == null) return null
            if (entity != null && !withinLimit(entity) || chatKey != null && !withinLimit(chatKey)) return null
            if (entity != null) {
                if (runCatching { JsonParser.parseString(entity).isJsonObject }.getOrDefault(false).not()) return null
                // SDK 9.0.7652 accepts both a business entity and a complete legacy ext here.
                // Keep the original text so duplicate-key validation is not lost through reserialization.
                if (chatKey != null) {
                    val destination = XingDunNotificationPayloadParser.parse(entity) ?: return null
                    val keyPayload = JsonObject().apply { addProperty("ChatKey", chatKey) }.toString()
                    if (destination != XingDunNotificationPayloadParser.parse(keyPayload)) return null
                }
                entity
            } else {
                JsonObject().apply { addProperty("ChatKey", chatKey) }.toString().takeIf(::withinLimit) ?: return null
            }
        }
        val json = runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        // Ordinary chat navigation must not take over the existing SDK call-wakeup path.
        val entity = json.get("entity")
        val callHintPayload = if (entity?.isJsonPrimitive == true && entity.asJsonPrimitive.isString) {
            runCatching {
                JsonObject().apply { add("entity", JsonParser.parseString(entity.asString)) }.toString()
            }.getOrNull()
        } else raw
        if (json.has("xd_voip_version") || XingDunIncomingCallLaunchPolicy.hasCallHint(callHintPayload)) return null
        return raw.takeIf { XingDunNotificationPayloadParser.parse(it) != null }
    }

    private fun withinLimit(value: String): Boolean = value.toByteArray(Charsets.UTF_8).size <= MAX_EXTRA_BYTES
}
