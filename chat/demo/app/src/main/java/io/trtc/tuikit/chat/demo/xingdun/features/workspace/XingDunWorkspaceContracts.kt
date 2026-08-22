package io.trtc.tuikit.chat.demo.xingdun.features.workspace

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

internal data class XingDunWorkspaceType(
    val type: String,
    val category: String,
    val name: String,
    val requiresTime: Boolean,
    val requiresAmount: Boolean,
    val available: Boolean,
    val unavailableReason: String?,
    val approverName: String?,
    val sortOrder: Int
)

internal object XingDunWorkspaceContracts {
    private val supportedCategories = setOf("attendance", "finance", "hr")

    fun parseTypes(payload: JsonArray): List<XingDunWorkspaceType> = payload.mapNotNull { element ->
        val json = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
        val type = json.string("type") ?: return@mapNotNull null
        val category = json.string("category")?.takeIf(supportedCategories::contains) ?: return@mapNotNull null
        val name = json.string("name") ?: type
        XingDunWorkspaceType(
            type = type,
            category = category,
            name = name,
            requiresTime = json.boolean("requires_time"),
            requiresAmount = json.boolean("requires_amount"),
            available = json.optionalBoolean("available") ?: json.optionalBoolean("enabled") ?: true,
            unavailableReason = json.string("unavailable_reason"),
            approverName = json.getAsJsonObject("approver")?.string("name")
                ?: json.getAsJsonObject("approver")?.string("nickname")
                ?: json.getAsJsonObject("approver")?.string("tim_user_id"),
            sortOrder = json.int("sort_order") ?: Int.MAX_VALUE
        )
    }.sortedWith(compareBy(XingDunWorkspaceType::category, XingDunWorkspaceType::sortOrder, XingDunWorkspaceType::name))

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.optionalBoolean(name: String): Boolean? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asBoolean }.getOrNull() }

    private fun JsonObject.boolean(name: String): Boolean = optionalBoolean(name) ?: false

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asInt }.getOrNull() }
}

internal enum class XingDunWorkspaceSubmissionError { TITLE, REASON, TIME, AMOUNT }

internal object XingDunWorkspaceSubmissionValidator {
    fun validate(
        type: XingDunWorkspaceType,
        title: String,
        reason: String,
        start: String,
        end: String,
        amount: String
    ): XingDunWorkspaceSubmissionError? {
        if (title.trim().isEmpty() || title.trim().length > 128) return XingDunWorkspaceSubmissionError.TITLE
        if (reason.trim().length > 5_000) return XingDunWorkspaceSubmissionError.REASON
        if (type.requiresTime) {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { isLenient = false }
            val startDate = runCatching { formatter.parse(start.trim()) }.getOrNull()
            val endDate = runCatching { formatter.parse(end.trim()) }.getOrNull()
            if (startDate == null || endDate == null || !startDate.before(endDate)) return XingDunWorkspaceSubmissionError.TIME
        }
        if (type.requiresAmount) {
            val value = amount.trim().toBigDecimalOrNull()
            if (value == null || value <= BigDecimal.ZERO || value > BigDecimal("99999999.99")) {
                return XingDunWorkspaceSubmissionError.AMOUNT
            }
        }
        return null
    }
}
