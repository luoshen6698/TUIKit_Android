package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal sealed class XingDunGroupSourceProfileState {
    object Loading : XingDunGroupSourceProfileState()
    object Restricted : XingDunGroupSourceProfileState()
    object Unavailable : XingDunGroupSourceProfileState()
    data class Loaded(val profile: XingDunContactDetail, val relationship: JsonObject?) : XingDunGroupSourceProfileState()
}

/** Fresh card authority gates every publication; periodic retention is bounded and never grants an action. */
internal class XingDunGroupSourceProfileLoader(
    private val groupID: String,
    private val targetUserID: String,
    private val currentScope: () -> XingDunGroupMemberPreviewScope?,
    private val loadGroup: suspend (String) -> XingDunGroupDetail,
    private val loadProfile: suspend (String) -> XingDunContactDetail,
    private val loadSelfProfile: suspend () -> XingDunContactDetail,
    private val loadRelationship: suspend (String) -> JsonObject?,
    private val publish: (XingDunGroupSourceProfileState) -> Unit,
    private val isActive: () -> Boolean,
    private val recommend: (String) -> Unit,
    private val showRecommendationFailure: () -> Unit,
    private val periodicTimeoutMillis: Long = 10_000L,
) {
    init { require(periodicTimeoutMillis in 1L..10_000L) }
    private val owner = currentScope()
    private var request = 0L
    private var confirmed = false
    private var pendingRecommendation: String? = null

    suspend fun refresh(periodic: Boolean = false): Boolean {
        val ticket = ++request
        if (!isActive()) return false
        val scope = currentScope()
        val validOwner = scope != null && owner == scope && scope.companyCode.isNotBlank() &&
            scope.sdkAppID > 0 && scope.userID.isNotBlank()
        val retaining = periodic && confirmed && validOwner
        if (!retaining) emit(XingDunGroupSourceProfileState.Loading)
        if (groupID.isBlank() || targetUserID.isBlank() || !validOwner || scope == null) {
            pendingRecommendation = null
            emit(XingDunGroupSourceProfileState.Unavailable)
            return false
        }
        return try {
            if (periodic) withTimeout(periodicTimeoutMillis) {
                loadCurrent(ticket, scope, retaining, allowRecommendation = false)
            } else loadCurrent(ticket, scope, retaining = false, allowRecommendation = true)
        } catch (timeout: TimeoutCancellationException) {
            // Only our bounded child timeout may close this request; external cancellation stays cancelled.
            currentCoroutineContext().ensureActive()
            failCurrent(ticket, scope, retaining)
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failCurrent(ticket, scope, retaining)
            false
        }
    }

    private suspend fun loadCurrent(
        ticket: Long, scope: XingDunGroupMemberPreviewScope, retaining: Boolean, allowRecommendation: Boolean,
    ): Boolean {
        val group = loadGroup(groupID)
        currentCoroutineContext().ensureActive()
        if (!currentOrConceal(ticket, scope, retaining)) return false
        check(group.groupId == groupID)
        if (!XingDunGroupMemberPolicy.canViewCard(group, XingDunGroupMember(userId = targetUserID), scope.userID)) {
            emit(XingDunGroupSourceProfileState.Restricted)
            rejectRecommendation()
            return false
        }
        val self = targetUserID == scope.userID
        val loadedProfile = if (self) loadSelfProfile() else loadProfile(targetUserID)
        currentCoroutineContext().ensureActive()
        if (!currentOrConceal(ticket, scope, retaining)) return false
        check(loadedProfile.timUserId.trim() == targetUserID || !self && loadedProfile.timUserId.isBlank())
        // Public visibility controls other viewers; the owner's endpoint returns their own fields.
        val profile = if (self) loadedProfile.copy(profileVisibility = JsonObject().apply {
            XingDunProfileVisibility.fields.forEach { addProperty(it, true) }
        }) else loadedProfile
        // The authenticated owner's own profile is not a friendship or a private-chat grant.
        val relationship = if (self) JsonObject().apply {
            addProperty("tim_user_id", scope.userID)
            addProperty("is_self", true)
            addProperty("relationship_status", "self")
        } else loadRelationship(profile.customId?.trim()?.takeIf(String::isNotEmpty) ?: targetUserID)
        currentCoroutineContext().ensureActive()
        if (!currentOrConceal(ticket, scope, retaining)) return false
        val relationshipTarget = relationship?.get("tim_user_id")?.takeUnless { it.isJsonNull }?.asString?.trim()
        check(relationshipTarget.isNullOrEmpty() || relationshipTarget == targetUserID)
        emit(XingDunGroupSourceProfileState.Loaded(profile, relationship))
        if (!currentOrConceal(ticket, scope, retaining)) return false
        // A periodic response never turns a retained view into a new send authorization.
        if (allowRecommendation) {
            val recommendation = pendingRecommendation
            pendingRecommendation = null
            if (recommendation != null) {
                val relation = relationship?.get("relationship_status")?.takeUnless { it.isJsonNull }?.asString?.trim()
                if (relation == "friend" || relation == "blocked") recommend(recommendation)
                else showRecommendationFailure()
            }
        }
        return true
    }

    private fun emit(state: XingDunGroupSourceProfileState) {
        confirmed = state is XingDunGroupSourceProfileState.Loaded
        publish(state)
    }

    private fun failCurrent(ticket: Long, scope: XingDunGroupMemberPreviewScope, retaining: Boolean) {
        if (isCurrent(ticket, scope)) {
            emit(XingDunGroupSourceProfileState.Unavailable)
            rejectRecommendation()
        } else currentOrConceal(ticket, scope, retaining)
    }

    private fun currentOrConceal(ticket: Long, scope: XingDunGroupMemberPreviewScope, retaining: Boolean): Boolean {
        if (isCurrent(ticket, scope)) return true
        // A changed owner must not keep the prior visible snapshot. An obsolete ticket must not
        // clear a newer request's success, and a paused host already masks synchronously.
        if (request == ticket && isActive() && currentScope() != scope) {
            pendingRecommendation = null
            if (retaining) emit(XingDunGroupSourceProfileState.Loading)
        }
        return false
    }

    // ActivityResult can arrive at ON_START, before onResume's hard reauthorization.
    fun queueRecommendation(conversationID: String) {
        if (owner == null || currentScope() != owner) {
            pendingRecommendation = null
        } else if (conversationID.isNotBlank()) pendingRecommendation = conversationID
    }

    fun invalidate() { request++; confirmed = false; pendingRecommendation = null }

    private fun rejectRecommendation() {
        if (pendingRecommendation != null) {
            pendingRecommendation = null
            showRecommendationFailure()
        }
    }

    private fun isCurrent(ticket: Long, scope: XingDunGroupMemberPreviewScope): Boolean =
        request == ticket && isActive() && owner == scope && currentScope() == scope
}

/** Immutable projected UI inputs: never keep a mutable JsonObject as the comparison snapshot. */
internal class XingDunGroupSourceProfileRenderState {
    private data class Inputs(
        val name: String?, val avatar: String, val showAccount: Boolean, val account: String?,
        val accountTypeLabel: Int, val remark: String?, val fields: List<Pair<String, String?>>,
        val relationship: String, val blacklist: Boolean?, val audio: Boolean, val video: Boolean,
        val operating: Boolean, val error: String?,
    )
    private var previous: Inputs? = null

    fun shouldRender(
        profile: XingDunContactDetail, relationship: String, audio: Boolean, video: Boolean,
        operating: Boolean, error: String?, targetUserID: String,
    ): Boolean {
        val friend = relationship == "friend" || relationship == "blocked"
        val showAccount = XingDunProfileVisibility.visible(profile.profileVisibility, "account")
        val next = Inputs(
            name = profile.nickname.normalized(), avatar = profile.avatar.orEmpty(),
            showAccount = showAccount,
            account = if (showAccount) profile.customId.normalized() ?: profile.timUserId.ifBlank { targetUserID } else null,
            accountTypeLabel = XingDunAccountType.label(profile.accountType),
            remark = if (friend) profile.alias.normalized() else null,
            fields = listOf("birthday" to profile.birthday, "phone" to profile.phone, "email" to profile.email, "signature" to profile.signature)
                .filter { XingDunProfileVisibility.visible(profile.profileVisibility, it.first) }
                .map { it.first to it.second.normalized() },
            relationship = relationship, blacklist = if (friend) profile.isBlacklist else null,
            audio = friend && audio, video = friend && video, operating = operating, error = error?.takeIf(String::isNotBlank),
        )
        val changed = previous != next
        previous = next
        return changed
    }

    fun reset() { previous = null }
    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
