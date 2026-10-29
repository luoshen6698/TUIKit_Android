package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class XingDunIndependentProfileState(
    val profile: XingDunContactDetail?, val relationship: String, val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Independent detail/discovery path used by the contact Activity. */
internal class XingDunIndependentProfileLoader(
    private val targetUserID: String,
    private val fallback: XingDunContactDetail,
    private val initialRelationship: String,
    private val currentScope: () -> XingDunGroupMemberPreviewScope?,
    private val loadProfile: suspend () -> XingDunContactDetail,
    private val loadRelationship: suspend (String) -> JsonObject?,
    private val publish: (XingDunIndependentProfileState) -> Unit,
    private val isActive: () -> Boolean = { true },
    private val currentStorageRevision: () -> Long? = { 0L },
) {
    private val owner = currentScope()
    private var request = 0L
    private var confirmed: Triple<Long, XingDunGroupMemberPreviewScope, Long>? = null

    suspend fun refresh() {
        val ticket = ++request
        confirmed = null
        if (!isActive()) return
        publish(XingDunIndependentProfileState(null, "unknown", loading = true))
        val scope = currentScope()
        val storageRevision = currentStorageRevision()
        if (scope == null || storageRevision == null || !sameOwner(scope) ||
            scope.companyCode.isBlank() || scope.sdkAppID <= 0 || scope.userID.isBlank() || targetUserID.isBlank()) {
            publish(XingDunIndependentProfileState(null, "unknown", failed = true))
            return
        }
        try {
            val profile = try { loadProfile() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (!currentOrConceal(ticket, scope, storageRevision)) return
            check(profile?.timUserId.isNullOrBlank() || profile?.timUserId?.trim() == targetUserID)
            val knownFriend = initialRelationship == "friend" || initialRelationship == "blocked"
            // A failed friend read can only fall back to a fresh public discovery response.
            // No old public field or permission is carried across this request.
            val relationship = if (profile == null || !knownFriend) {
                loadRelationship(profile?.customId.normalized() ?: fallback.customId.normalized() ?: targetUserID)
            } else null
            currentCoroutineContext().ensureActive()
            if (!currentOrConceal(ticket, scope, storageRevision)) return
            check(profile != null || relationship != null)
            val target = relationship?.get("tim_user_id")?.takeUnless { it.isJsonNull }?.asString?.trim()
            check(target.isNullOrEmpty() || target == targetUserID)
            val fresh = profile?.copy(
                timUserId = targetUserID,
                nickname = profile.nickname.normalized() ?: fallback.nickname,
                avatar = profile.avatar.normalized() ?: fallback.avatar,
            ) ?: fallback.copy(
                customId = null, signature = null, phone = null, email = null, birthday = null,
                profileVisibility = null,
            )
            val merged = if (relationship != null) mergeXingDunRelationshipProfile(fresh, relationship, targetUserID)
                else fresh to if (knownFriend) { if (fresh.isBlacklist) "blocked" else "friend" } else "unknown"
            confirmed = Triple(ticket, scope, storageRevision)
            publish(XingDunIndependentProfileState(merged.first, merged.second))
        } catch (cancelled: CancellationException) {
            // A callback that ignores cancellation must not republish data or start discovery.
            if (request == ticket && isActive()) publish(XingDunIndependentProfileState(null, "unknown"))
            throw cancelled
        } catch (_: Exception) {
            if (currentOrConceal(ticket, scope, storageRevision)) {
                publish(XingDunIndependentProfileState(null, "unknown", failed = true))
            }
        }
    }

    fun invalidate() { request++; confirmed = null }

    fun ownsSnapshot(): Boolean = confirmed?.let {
        isActive() && request == it.first && currentScope() == it.second && currentStorageRevision() == it.third
    } == true

    private fun sameOwner(scope: XingDunGroupMemberPreviewScope): Boolean = owner?.let {
        it.companyCode == scope.companyCode && it.sdkAppID == scope.sdkAppID && it.userID == scope.userID
    } == true

    private fun currentOrConceal(ticket: Long, scope: XingDunGroupMemberPreviewScope, storageRevision: Long): Boolean {
        if (request != ticket || !isActive()) return false
        if (currentScope() == scope && currentStorageRevision() == storageRevision) return true
        publish(XingDunIndependentProfileState(null, "unknown", failed = true))
        return false
    }

    private fun String?.normalized() = this?.trim()?.takeIf(String::isNotEmpty)
}

/** Shared projection of the existing business discovery response, never an IM SDK profile. */
internal fun mergeXingDunRelationshipProfile(
    current: XingDunContactDetail, profile: JsonObject, targetUserID: String,
    authoritativeOwnProfile: Boolean = false,
): Pair<XingDunContactDetail, String> {
    fun string(name: String): String? = profile.get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)
    fun int(name: String): Int? = profile.get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }
    val self = profile.get("is_self")?.takeUnless { it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
    val relationship = if (self) "self" else string("relationship_status") ?: "none"
    // The group loader already fetched /user/profile for the authenticated owner. Its
    // synthetic self relationship carries no public policy and must not mask that own profile.
    if (authoritativeOwnProfile && self) return current to relationship
    val policy = profile.get("profile_visibility")?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy()
    return current.copy(
        id = int("id") ?: current.id,
        customId = string("custom_id").takeIf { XingDunProfileVisibility.visible(policy, "account") },
        nickname = string("nickname") ?: current.nickname,
        avatar = string("avatar") ?: current.avatar,
        signature = string("signature").takeIf { XingDunProfileVisibility.visible(policy, "signature") },
        phone = current.phone.takeIf { XingDunProfileVisibility.visible(policy, "phone") },
        email = current.email.takeIf { XingDunProfileVisibility.visible(policy, "email") },
        birthday = current.birthday.takeIf { XingDunProfileVisibility.visible(policy, "birthday") },
        profileVisibility = policy,
        accountType = if (profile.has("account_type")) string("account_type") else "normal",
        timUserId = string("tim_user_id") ?: current.timUserId.ifBlank { targetUserID },
        isBlacklist = relationship == "blocked",
    ) to relationship
}
