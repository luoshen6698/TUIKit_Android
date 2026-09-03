package io.trtc.tuikit.chat.demo.xingdun.features

import java.math.BigDecimal
import java.math.RoundingMode

internal enum class XingDunRedpacketType(val wireValue: String) {
    SINGLE("single"),
    TEAM_FIXED("team_fixed"),
    TEAM_RANDOM("team_random"),
    TEAM_EXCLUSIVE("team_exclusive"),
}

internal enum class XingDunRedpacketValidationError {
    INVALID_AMOUNT,
    AMOUNT_TOO_LARGE,
    INSUFFICIENT_BALANCE,
    INVALID_COUNT,
    AMOUNT_BELOW_COUNT,
    MISSING_EXCLUSIVE_RECEIVER,
    INVALID_GREETING,
}

internal class XingDunRedpacketValidationException(
    val reason: XingDunRedpacketValidationError,
) : IllegalArgumentException(reason.name)

internal data class XingDunValidatedRedpacketDraft(
    val type: XingDunRedpacketType,
    val totalAmount: Int,
    val count: Int,
    val greeting: String,
    val exclusiveReceiverTimUserId: String?,
)

internal object XingDunRedpacketSendPolicy {
    fun validate(
        amountText: String,
        requestedType: XingDunRedpacketType,
        requestedCount: Int,
        greeting: String,
        isGroup: Boolean,
        availableBalance: Int?,
        groupMemberCount: Int,
        exclusiveReceiverTimUserId: String?,
        defaultGreeting: String,
    ): XingDunValidatedRedpacketDraft {
        if (availableBalance != null && availableBalance <= 0) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.INSUFFICIENT_BALANCE)
        }
        val amount = cents(amountText)
            ?: throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.INVALID_AMOUNT)
        if (amount > MAX_AMOUNT_CENTS) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.AMOUNT_TOO_LARGE)
        }
        if (availableBalance != null && amount > availableBalance) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.INSUFFICIENT_BALANCE)
        }

        val type = if (isGroup) requestedType.takeUnless { it == XingDunRedpacketType.SINGLE }
            ?: XingDunRedpacketType.TEAM_RANDOM else XingDunRedpacketType.SINGLE
        val count = if (isGroup && type != XingDunRedpacketType.TEAM_EXCLUSIVE) requestedCount else 1
        if (count <= 0 || (groupMemberCount > 0 && count > groupMemberCount)) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.INVALID_COUNT)
        }
        if (amount < count) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.AMOUNT_BELOW_COUNT)
        }
        val receiver = exclusiveReceiverTimUserId?.trim()?.takeIf(String::isNotEmpty)
        if (type == XingDunRedpacketType.TEAM_EXCLUSIVE && receiver == null) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.MISSING_EXCLUSIVE_RECEIVER)
        }
        val normalizedGreeting = greeting.trim()
        if (normalizedGreeting.length > MAX_GREETING_LENGTH) {
            throw XingDunRedpacketValidationException(XingDunRedpacketValidationError.INVALID_GREETING)
        }
        return XingDunValidatedRedpacketDraft(
            type = type,
            totalAmount = amount,
            count = count,
            greeting = normalizedGreeting.ifEmpty { defaultGreeting },
            exclusiveReceiverTimUserId = receiver,
        )
    }

    internal fun cents(text: String): Int? = runCatching {
        val decimal = BigDecimal(text.trim())
        if (decimal <= BigDecimal.ZERO) return null
        decimal.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
    }.getOrNull()

    private const val MAX_AMOUNT_CENTS = 20_000
    private const val MAX_GREETING_LENGTH = 128
}
