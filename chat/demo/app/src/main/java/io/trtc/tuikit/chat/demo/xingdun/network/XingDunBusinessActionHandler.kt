package io.trtc.tuikit.chat.demo.xingdun.network

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionHandler
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class XingDunBusinessActionHandler(context: Context) : BusinessActionHandler {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun handle(action: BusinessAction, completion: BusinessActionCompletion): Boolean {
        // The existing server has no direct join endpoint. Keep the same Tencent SDK join path
        // used by iOS so callback synchronization can update the business database.
        if (action is BusinessAction.JoinGroup) return false
        // The existing team/update contract accepts an uploaded file, not a remote URL.
        if (action is BusinessAction.UpdateGroup && action.fields.keys == setOf("avatar_url")) return false

        scope.launch {
            try {
                val result = perform(action)
                withContext(Dispatchers.Main) { completion.onSuccess(result) }
            } catch (error: Throwable) {
                val code = (error as? XingDunApiException)?.businessCode
                    ?: (error as? XingDunApiException)?.httpStatus
                    ?: -1
                withContext(Dispatchers.Main) {
                    val message = error.message.orEmpty().ifBlank {
                        appContext.getString(R.string.xingdun_action_failed)
                    }
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                    completion.onFailure(code, message)
                }
            }
        }
        return true
    }

    private suspend fun perform(action: BusinessAction): BusinessActionResult {
        val session = XingDunSessionManager.currentSession()
            ?: throw XingDunApiException(401, 401, appContext.getString(R.string.xingdun_session_expired))
        val client = XingDunSessionManager.apiClient()

        return when (action) {
            is BusinessAction.ApplyFriend -> {
                val targetID = resolveLocalUserID(action.targetUserID)
                client.postEmpty(
                    session,
                    "friend/apply",
                    mapOf("to_user_id" to targetID, "message" to action.message)
                )
                BusinessActionResult()
            }
            is BusinessAction.HandleFriendApplication -> {
                val applicationID = resolveFriendApplicationID(action.targetUserID)
                client.postEmpty(
                    session,
                    if (action.approve) "friend/agree" else "friend/reject",
                    mapOf("apply_id" to applicationID)
                )
                BusinessActionResult()
            }
            is BusinessAction.DeleteFriend -> {
                client.postEmpty(session, "friend/delete", mapOf("friend_id" to resolveLocalUserID(action.targetUserID)))
                BusinessActionResult()
            }
            is BusinessAction.SetFriendBlacklist -> {
                client.postEmpty(
                    session,
                    "friend/setBlacklist",
                    mapOf(
                        "friend_id" to resolveLocalUserID(action.targetUserID),
                        "is_blacklist" to action.enabled
                    )
                )
                BusinessActionResult()
            }
            is BusinessAction.SetFriendRemark -> {
                client.postEmpty(
                    session,
                    "friend/setAlias",
                    mapOf("friend_id" to resolveLocalUserID(action.targetUserID), "alias" to action.remark)
                )
                BusinessActionResult()
            }
            is BusinessAction.CreateGroup -> {
                val avatarFile = action.avatarURL?.takeIf(String::isNotBlank)?.let(::downloadGroupAvatar)
                val response: JsonObject = client.postMultipart(
                    session,
                    "team/create",
                    mapOf(
                        "name" to action.groupName,
                        "member_user_ids" to Gson().toJson(action.memberUserIDs),
                        "be_invite_mode" to 2,
                        "app_language" to if (Locale.getDefault().language == "zh") "zh-Hans" else "en"
                    ),
                    avatarFile?.let(::listOf).orEmpty(),
                    JsonObject::class.java
                )
                BusinessActionResult(response.string("group_id"))
            }
            is BusinessAction.InviteGroupMembers -> {
                client.postEmpty(
                    session,
                    "team/invite",
                    mapOf("team_id" to action.groupID, "member_user_ids" to action.memberUserIDs)
                )
                BusinessActionResult()
            }
            is BusinessAction.HandleGroupApplication -> {
                val invitationID = resolveGroupInvitationID(action)
                client.postEmpty(
                    session,
                    "team/handleInvitation",
                    mapOf("invitation_id" to invitationID, "approve" to action.approve)
                )
                BusinessActionResult()
            }
            is BusinessAction.UpdateGroup -> {
                val body = mutableMapOf<String, Any?>("team_id" to action.groupID)
                body.putAll(action.fields)
                client.postEmpty(session, "team/update", body)
                BusinessActionResult()
            }
            is BusinessAction.SetGroupAdministrator -> {
                client.postEmpty(
                    session,
                    "team/setAdmin",
                    mapOf(
                        "team_id" to action.groupID,
                        "member_user_id" to action.memberUserID,
                        "is_admin" to action.enabled
                    )
                )
                BusinessActionResult()
            }
            is BusinessAction.MuteGroupMember -> {
                client.postEmpty(
                    session,
                    "team/muteMember",
                    mapOf(
                        "team_id" to action.groupID,
                        "member_user_id" to action.memberUserID,
                        "duration_seconds" to action.durationSeconds
                    )
                )
                BusinessActionResult()
            }
            is BusinessAction.SetGroupMuteAll -> {
                client.postEmpty(
                    session,
                    "team/setMuteAll",
                    mapOf("team_id" to action.groupID, "is_muted" to action.enabled)
                )
                BusinessActionResult()
            }
            is BusinessAction.TransferGroupOwner -> {
                client.postEmpty(
                    session,
                    "team/transfer",
                    mapOf("team_id" to action.groupID, "new_owner_user_id" to action.newOwnerUserID)
                )
                BusinessActionResult()
            }
            is BusinessAction.RemoveGroupMembers -> {
                action.memberUserIDs.forEach { memberUserID ->
                    client.postEmpty(
                        session,
                        "team/kick",
                        mapOf("team_id" to action.groupID, "member_id" to resolveLocalUserID(memberUserID))
                    )
                }
                BusinessActionResult()
            }
            is BusinessAction.LeaveGroup -> {
                client.postEmpty(session, "team/leave", mapOf("team_id" to action.groupID))
                BusinessActionResult()
            }
            is BusinessAction.DismissGroup -> {
                client.postEmpty(session, "team/dismiss", mapOf("team_id" to action.groupID))
                BusinessActionResult()
            }
            is BusinessAction.JoinGroup -> error("JoinGroup must use the Tencent SDK path")
        }
    }

    private fun downloadGroupAvatar(url: String): XingDunUploadFile {
        val uri = Uri.parse(url)
        if (uri.scheme == "content" || uri.scheme == "file") {
            val mimeType = if (uri.scheme == "content") {
                appContext.contentResolver.getType(uri)
            } else {
                null
            }?.lowercase(Locale.ROOT)?.takeIf { it in SUPPORTED_AVATAR_MIME_TYPES }
                ?: if (uri.path?.endsWith(".png", true) == true) "image/png" else "image/jpeg"
            val bytes = when (uri.scheme) {
                "content" -> appContext.contentResolver.openInputStream(uri)?.use(::readAvatarBytes)
                else -> uri.path?.let(::File)?.inputStream()?.use(::readAvatarBytes)
            } ?: throw IllegalArgumentException(appContext.getString(R.string.xingdun_group_avatar_invalid))
            return groupAvatarFile(bytes, mimeType)
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        try {
            val mimeType = connection.contentType?.substringBefore(';')?.lowercase(Locale.ROOT)
                ?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp") }
                ?: if (url.substringBefore('?').endsWith(".png", true)) "image/png" else "image/jpeg"
            return groupAvatarFile(connection.inputStream.use(::readAvatarBytes), mimeType)
        } finally {
            connection.disconnect()
        }
    }

    private fun readAvatarBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_AVATAR_BYTES) {
                appContext.getString(R.string.xingdun_group_avatar_invalid)
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().also { data ->
            require(data.isNotEmpty()) { appContext.getString(R.string.xingdun_group_avatar_invalid) }
        }
    }

    private fun groupAvatarFile(bytes: ByteArray, mimeType: String): XingDunUploadFile {
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return XingDunUploadFile("avatar", "xingdun-group-avatar.$extension", mimeType, bytes)
    }

    private suspend fun resolveLocalUserID(timUserID: String): Int {
        val session = XingDunSessionManager.currentSession()
            ?: throw XingDunApiException(401, 401, appContext.getString(R.string.xingdun_session_expired))
        val detail: JsonObject = XingDunSessionManager.apiClient().get(
            session,
            "user/detail",
            mapOf("tim_user_id" to timUserID),
            JsonObject::class.java
        )
        return detail.int("id") ?: throw IllegalStateException(appContext.getString(R.string.xingdun_error_member_not_found))
    }

    private suspend fun resolveFriendApplicationID(timUserID: String): Int {
        val session = XingDunSessionManager.currentSession()
            ?: throw XingDunApiException(401, 401, appContext.getString(R.string.xingdun_session_expired))
        val page: JsonObject = XingDunSessionManager.apiClient().get(
            session,
            "friend/receivedApply",
            mapOf("page" to "1", "pageSize" to "50"),
            JsonObject::class.java
        )
        val match = page.array("list").firstOrNull { item ->
            item.asJsonObject.obj("from_user")?.string("tim_user_id") == timUserID
        }?.asJsonObject
        return match?.int("id") ?: throw IllegalStateException(appContext.getString(R.string.xingdun_error_friend_application_expired))
    }

    private suspend fun resolveGroupInvitationID(action: BusinessAction.HandleGroupApplication): Int {
        action.applicationID.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        val session = XingDunSessionManager.currentSession()
            ?: throw XingDunApiException(401, 401, appContext.getString(R.string.xingdun_session_expired))
        val invitations: JsonArray = XingDunSessionManager.apiClient().get(
            session,
            "team/invitations",
            emptyMap(),
            JsonArray::class.java
        )
        val match = invitations.firstOrNull { element ->
            val item = element.asJsonObject
            item.string("group_id") == action.groupID &&
                (action.fromUserID.isBlank() || item.string("inviter_user_id") == action.fromUserID)
        }?.asJsonObject
        return match?.int("id") ?: throw IllegalStateException(appContext.getString(R.string.xingdun_error_group_invitation_expired))
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asInt

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

    private companion object {
        const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        val SUPPORTED_AVATAR_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
