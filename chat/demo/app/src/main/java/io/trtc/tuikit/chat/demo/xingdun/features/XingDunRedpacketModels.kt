package io.trtc.tuikit.chat.demo.xingdun.features

internal data class XingDunRedpacketBalancePayload(
    val userId: Int = 0,
    val nickname: String = "",
    val avatar: String? = null,
    val timUserId: String = "",
    val timSdkAppId: Int = 0,
    val redpacketBalance: Int = 0,
    val currencyUnit: String = "CNY",
)

internal data class XingDunRedpacketUserPayload(
    val userId: Int? = null,
    val timUserId: String = "",
    val nickname: String = "",
    val avatar: String? = null,
) {
    val displayName: String
        get() = nickname.trim().ifBlank { timUserId }
}

internal data class XingDunRedpacketClaimPayload(
    val id: Int = 0,
    val packetNo: String = "",
    val user: XingDunRedpacketUserPayload? = null,
    val claimAmount: Int = 0,
    val remainAmountAfter: Int = 0,
    val remainCountAfter: Int = 0,
    val claimTime: String? = null,
)

internal data class XingDunRedpacketDetailPayload(
    val packetNo: String = "",
    val status: Int = 0,
    val statusName: String = "",
    val scene: String = "",
    val conversationId: String = "",
    val packetType: String = "",
    val totalAmount: Int = 0,
    val count: Int = 0,
    val claimedAmount: Int = 0,
    val claimedCount: Int = 0,
    val refundedAmount: Int = 0,
    val remainAmount: Int = 0,
    val remainCount: Int = 0,
    val greeting: String = "",
    val sender: XingDunRedpacketUserPayload? = null,
    val receiver: XingDunRedpacketUserPayload? = null,
    val exclusiveReceiver: XingDunRedpacketUserPayload? = null,
    val isSender: Boolean = false,
    val hasClaimed: Boolean = false,
    val canClaim: Boolean = false,
    val myClaim: XingDunRedpacketClaimPayload? = null,
    val expireTime: String? = null,
    val createTime: String? = null,
)

internal data class XingDunRedpacketClaimResultPayload(
    val packetNo: String = "",
    val claimAmount: Int = 0,
    val claimTime: String? = null,
    val receiverBalance: Int = 0,
    val status: Int = 0,
    val statusName: String = "",
    val detail: XingDunRedpacketDetailPayload = XingDunRedpacketDetailPayload(),
)

internal data class XingDunRedpacketClaimRecordsPage(
    val packet: XingDunRedpacketDetailPayload = XingDunRedpacketDetailPayload(),
    val total: Int = 0,
    val list: List<XingDunRedpacketClaimPayload> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 30,
)

internal data class XingDunRedpacketListItem(
    val packetNo: String = "",
    val status: Int = 0,
    val statusName: String = "",
    val scene: String = "",
    val packetType: String = "",
    val totalAmount: Int = 0,
    val count: Int = 0,
    val claimedAmount: Int = 0,
    val claimedCount: Int = 0,
    val refundedAmount: Int = 0,
    val remainAmount: Int = 0,
    val remainCount: Int = 0,
    val greeting: String = "",
    val sender: XingDunRedpacketUserPayload? = null,
    val receiver: XingDunRedpacketUserPayload? = null,
    val exclusiveReceiver: XingDunRedpacketUserPayload? = null,
    val isSender: Boolean = false,
    val expireTime: String? = null,
    val createTime: String? = null,
)

internal data class XingDunRedpacketPage(
    val total: Int = 0,
    val list: List<XingDunRedpacketListItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
)

internal data class XingDunReceivedRedpacketItem(
    val packet: XingDunRedpacketListItem = XingDunRedpacketListItem(),
    val claim: XingDunRedpacketClaimPayload = XingDunRedpacketClaimPayload(),
)

internal data class XingDunReceivedRedpacketPage(
    val total: Int = 0,
    val list: List<XingDunReceivedRedpacketItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
)

internal data class XingDunRedpacketBalanceLog(
    val id: Int = 0,
    val changeType: String = "",
    val changeTypeText: String = "",
    val changeAmount: Int = 0,
    val direction: String = "",
    val beforeBalance: Int = 0,
    val afterBalance: Int = 0,
    val packetNo: String = "",
    val packetGreeting: String = "",
    val operatorTypeText: String = "",
    val remark: String = "",
    val createTime: String? = null,
)

internal data class XingDunRedpacketBalanceLogPage(
    val userId: Int = 0,
    val redpacketBalance: Int = 0,
    val total: Int = 0,
    val list: List<XingDunRedpacketBalanceLog> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
)

internal object XingDunRedpacketPresentationPolicy {
    const val STATUS_PENDING = 1
    const val STATUS_ACTIVE = 2
    const val STATUS_EXHAUSTED = 3
    const val STATUS_EXPIRED = 4
    const val STATUS_REFUNDED = 5
    const val STATUS_CANCELLED = 6

    fun isTerminal(status: Int, hasClaimed: Boolean): Boolean =
        hasClaimed || status in setOf(STATUS_EXHAUSTED, STATUS_EXPIRED, STATUS_REFUNDED, STATUS_CANCELLED)

    fun shouldPresentEnvelope(status: Int, hasClaimed: Boolean): Boolean =
        !hasClaimed && status != STATUS_EXHAUSTED

    fun canClaim(status: Int, hasClaimed: Boolean, serverCanClaim: Boolean): Boolean =
        status == STATUS_ACTIVE && !hasClaimed && serverCanClaim

    fun mergeClaims(
        current: List<XingDunRedpacketClaimPayload>,
        next: List<XingDunRedpacketClaimPayload>,
    ): List<XingDunRedpacketClaimPayload> = (current + next).distinctBy { claim ->
        claim.id.takeIf { it > 0 }?.toString()
            ?: listOf(claim.packetNo, claim.user?.timUserId, claim.claimTime, claim.claimAmount).joinToString("|")
    }
}
