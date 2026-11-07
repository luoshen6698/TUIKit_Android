package io.trtc.tuikit.chat.demo.xingdun.features.travel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TravelRepositoryTest {
    private fun fixture(): JsonObject = JsonParser.parseString(javaClass.getResource("/travel/editorial.json")!!.readText()).asJsonObject
    @Test fun fullReferenceCatalogAndUserReceiptParse() {
        val c=TravelCatalog.parse(fixture())
        assertEquals(59,c.routes.size);assertEquals(18,c.matches.size);assertTrue(c.articles.isNotEmpty())
        val a=c.articles.first();val raw=fixture();raw.add("user_state",travelBody("article_likes" to travelBody(a.str("id") to true)))
        val selected=TravelCatalog.parse(raw);assertEquals(a.count("likes")+1,selected.likeCount(selected.articles.first()))
    }
    @Test fun unsafeMediaAndBrokenRelationsAreRejected() {
        for(path in listOf("http://example.com/a.jpg","assets/tuxing/../secret","https://user:password@example.com/a")) {
            val raw=fixture();raw.obj("home").addProperty("hero_image",path)
            assertTrue(runCatching{TravelCatalog.parse(raw)}.isFailure)
        }
        val raw=fixture();raw.rows("routes").first().addProperty("city_id","missing")
        assertTrue(runCatching{TravelCatalog.parse(raw)}.isFailure)
    }
    @Test fun unknownSchemaAndDuplicateIdsAreRejected() {
        val raw=fixture();raw.addProperty("schema_version",2);assertTrue(runCatching{TravelCatalog.parse(raw)}.isFailure)
        val duplicate=fixture();duplicate.getAsJsonArray("articles").add(duplicate.rows("articles").first().deepCopy());assertTrue(runCatching{TravelCatalog.parse(duplicate)}.isFailure)
    }
    @Test fun transientRetryUsesOneRequestIdAndPublishesOnlyServerReceipt() = runBlocking {
        val ids=mutableListOf<String>();var fail=true
        val repo=TravelRepository({"account-a"},{body->ids+=body!!.str("request_id");if(fail){fail=false;throw java.io.IOException("offline")};fixture()})
        val body=travelBody("action" to "save_intent","intent" to travelBody("destination" to "杭州"))
        assertTrue(runCatching{repo.action(body)}.isFailure);assertNull(repo.current)
        repo.action(body);assertEquals(ids[0],ids[1]);assertNotNull(repo.current)
        repo.action(body);assertNotEquals(ids[1],ids[2])
    }
    @Test fun oldAccountResponseNeverBecomesNewAccountContent() = runBlocking {
        var account="a";val gate=CompletableDeferred<Unit>()
        val repo=TravelRepository({account},{gate.await();fixture()})
        val old=async{runCatching{repo.load()}};yield();account="b";gate.complete(Unit)
        assertTrue(old.await().isFailure);assertNull(repo.current)
        repo.load();assertNotNull(repo.current);account="c";assertNull(repo.current)
    }
    @Test fun oldGetCompletesBeforeMutationAndCannotReplaceReceipt() = runBlocking {
        val gate=CompletableDeferred<Unit>();val order=mutableListOf<String>()
        val repo=TravelRepository({"a"},{body->if(body==null){order+="get";gate.await();fixture()}else{order+="write";fixture().apply{addProperty("revision","write-receipt")}}})
        val get=launch{repo.load()};yield();val write=launch{repo.action(travelBody("action" to "match","id" to "match_1","selected" to true))};yield()
        assertEquals(listOf("get"),order);gate.complete(Unit);get.join();write.join()
        assertEquals(listOf("get","write"),order);assertEquals("write-receipt",repo.current!!.revision)
    }
    @Test fun cachedLoadSkipsNetworkButForceRefreshes() = runBlocking {
        var requests=0;var now=1000L
        val repo=TravelRepository({"a"},{requests++;fixture()},{now})
        repo.load();repo.load();assertEquals(1,requests);repo.load(true);assertEquals(2,requests);now+=300_001;repo.load();assertEquals(3,requests)
    }
    @Test fun failedRefreshKeepsLastGoodSnapshot() = runBlocking {
        var fail=false;val repo=TravelRepository({"a"},{if(fail)throw java.io.IOException();fixture()})
        repo.load();val previous=repo.current;fail=true;assertTrue(runCatching{repo.load(true)}.isFailure);assertSame(previous,repo.current)
    }
    @Test fun queuedWriteIsRejectedIfAccountChangesWhileWaitingForGet() = runBlocking {
        var account="a";var writes=0;val gate=CompletableDeferred<Unit>()
        val repo=TravelRepository({account},{body->if(body==null)gate.await()else writes++;fixture()})
        val get=async{runCatching{repo.load()}};yield()
        val write=async{runCatching{repo.action(travelBody("action" to "save_intent"))}};yield()
        account="b";gate.complete(Unit);get.await()
        assertTrue(write.await().isFailure);assertEquals(0,writes);assertNull(repo.current)
    }

    @Test fun typographyMatchesReferenceAndroidNormalization() {
        listOf(0f to 0f, 9f to 10f, 11f to 12f, 16f to 12f, 17f to 18f, 20f to 18f, 21f to 22f, 24f to 22f, 26f to 26f, 30f to 30f).forEach{(input,expected)->assertEquals(expected,TravelTypography.size(input),0f)}
    }

    @Test fun businessErrorWithEmptyPhpArrayCanReachApiErrorHandling() {
        val type=com.google.gson.reflect.TypeToken.getParameterized(io.trtc.tuikit.chat.demo.xingdun.network.XingDunEnvelope::class.java,com.google.gson.JsonElement::class.java).type
        val error:io.trtc.tuikit.chat.demo.xingdun.network.XingDunEnvelope<com.google.gson.JsonElement> = com.google.gson.Gson().fromJson("""{"code":400,"message":"Invalid dates","data":[]}""",type)
        assertEquals(400,error.code);assertTrue(error.data!!.isJsonArray)
    }

    @Test fun calendarValidationRejectsImpossibleDatesWithoutApi26Classes() {
        assertTrue(TravelDates.validDate("2028-02-29"));assertTrue(TravelDates.validDate("2026-12-31"))
        listOf("2026-02-29","2026-02-30","2026-1-01","2026-01-01extra").forEach{assertFalse(TravelDates.validDate(it))}
    }
    @Test fun serverUtcCommentTimesParseStrictlyWithAndWithoutMilliseconds() {
        assertEquals(1000L,TravelDates.utcTimestamp("1970-01-01T00:00:01Z")!!.time)
        assertEquals(1234L,TravelDates.utcTimestamp("1970-01-01T00:00:01.234Z")!!.time)
        assertNull(TravelDates.utcTimestamp("2026-02-30T10:00:00Z"));assertNull(TravelDates.utcTimestamp("2026-01-01T10:00:00"))
    }

}
