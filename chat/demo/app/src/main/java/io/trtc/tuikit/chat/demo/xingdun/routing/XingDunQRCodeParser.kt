package io.trtc.tuikit.chat.demo.xingdun.routing

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface XingDunQRCodeRoute {
    data class User(val userID: String) : XingDunQRCodeRoute
    data class Group(val groupID: String) : XingDunQRCodeRoute
    data class Invitation(val code: String, val companyCode: String?) : XingDunQRCodeRoute
}

internal object XingDunQRCodeParser {
    private val invitationPattern = Regex("^[23456789abcdefghjkmnpqrstuvwxyz]{6,20}$")
    private val companyPattern = Regex("^[A-Za-z0-9]{4,20}$")
    private val forbiddenKeys = setOf("token", "usersig", "user_sig", "jwt", "secret", "secretkey", "password")
    private val allowedQueryKeys = setOf("code", "invite_code", "invitecode", "company_code", "companycode", "campaign")

    fun parse(rawPayload: String): XingDunQRCodeRoute {
        val payload = rawPayload.trim()
        require(payload.isNotEmpty() && payload.toByteArray().size <= 2_048)
        runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()?.let(::parseJson)?.let { return it }
        return parseUri(URI(payload))
    }

    private fun parseJson(json: JsonObject): XingDunQRCodeRoute {
        require(json.keySet().map(String::lowercase).none(forbiddenKeys::contains))
        require(json.int("version") == 1)
        return when (json.string("type")?.lowercase()) {
            "user" -> {
                require(json.string("app").equals("xingdun", ignoreCase = true))
                XingDunQRCodeRoute.User(identifier(json.string("user_id")))
            }
            "group" -> {
                require(json.string("app").equals("xingdun", ignoreCase = true))
                XingDunQRCodeRoute.Group(identifier(json.string("group_id")))
            }
            "xingdun_invite" -> XingDunQRCodeRoute.Invitation(
                invitationCode(json.string("code")),
                companyCode(json.string("company_code"))
            )
            else -> throw IllegalArgumentException("Unsupported QR payload")
        }
    }

    private fun parseUri(uri: URI): XingDunQRCodeRoute {
        require(uri.userInfo == null && uri.port == -1 && uri.fragment == null)
        val universal = uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("api.xingdunim.com", ignoreCase = true) &&
            uri.path == "/prod/xingdun/share.html"
        val custom = uri.scheme.equals("xingdun", ignoreCase = true) &&
            uri.host.equals("invite", ignoreCase = true) &&
            (uri.path.isNullOrEmpty() || uri.path == "/")
        require(universal || custom)
        val pairs = uri.rawQuery.orEmpty()
            .takeIf(String::isNotEmpty)
            ?.split('&')
            ?.map { item ->
                val pieces = item.split('=', limit = 2)
                decode(pieces[0]) to decode(pieces.getOrElse(1) { "" })
            }
            .orEmpty()
        require(pairs.size <= 6)
        pairs.forEach { (name, value) ->
            require(name.length <= 32 && value.length <= 128)
            require(name.lowercase() in allowedQueryKeys)
            require(value.none(Char::isISOControl))
        }
        val invitations = pairs.filter { it.first.lowercase() in setOf("code", "invite_code", "invitecode") }
        val companies = pairs.filter { it.first.lowercase() in setOf("company_code", "companycode") }
        require(invitations.size == 1 && companies.size <= 1)
        return XingDunQRCodeRoute.Invitation(
            invitationCode(invitations.single().second),
            companyCode(companies.firstOrNull()?.second)
        )
    }

    private fun identifier(value: String?): String {
        val normalized = value?.trim().orEmpty()
        require(normalized.isNotEmpty() && normalized.toByteArray().size <= 128)
        return normalized
    }

    private fun invitationCode(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty()
        require(invitationPattern.matches(normalized))
        return normalized
    }

    private fun companyCode(value: String?): String? {
        if (value == null) return null
        val normalized = value.trim()
        require(companyPattern.matches(normalized))
        return normalized
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.int(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.asInt
}
