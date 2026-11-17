package io.trtc.tuikit.chat.demo.xingdun.features.travel

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TravelPlansTest {
    @Test fun datesRespectLeapYearsOrderAndSixtyDayLimit() {
        assertEquals(2,TravelPlans.duration("2028-02-28","2028-02-29"))
        assertEquals(0,TravelPlans.duration("2026-02-29","2026-03-01"))
        assertEquals(0,TravelPlans.duration("2026-12-02","2026-12-01"))
        assertEquals(60,TravelPlans.duration("2026-01-01","2026-03-01"))
        assertEquals(0,TravelPlans.duration("2026-01-01","2026-03-02"))
        assertEquals("2027-01-01",TravelPlans.date("2026-12-31",1))
    }
    @Test fun resizingPreservesExistingDaysAndDoesNotMutateSavedPlan() {
        val original=TravelPlans.newPlan().apply { add("days",JsonArray().apply{add(travelBody("day" to 1,"title" to "Meet","notes" to "09:00 station"))}) }
        val extended=TravelPlans.days(original,3)
        assertEquals("09:00 station",extended[0].asJsonObject.str("notes"))
        assertEquals(3,extended[2].asJsonObject.count("day"))
        extended[0].asJsonObject.addProperty("notes","Changed")
        assertEquals("09:00 station",original.rows("days")[0].str("notes"))
    }
    @Test fun multiplePlansAndLegacyFallbackAreKeptSeparateFromEditorialRoutes() {
        val raw=JsonParser.parseString(javaClass.getResource("/travel/editorial.json")!!.readText()).asJsonObject
        val one=raw.rows("routes").first().deepCopy().apply {addProperty("id","plan_one");addProperty("mine",true)}
        val two=one.deepCopy().apply {addProperty("id","plan_two");addProperty("status","已完成")}
        raw.add("plans",JsonArray().apply{add(one);add(two)})
        val c=TravelCatalog.parse(raw)
        assertEquals(2,LushuRoutes.visible(c,true,null).size)
        assertEquals(two,c.route("plan_two"))
        assertTrue(LushuRoutes.filters(c,true).contains("已完成"))
        assertFalse(LushuRoutes.visible(c,false,null).any{it.str("id")=="plan_two"})
    }
    @Test fun editorialTemplateKeepsDurationAndBudgetWithoutReusingPastDatesOrIdentity() {
        val source=travelBody("id" to "guide_one","title" to "Lake trip","city" to "杭州","start_date" to "2025-01-01","end_date" to "2025-01-04","budget" to "¥1400-1950")
        val plan=TravelPlans.newPlan(source)
        assertEquals(4,TravelPlans.duration(plan.str("start_date"),plan.str("end_date")))
        assertEquals("1400-1950",plan.str("budget"));assertEquals("",plan.str("id"));assertEquals(0,plan.count("version"))
        assertNotEquals("2025-01-01",plan.str("start_date"))
    }
    @Test fun routeDisplayTagsCannotBecomeUnsupportedPrivateTravelStyles() {
        val source=travelBody("tags" to stringArray(listOf("城市漫游","湖景","散步")))
        assertEquals(listOf("城市漫游"),TravelPlans.newPlan(source,listOf("城市漫游","摄影")).strings("tags"))
        assertTrue(TravelPlans.newPlan(source).strings("tags").isEmpty())
    }
    @Test fun budgetAllowsUnspecifiedAndZeroButRejectsInvalidRanges() {
        listOf("","0","800","800-1500").forEach{assertTrue(it,TravelPlans.validBudget(it))}
        listOf("-1","1500-800","free","1.5","100000000").forEach{assertFalse(it,TravelPlans.validBudget(it))}
    }
    @Test fun sharingIncludesAllDaysAndDoesNotChangeOwnerVersion() {
        val p=travelBody("title" to "Weekend","city" to "Hangzhou","dates" to "2026.12.1 - 2026.12.2","budget" to "¥800-1500","status" to "计划中","description" to "Trip","version" to 4,
            "days" to JsonArray().apply{add(travelBody("day" to 1,"title" to "Lake","notes" to "Lin: tickets"));add(travelBody("day" to 2,"title" to "Museum","notes" to "09:30 meet"))})
        val before=p.toString();val shared=TravelPlans.summary(p,"Shared"){"Day $it"}
        assertTrue(shared.contains("Day 2 · Museum"));assertTrue(shared.contains("Lin: tickets"));assertTrue(shared.contains("09:30 meet"));assertEquals(before,p.toString())
    }
}
