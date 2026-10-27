package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal sealed class XingDunGroupSourceProfileState {
    object Loading : XingDunGroupSourceProfileState()
    object Restricted : XingDunGroupSourceProfileState()
    object Unavailable : XingDunGroupSourceProfileState()
    data class Loaded(val profile: XingDunContactDetail, val relationship: JsonObject?) : XingDunGroupSourceProfileState()
}

/** A group-source profile is publishable only after this request's fresh card authorization. */
internal class XingDunGroupSourceProfileLoader(
    private val groupID: String,
    private val targetUserID: String,
    private val currentScope: () -> XingDunGroupMemberPreviewScope?,
    private val loadGroup: suspend (String) -> XingDunGroupDetail,
    private val loadProfile: suspend (String) -> XingDunContactDetail,
    private val loadRelationship: suspend (String) -> JsonObject?,
    private val publish: (XingDunGroupSourceProfileState) -> Unit,
    private val isActive: () -> Boolean,
    private val recommend: (String) -> Unit,
    private val showRecommendationFailure: () -> Unit,
) {
    private val owner = currentScope()
    private var request = 0L
    private var pendingRecommendation: String? = null

    suspend fun refresh() {
        val ticket = ++request
        if (!isActive()) return
        // Conceal the old profile before any suspending work, including a failed permission read.
        publish(XingDunGroupSourceProfileState.Loading)
        val scope = currentScope()
        if (groupID.isBlank() || targetUserID.isBlank() || scope == null || owner != scope ||
            scope.companyCode.isBlank() || scope.sdkAppID <= 0 || scope.userID.isBlank()) {
            pendingRecommendation = null
            publish(XingDunGroupSourceProfileState.Unavailable)
            return
        }
        try {
            val group = loadGroup(groupID)
            currentCoroutineContext().ensureActive()
            if (!isCurrent(ticket, scope)) return
            check(group.groupId == groupID)
            if (!XingDunGroupMemberPolicy.canViewCard(group, XingDunGroupMember(userId = targetUserID), scope.userID)) {
                publish(XingDunGroupSourceProfileState.Restricted)
                rejectRecommendation()
                return
            }
            val profile = loadProfile(targetUserID)
            currentCoroutineContext().ensureActive()
            if (!isCurrent(ticket, scope)) return
            check(profile.timUserId.isBlank() || profile.timUserId.trim() == targetUserID)
            val relationship = loadRelationship(profile.customId?.trim()?.takeIf(String::isNotEmpty) ?: targetUserID)
            currentCoroutineContext().ensureActive()
            if (!isCurrent(ticket, scope)) return
            val relationshipTarget = relationship?.get("tim_user_id")?.takeUnless { it.isJsonNull }?.asString?.trim()
            check(relationshipTarget.isNullOrEmpty() || relationshipTarget == targetUserID)
            publish(XingDunGroupSourceProfileState.Loaded(profile, relationship))
            if (!isCurrent(ticket, scope)) return
            val recommendation = pendingRecommendation
            pendingRecommendation = null
            if (recommendation != null) {
                val relation = relationship?.get("relationship_status")?.takeUnless { it.isJsonNull }?.asString?.trim()
                if (relation == "friend" || relation == "blocked") recommend(recommendation)
                else showRecommendationFailure()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isCurrent(ticket, scope)) {
                publish(XingDunGroupSourceProfileState.Unavailable)
                rejectRecommendation()
            }
        }
    }

    // ActivityResult may arrive at ON_START, before the page's onResume reauthorization.
    // Retain only the selected target, never the old profile, and consume it once after refresh.
    fun queueRecommendation(conversationID: String) {
        if (conversationID.isNotBlank() && owner != null && currentScope() == owner) {
            pendingRecommendation = conversationID
        }
    }

    fun invalidate() { request++; pendingRecommendation = null }

    private fun rejectRecommendation() {
        if (pendingRecommendation != null) {
            pendingRecommendation = null
            showRecommendationFailure()
        }
    }

    private fun isCurrent(ticket: Long, scope: XingDunGroupMemberPreviewScope): Boolean =
        request == ticket && isActive() && owner == scope && currentScope() == scope
}
