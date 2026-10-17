package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

/** Keeps incoming CallStyle updates eligible and routes accepted calls through their service. */
internal object NativeCallNotificationPolicy {
    enum class IncomingDelivery { Pending, Published, Failed, Expired }

    data class IncomingPresentation(val identity: NativeCallIdentity, val delivery: IncomingDelivery)

    fun needsLocalRinging(
        identity: NativeCallIdentity,
        presentation: IncomingPresentation?,
        notificationsAllowed: Boolean,
        isForeground: Boolean,
        isWaiting: Boolean,
        isSuspended: Boolean,
        preferLocalAlert: Boolean = false,
    ): Boolean = !isSuspended && isForeground && isWaiting &&
        presentation?.let { it.identity == identity && it.delivery == IncomingDelivery.Expired } != true &&
        (preferLocalAlert || !notificationsAllowed || presentation?.let {
            it.identity == identity && it.delivery == IncomingDelivery.Failed
        } == true)

    fun failureReason(message: String?): String = when {
        message == null -> "unspecified"
        message.contains("CallStyle notifications must", ignoreCase = true) -> "call_style_requires_service_or_fsi"
        message.contains("no valid small icon", ignoreCase = true) -> "missing_small_icon"
        message.contains("non-empty", ignoreCase = true) && message.contains("name", ignoreCase = true) -> "missing_caller_name"
        message.contains("PendingIntent", ignoreCase = true) -> "pending_intent_rejected"
        else -> "other"
    }

    fun shouldUseCompatibleIncoming(phase: NativeCallPhase, isIllegalArgument: Boolean, reason: String): Boolean =
        phase == NativeCallPhase.Waiting && isIllegalArgument && reason == "call_style_requires_service_or_fsi"

    enum class Delivery { IncomingNotification, OngoingForegroundService, Defer }

    data class Plan(
        val delivery: Delivery,
        val fullScreenIntent: Boolean,
        val onlyAlertOnce: Boolean,
    )

    fun plan(phase: NativeCallPhase, serviceActive: Boolean): Plan = when (phase) {
        NativeCallPhase.Waiting -> Plan(
            delivery = Delivery.IncomingNotification,
            fullScreenIntent = true,
            onlyAlertOnce = false,
        )
        NativeCallPhase.Accepted -> if (serviceActive) {
            Plan(
                delivery = Delivery.OngoingForegroundService,
                fullScreenIntent = false,
                onlyAlertOnce = true,
            )
        } else {
            Plan(Delivery.Defer, fullScreenIntent = false, onlyAlertOnce = true)
        }
        NativeCallPhase.None -> Plan(Delivery.Defer, fullScreenIntent = false, onlyAlertOnce = true)
    }
}
