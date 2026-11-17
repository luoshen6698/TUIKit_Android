package io.trtc.tuikit.chat.demo.xingdun.features.travel

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Server-compatible private plans; sharing sends an explicit snapshot, never grants edit rights. */
internal object TravelPlans {
    fun duration(start: String, end: String): Int {
        if (!TravelDates.validDate(start) || !TravelDates.validDate(end) || start.take(4) !in "2020".."2100" || end.take(4) !in "2020".."2100") return 0
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val days = ((format.parse(end)!!.time - format.parse(start)!!.time) / 86400000L) + 1
        return if (days in 1..60) days.toInt() else 0
    }
    fun date(start: String, offset: Int): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("UTC") }
        return f.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time=f.parse(start)!!; add(Calendar.DATE,offset) }.time)
    }
    fun days(plan: JsonObject, count: Int): JsonArray = JsonArray().apply {
        val previous = plan.rows("days")
        repeat(count) { i -> add(previous.getOrNull(i)?.deepCopy() ?: travelBody("day" to i+1,"title" to "","notes" to "")) }
    }
    fun newPlan(source: JsonObject? = null, allowedStyles: Collection<String> = emptyList()): JsonObject {
        val today=SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date())
        val length=source?.let{duration(it.opt("start_date"),it.opt("end_date"))}?.takeIf{it>0} ?: 2
        val budget=source?.opt("budget").orEmpty().removePrefix("¥").takeIf(::validBudget).orEmpty()
        return travelBody("id" to "", "version" to 0, "title" to (source?.opt("title") ?: ""),
            "city" to (source?.opt("city") ?: ""), "start_date" to today, "end_date" to date(today,length-1),
            "budget" to budget, "description" to (source?.opt("description") ?: ""),
            "status" to "计划中", "tags" to stringArray(source?.strings("tags").orEmpty().filter { it in allowedStyles }), "days" to JsonArray())
    }
    fun editable(row: JsonObject): JsonObject = row.deepCopy().apply {
        addProperty("version", row.get("version")?.asInt ?: 1)
        addProperty("budget", if (row.get("budget_specified")?.let { it.isJsonPrimitive && !it.asBoolean } == true) "" else row.opt("budget").removePrefix("¥"))
    }
    fun validBudget(value: String): Boolean {
        if(value.isBlank()) return true
        val m=Regex("[0-9]{1,7}(?:-[0-9]{1,7})?").matchEntire(value.trim()) ?: return false
        val values=m.value.split('-').map { it.toLong() }; return values.size==1 || values[0]<=values[1]
    }
    fun summary(plan: JsonObject, heading: String, dayLabel: (Int)->String): String = buildString {
        append(heading).append('\n').append(plan.str("title")).append('\n')
        append(plan.str("city")).append(" · ").append(plan.str("dates")).append('\n')
        append(plan.str("budget")).append(" · ").append(plan.str("status")).append('\n')
        if(plan.opt("description").isNotBlank()) append(plan.opt("description")).append('\n')
        plan.rows("days").forEach { day -> append('\n').append(dayLabel(day.count("day"))).append(" · ").append(day.opt("title")).append('\n').append(day.opt("notes")).append('\n') }
    }.trim()
}
