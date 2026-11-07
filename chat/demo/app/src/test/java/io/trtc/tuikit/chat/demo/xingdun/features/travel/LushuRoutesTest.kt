package io.trtc.tuikit.chat.demo.xingdun.features.travel

import com.google.gson.JsonParser
import com.google.gson.JsonArray
import org.junit.Assert.*
import org.junit.Test

class LushuRoutesTest {
    private fun raw() = JsonParser.parseString(javaClass.getResource("/travel/editorial.json")!!.readText()).asJsonObject
    @Test fun cityCountsPartitionAllServerRoutesAndTimelineIsChronological() {
        val c = TravelCatalog.parse(raw())
        assertEquals(20, c.cities.size)
        assertEquals(c.routes.size, c.cities.sumOf { LushuRoutes.inCity(c, it.str("id")).size })
        assertEquals(4, LushuRoutes.inCity(c, "city_2").size)
        assertEquals(c.routes.map { it.str("start_date") }.sorted(), LushuRoutes.visible(c, false, null).map { it.str("start_date") })
        assertEquals(listOf("推荐"), LushuRoutes.filters(c, false))
        assertTrue(LushuRoutes.visible(c, false, "已结束").isEmpty())
    }
    @Test fun ownPlanIsSeparateAndFilterOptionsFollowServerOrdering() {
        val d = raw(); val own = d.rows("routes").first().deepCopy().apply { addProperty("id", "own_1"); addProperty("mine", true); addProperty("status", "计划中") }
        d.add("own_route", own)
        val c = TravelCatalog.parse(d)
        assertEquals(listOf("own_1"), LushuRoutes.visible(c, true, null).map { it.str("id") })
        assertEquals(listOf("计划中"), LushuRoutes.filters(c, true))
        assertFalse(LushuRoutes.visible(c, false, null).any { it.str("id") == "own_1" })
        assertTrue(LushuRoutes.visible(c, true, "推荐").isEmpty())
        assertTrue(LushuRoutes.visible(TravelCatalog.parse(raw()), true, null).isEmpty())
    }
    @Test fun malformedCityCoordinatesFailBeforeRendering() {
        listOf("[]", "[1]", "[1,2,3]", "[true,2]", "[\"10\",2]", "[-1,2]", "[1,721]").forEach { point ->
            val d = raw(); d.rows("cities").first().add("map_point", JsonParser.parseString(point))
            assertTrue(point, runCatching { TravelCatalog.parse(d) }.isFailure)
        }
    }
    @Test fun emptyEditorialCatalogStillSupportsMyPlanAndAllFilter() {
        val d = raw(); listOf("cities", "routes", "matches").forEach { d.add(it, JsonArray()) }
        val c = TravelCatalog.parse(d)
        assertTrue(LushuRoutes.filters(c, false).isEmpty()); assertTrue(LushuRoutes.visible(c, true, null).isEmpty())
    }
}
