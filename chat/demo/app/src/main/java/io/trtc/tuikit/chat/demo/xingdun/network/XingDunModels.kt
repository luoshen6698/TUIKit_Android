package io.trtc.tuikit.chat.demo.xingdun.network

internal data class XingDunEnvelope<T>(
    val code: Int = -1,
    val message: String = "",
    val data: T? = null,
    val traceId: String? = null
)

data class XingDunBootstrapConfiguration(
    val mode: String? = null,
    val imProvider: String = "",
    val configured: Boolean = false,
    val companyCode: String = "",
    val sdkAppId: Int = 0,
    val apiBaseUrl: String? = null,
    val company: XingDunCompany? = null,
    val platform: XingDunPlatformConfiguration? = null,
    val push: XingDunPushConfiguration = XingDunPushConfiguration(),
    val features: XingDunFeatures = XingDunFeatures(),
    val privacy: XingDunPrivacy = XingDunPrivacy()
)

data class XingDunCompany(
    val id: Int? = null,
    val code: String = "",
    val name: String = "",
    val logoUrl: String? = null,
    val domain: String? = null,
    val apiBaseUrl: String? = null,
    val expireTime: String? = null
)

data class XingDunPushConfiguration(
    val businessIdDev: String = "",
    val businessIdProd: String = "",
    val voipCertificateIdDev: String = "",
    val voipCertificateIdProd: String = ""
)

data class XingDunPlatformConfiguration(
    val platformName: String? = null,
    val platformLogo: String? = null,
    val aboutUrl: String? = null,
    val siteCopyright: String? = null,
    val siteRecordNumber: String? = null,
)

data class XingDunFeatures(
    val redpacket: Boolean = false,
    val customerService: Boolean = false,
    val audioCall: Boolean = false,
    val videoCall: Boolean = false,
    val groupCall: Boolean = false,
    val messageFavorite: Boolean = false,
    val messagePin: Boolean = false,
    val readReceipt: Boolean = false,
    val autoDelete: Boolean = false,
    val community: Boolean = false,
    val remoteLanguagePack: Boolean = false
)

/** Tenant-scoped shared message retention configuration. */
data class XingDunAutoDeleteConfiguration(
    val conversationId: String = "",
    val syncConversationId: String? = null,
    val conversationType: String? = null,
    val ttlSeconds: Int = 0,
    val enabled: Boolean = false,
    val version: Int = 0,
    val updatedAt: String? = null,
    val updatedBy: String? = null,
    val allowedTtlSeconds: List<Int> = emptyList()
)

data class XingDunPrivacy(
    val privacyUrl: String = "",
    val userAgreementUrl: String = ""
)

data class XingDunVersionCheckResult(
    val hasUpdate: Boolean = false,
    val isForce: Boolean = false,
    val latestVersion: XingDunVersionInformation? = null
)

data class XingDunVersionInformation(
    val versionCode: String = "",
    val versionName: String? = null,
    val downloadUrl: String? = null,
    val updateLog: String? = null,
    val publishTime: String? = null
)

data class XingDunAuthResponse(
    val accessToken: String = "",
    val tokenType: String? = null,
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val refreshExpiresIn: Long? = null,
    val user: XingDunUserProfile? = null,
    val company: XingDunCompany? = null,
    val imCredential: XingDunIMCredential? = null,
    val companyCode: String? = null,
    val timSdkAppId: Int? = null,
    val timUserId: String? = null,
    val username: String? = null,
    val nickname: String? = null
)

data class XingDunIMCredential(
    val provider: String? = null,
    val imProvider: String? = null,
    val sdkAppId: Int = 0,
    val userId: String = "",
    val userSig: String = "",
    val expire: Long? = null,
    val expireAt: String? = null
)

data class XingDunUserProfile(
    val id: Int? = null,
    val userId: Int? = null,
    val username: String? = null,
    val timUserId: String? = null,
    val customId: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val companyCode: String? = null,
    val status: String? = null
)

/** Tenant-scoped business profile returned by `/user/detail`. */
data class XingDunContactDetail(
    val id: Int = 0,
    val customId: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val birthday: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val signature: String? = null,
    val timUserId: String = "",
    val alias: String? = null,
    val isBlacklist: Boolean = false,
    val isBlockedByPeer: Boolean = false,
    val departmentPath: List<String> = emptyList()
)

/** Business metadata merged into the Tencent IM joined-group list. */
data class XingDunGroupMetadata(
    val groupId: String = "",
    val name: String? = null,
    val avatar: String? = null,
    val announcement: String? = null,
    val isOfficial: Boolean = false,
    val isCustomerService: Boolean = false
)

/** Tenant-scoped group details returned by `/team/detail`. */
data class XingDunGroupDetail(
    val groupId: String = "",
    val displayGroupId: String? = null,
    val name: String = "",
    val avatar: String? = null,
    val announcement: String? = null,
    val intro: String? = null,
    val memberCount: Int = 0,
    val ownerUserId: String? = null,
    val currentUserRole: String = "member",
    val groupType: String = "unknown",
    val joinMode: Int = JOIN_MODE_APPROVAL,
    val inviteMode: Int = MODE_ADMINISTRATORS,
    val updateTeamMode: Int = 2,
    val atAllMode: Int = MODE_ADMINISTRATORS,
    val beInviteMode: Int = BE_INVITE_CONFIRM,
    val viewMemberCardMode: Int = MODE_ADMINISTRATORS,
    val pinMessageMode: Int = MODE_ADMINISTRATORS,
    val muteAll: Boolean = false,
    val muteAllLevel: Int = 0,
    val isOfficial: Boolean = false,
    val isCustomerService: Boolean = false,
    val currentUserIsAssignedCs: Boolean = false,
) {
    val publicGroupId: String?
        get() = displayGroupId?.trim()?.takeIf { value -> value.isNotEmpty() && value.all(Char::isDigit) }
            ?: groupId.trim().takeIf { value -> value.isNotEmpty() && value.all(Char::isDigit) }

    val canEditGroupInfo: Boolean
        get() = currentUserIsAssignedCs ||
            currentUserRole == "owner" ||
            currentUserRole == "administrator" ||
            updateTeamMode == MODE_ALL

    /** Matches the iOS group-announcement screen: members can view, owners/admins can save. */
    val canEditAnnouncement: Boolean
        get() = currentUserRole == "owner" || currentUserRole == "administrator"

    val canEditManagement: Boolean
        get() = currentUserRole == "owner" || currentUserRole == "administrator"

    val canManagePolicies: Boolean
        get() = currentUserRole == "owner" ||
            (currentUserIsAssignedCs && currentUserRole == "administrator")

    val canSetMuteAll: Boolean
        get() = currentUserIsAssignedCs ||
            (muteAllLevel != MUTE_LEVEL_CUSTOMER_SERVICE && canEditManagement)

    val supportsJoinMode: Boolean
        get() = groupType.equals("public", ignoreCase = true) ||
            groupType.equals("community", ignoreCase = true)

    val canLeave: Boolean
        get() = currentUserRole != "owner" && !currentUserIsAssignedCs

    val canDismiss: Boolean
        get() = currentUserRole == "owner" && !isOfficial && !isCustomerService

    private companion object {
        const val MODE_ALL = 1
        const val MODE_ADMINISTRATORS = 2
        const val JOIN_MODE_APPROVAL = 2
        const val BE_INVITE_CONFIRM = 1
        const val MUTE_LEVEL_CUSTOMER_SERVICE = 2
    }
}

data class XingDunGroupMemberPage(
    val total: Int = 0,
    val list: List<XingDunGroupMember> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 50,
)

data class XingDunGroupMember(
    val userId: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val role: String = "member",
    val isMuted: Boolean = false,
    val muteEndTime: String? = null,
    val joinTime: String? = null,
)

data class XingDunLoginRequest(
    val username: String,
    val password: String,
    val companyCode: String
)

data class XingDunRegisterRequest(
    val username: String,
    val password: String,
    val confirmPassword: String,
    val nickname: String,
    val inviteCode: String?,
    val companyCode: String,
    val adultDeclaration: Boolean,
    val consent: Boolean,
    val userAgreementVersion: String,
    val privacyPolicyVersion: String,
    val consentSource: String = "android_registration",
    val consentEvidenceId: String
)

data class XingDunPhoneRegisterRequest(
    val phone: String,
    val code: String,
    val password: String,
    val confirmPassword: String,
    val nickname: String?,
    val inviteCode: String?,
    val companyCode: String,
    val adultDeclaration: Boolean,
    val consent: Boolean,
    val userAgreementVersion: String,
    val privacyPolicyVersion: String,
    val consentSource: String = "android_registration",
    val consentEvidenceId: String
)

data class XingDunSendResetCodeRequest(
    val verifyType: String,
    val target: String,
    val companyCode: String
)

data class XingDunResetCodeResponse(
    val verifyType: String? = null,
    val expiresIn: Long? = null
)

data class XingDunResetPasswordRequest(
    val verifyType: String,
    val target: String,
    val code: String,
    val newPassword: String,
    val confirmPassword: String,
    val companyCode: String
)

data class XingDunAccountDeletionStatus(
    val status: String = "",
    val requestedAt: String? = null,
    val purgeAfter: String? = null,
    val retentionDays: Int? = null,
    val completedAt: String? = null
)

data class XingDunRefreshRequest(
    val refreshToken: String,
    val companyCode: String
)

data class XingDunStoredSession(
    val tenantSchemaVersion: Int = 2,
    val accessToken: String,
    val tokenType: String,
    val accessExpiresAtMillis: Long,
    val refreshToken: String? = null,
    val refreshExpiresAtMillis: Long? = null,
    val companyCode: String,
    val companyId: Int? = null,
    val companyName: String,
    val apiBaseUrl: String,
    val sdkAppId: Int,
    val timUserId: String,
    val userSig: String,
    val userSigExpiresAtMillis: Long,
    val username: String? = null,
    val nickname: String,
    val push: XingDunPushConfiguration = XingDunPushConfiguration(),
    val features: XingDunFeatures = XingDunFeatures(),
    val privacy: XingDunPrivacy = XingDunPrivacy()
)

class XingDunApiException(
    val businessCode: Int?,
    val httpStatus: Int?,
    override val message: String,
    val traceId: String? = null
) : Exception(message) {
    val isUnauthorized: Boolean
        get() = httpStatus == 401 || businessCode == 401 || businessCode == 40100 || businessCode == 40101
}
