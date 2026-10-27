package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember
import kotlinx.coroutines.CancellationException

internal data class XingDunGroupMemberPreviewScope(
    val companyCode: String,
    val sdkAppID: Int,
    val userID: String,
    val generation: Long,
)

internal enum class XingDunGroupMemberPreviewFailure { RESTRICTED, UNAVAILABLE }

/** The actual group-settings preview click entry; Android navigation stays at its host boundary. */
internal class XingDunGroupMemberPreviewNavigator(
    private val currentScope: () -> XingDunGroupMemberPreviewScope?,
    private val loadGroup: suspend (String) -> XingDunGroupDetail,
    private val loadContact: suspend (String) -> ContactInfo?,
    private val openProfile: (String, ContactInfo) -> Unit,
    private val showFailure: (XingDunGroupMemberPreviewFailure) -> Unit,
    private val isActive: () -> Boolean,
) {
    private var operating = false

    suspend fun open(groupID: String, userID: String) {
        if (operating || !isActive()) return
        val scope = currentScope()
        if (groupID.isBlank() || userID.isBlank() || scope == null ||
            scope.companyCode.isBlank() || scope.sdkAppID <= 0 || scope.userID.isBlank()) {
            showFailure(XingDunGroupMemberPreviewFailure.UNAVAILABLE)
            return
        }
        operating = true
        try {
            val detail = loadGroup(groupID)
            if (!isCurrent(scope)) return
            if (detail.groupId != groupID) {
                showFailure(XingDunGroupMemberPreviewFailure.UNAVAILABLE)
                return
            }
            if (!XingDunGroupMemberPolicy.canViewCard(detail, XingDunGroupMember(userId = userID), scope.userID)) {
                showFailure(XingDunGroupMemberPreviewFailure.RESTRICTED)
                return
            }
            val contact = loadContact(userID)
            if (!isCurrent(scope)) return
            if (contact == null || contact.userID != userID) {
                showFailure(XingDunGroupMemberPreviewFailure.UNAVAILABLE)
                return
            }
            openProfile(groupID, contact)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isCurrent(scope)) showFailure(XingDunGroupMemberPreviewFailure.UNAVAILABLE)
        } finally {
            operating = false
        }
    }

    private fun isCurrent(scope: XingDunGroupMemberPreviewScope): Boolean =
        isActive() && currentScope() == scope
}
