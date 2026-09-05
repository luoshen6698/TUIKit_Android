package io.trtc.tuikit.chat.demo.xingdun.features

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import com.google.gson.JsonParser

internal object XingDunAutoDeletePolicy {
    fun remoteDeletedIDs(encoded: String?): Set<String> = runCatching {
        JsonParser.parseString(encoded.orEmpty()).asJsonArray
            .mapNotNull { element -> element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim() }
            .filter(String::isNotBlank).toSet()
    }.getOrDefault(emptySet())

    fun effectiveTimeMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")) {
            val position = ParsePosition(0)
            val date = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }.parse(value, position)
            if (date != null && position.index == value.length) return date.time
        }
        return null
    }

    fun isExpired(timestampSeconds: Long?, ttlSeconds: Int, enabled: Boolean, effectiveAt: String?, nowMillis: Long): Boolean {
        if (!enabled || ttlSeconds <= 0 || timestampSeconds == null || timestampSeconds <= 0) return false
        // Missing/invalid effective dates must never turn on deletion of historical messages.
        val effectiveMillis = effectiveTimeMillis(effectiveAt) ?: return false
        if (timestampSeconds > Long.MAX_VALUE / 1000) return false
        val sentMillis = timestampSeconds * 1000
        return sentMillis >= effectiveMillis && sentMillis <= nowMillis - ttlSeconds.toLong() * 1000
    }

    val DEFAULT_TTL_SECONDS = listOf(0, 120, 3_600, 86_400, 604_800, 2_592_000, 7_776_000, 31_536_000)

    fun normalizedOptions(values: List<Int>): List<Int> {
        val supported = values.filter { it in DEFAULT_TTL_SECONDS }.distinct()
        return supported.ifEmpty { DEFAULT_TTL_SECONDS }
    }

    fun shouldApplyRemote(currentVersion: Int?, incomingVersion: Int): Boolean =
        incomingVersion > 0 && (currentVersion == null || incomingVersion >= currentVersion)
}
