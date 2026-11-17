package io.trtc.tuikit.chat.demo.xingdun.features.travel

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URI

internal fun JsonObject.str(key: String): String = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    ?: throw IllegalArgumentException("Invalid travel field: $key")
internal fun JsonObject.opt(key: String): String = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
internal fun JsonObject.obj(key: String): JsonObject = get(key)?.takeUnless { it.isJsonNull }?.let { value ->
    when { value.isJsonObject -> value.asJsonObject; value.isJsonArray && value.asJsonArray.size() == 0 -> JsonObject(); else -> error("Invalid travel object: $key") }
} ?: JsonObject()
internal fun JsonObject.rows(key: String): List<JsonObject> = (get(key) ?: JsonArray()).let { v ->
    require(v.isJsonArray) { "Invalid travel list: $key" }; v.asJsonArray.map { require(it.isJsonObject); it.asJsonObject }
}
internal fun JsonObject.strings(key: String): List<String> = (get(key) ?: JsonArray()).let { v ->
    require(v.isJsonArray); v.asJsonArray.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }
}
internal fun JsonObject.flag(key: String): Boolean = get(key)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
internal fun JsonObject.count(key: String): Int { val v = get(key); require(v != null && v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asString.matches(Regex("[0-9]+"))); return v.asInt.also { require(it >= 0) } }
internal fun JsonObject.creator(fallback: String): String = if (opt("creator_kind") == "virtual_guide") str("creator_nickname") else str(fallback)
internal fun JsonObject.guide() = opt("content_kind") == "platform_route"
internal fun JsonObject.media(key: String): String = str(key).also { require(safeTravelMedia(it)) { "Invalid travel media" } }
internal fun safeTravelMedia(value: String): Boolean =
    (value.startsWith("assets/tuxing/") && !value.contains("..") && !value.contains("://") && !value.contains('\\')) ||
    runCatching { URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
internal fun travelId(row: JsonObject): String = row.str("id").also { require(it.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) }
internal fun travelBody(vararg fields: Pair<String, Any?>): JsonObject = JsonObject().apply {
    fields.forEach { (key, value) -> when (value) { null -> add(key, com.google.gson.JsonNull.INSTANCE); is JsonElement -> add(key, value); is Boolean -> addProperty(key, value); is Number -> addProperty(key, value); else -> addProperty(key, value.toString()) } }
}
internal fun stringArray(values: Collection<String>) = JsonArray().apply { values.forEach { add(it) } }

/** The versioned tenant document is kept intact, including fields added by newer servers. */
internal class TravelCatalog private constructor(val raw: JsonObject) {
    val revision = raw.str("revision")
    val home = raw.obj("home")
    val calendar = raw.obj("calendar")
    val state = raw.obj("user_state")
    val intent = raw.get("intent")?.takeIf { it.isJsonObject }?.asJsonObject
    val ownRoute = raw.get("own_route")?.takeIf { it.isJsonObject }?.asJsonObject
    val plans = if (raw.has("plans")) raw.rows("plans").sortedBy { it.str("start_date") } else listOfNotNull(ownRoute)
    val articles = raw.rows("articles")
    val routes = raw.rows("routes").sortedBy { it.str("start_date") }
    val cities = raw.rows("cities")
    val statuses = raw.strings("status_options")
    val matches = raw.rows("matches")
    val templates = raw.rows("intent_templates")
    val styles = raw.strings("style_options")
    val reportReasons = raw.strings("report_reasons")
    fun selected(key: String, id: String) = state.obj(key).flag(id)
    fun article(id: String) = articles.find { it.str("id") == id }
    fun route(id: String) = plans.find { it.str("id") == id } ?: ownRoute?.takeIf { it.str("id") == id } ?: routes.find { it.str("id") == id }
    fun articleComments(id: String): List<JsonObject> {
        val article = article(id) ?: return emptyList()
        val extra = state.obj("comments").rows(id)
        return (extra.filter { it.opt("parent_id").isEmpty() } + article.rows("messages")).map { row ->
            row.deepCopy().apply { add("replies", JsonArray().apply {
                (row.rows("replies") + extra.filter { it.opt("parent_id") == row.str("id") }).forEach { add(it.deepCopy()) }
            }) }
        }.filterNot { selected("hidden_comments", it.str("id")) }
    }
    fun commentCount(article: JsonObject) = article.count("comments") + state.obj("comments").rows(article.str("id")).size
    fun likeCount(article: JsonObject) = article.count("likes") + if (selected("article_likes", article.str("id"))) 1 else 0
    companion object {
        fun parse(raw: JsonObject): TravelCatalog {
            require(raw.count("schema_version") == 1) { "Unsupported travel schema" }
            val result = TravelCatalog(raw.deepCopy())
            listOf("title", "subtitle", "hero_title", "hero_subtitle").forEach { result.home.str(it) }
            listOf("hero_image", "intent_image", "editor_image").forEach { result.home.media(it) }
            listOf("day", "month", "weekday", "detail").forEach { result.calendar.str(it) }
            val cities = result.cities; val cityIds = cities.map { travelId(it) }.toSet()
            listOf(result.articles, result.routes, result.matches, cities, result.templates).forEach { rows -> require(rows.map(::travelId).distinct().size == rows.size) }
            cities.forEach { city ->
                city.str("name")
                val point = city.get("map_point")
                require(point != null && point.isJsonArray && point.asJsonArray.size() == 2)
                point.asJsonArray.forEach { v -> require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asDouble.isFinite() && v.asDouble in 0.0..720.0) }
            }
            (result.routes + result.matches).forEach { require(it.opt("city_id") in cityIds) }
            fun route(row: JsonObject) {
                travelId(row)
                listOf("title", "city", "province", "dates", "owner", "status", "budget", "description", "owner_bio", "note", "action").forEach { row.str(it) }
                require(TravelDates.validDate(row.str("start_date")) && TravelDates.validDate(row.str("end_date")) && row.str("start_date") <= row.str("end_date"))
                row.media("photo"); row.media("avatar"); row.count("remaining"); row.strings("tags")
                listOf("mine", "profile").forEach { require(row[it]?.isJsonPrimitive == true && row[it].asJsonPrimitive.isBoolean) }
                row.creator("owner")
            }
            result.routes.forEach(::route); result.ownRoute?.let(::route); result.plans.forEach(::route)
            result.articles.forEach { row ->
                listOf("destination", "title", "description", "time", "city").forEach { row.str(it) }
                row.creator("author"); row.media("image"); row.media("avatar"); row.count("likes"); row.count("comments"); row.strings("paragraphs"); row.strings("tags")
                fun comment(c: JsonObject) { travelId(c); c.str("author"); c.str("text"); c.media("avatar"); c.count("likes"); c.rows("replies").forEach { comment(it) } }
                row.rows("messages").forEach(::comment); result.state.obj("comments").rows(row.str("id")).forEach(::comment)
            }
            result.matches.forEach { row -> row.media("photo"); row.media("avatar"); row.creator("author"); require(row.count("score") <= 100); listOf("city", "dates", "home", "reason").forEach { row.str(it) }; row.strings("tags") }
            (result.templates + listOfNotNull(result.intent)).forEach { row -> listOf("destination", "dates", "description").forEach { row.str(it) }; row.strings("styles") }
            result.reportReasons; raw.strings("status_options")
            return result
        }
    }
}
