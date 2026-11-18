package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FriendRequestUnreadTest {
    @Test fun chatReadAllDoesNotClearFriendRequestsAndDuplicateSourcesAreMerged() {
        val f=FriendRequestBadgePolicy.friends(2,FriendRequestUnread("a",2),"a")
        assertEquals(2,f);assertEquals(7,FriendRequestBadgePolicy.total(5,f))
        assertEquals(2,FriendRequestBadgePolicy.total(0,f))
        assertEquals(0,FriendRequestBadgePolicy.total(0,0))
        assertEquals(Int.MAX_VALUE,FriendRequestBadgePolicy.total(Long.MAX_VALUE,12))
    }
    @Test fun serverUnreadBelongsToOneAccountAndAuthenticationGeneration() {
        val unread=FriendRequestUnread("api|tenant|a|generation-1",4)
        assertEquals(4,unread.forOwner("api|tenant|a|generation-1"))
        assertEquals(0,unread.forOwner("api|tenant|b|generation-1"))
        assertEquals(0,unread.forOwner("api|tenant|a|generation-2"))
        assertEquals(0,unread.forOwner(null))
    }
    @Test fun counterAcceptsOnlyNonnegativeIntegerServerValues() {
        listOf("0","1","250","2147483647").forEach { assertEquals(it.toInt(),FriendRequestBadgePolicy.parse(JsonParser.parseString("{\"unread_count\":$it}").asJsonObject)) }
        listOf("-1","1.2","true","null","\"2\"","2147483648").forEach {
            assertThrows(IllegalArgumentException::class.java) { FriendRequestBadgePolicy.parse(JsonParser.parseString("{\"unread_count\":$it}").asJsonObject) }
        }
    }
    @Test fun failedRefreshAndFailedAcknowledgementKeepTheReminder() = runBlocking {
        var fail=false
        val repo=FriendRequestUnreadRepository({"a"},{if(fail)throw java.io.IOException();3},{if(fail)throw java.io.IOException()})
        repo.refresh();fail=true
        assertTrue(runCatching{repo.refresh()}.isFailure);assertEquals(3,repo.state.value.forOwner("a"))
        assertTrue(runCatching{repo.markRead("a")}.isFailure);assertEquals(3,repo.state.value.forOwner("a"))
        fail=false;repo.markRead("a");assertEquals(0,repo.state.value.forOwner("a"))
    }
    @Test fun oldResponseNeverLeaksToAnotherAccount() = runBlocking {
        var owner:String?="a";val gate=CompletableDeferred<Unit>()
        val repo=FriendRequestUnreadRepository({owner},{gate.await();5},{})
        val load=launch{repo.refresh()};yield();owner="b";gate.complete(Unit);load.join()
        assertEquals(0,repo.state.value.forOwner("b"))
        repo.refresh();assertEquals(5,repo.state.value.forOwner("b"))
        owner=null;repo.refresh();assertEquals(FriendRequestUnread(null,0),repo.state.value)
    }
    @Test fun clearingWaitsForInFlightReadAndCannotBeOverwrittenByIt() = runBlocking {
        val gate=CompletableDeferred<Unit>();val order=mutableListOf<String>()
        val repo=FriendRequestUnreadRepository({"a"},{order+="get";gate.await();2},{order+="read"})
        val refresh=launch{repo.refresh()};yield();val clear=launch{repo.markRead("a")};yield()
        assertEquals(listOf("get"),order);gate.complete(Unit);refresh.join();clear.join()
        assertEquals(listOf("get","read"),order);assertEquals(0,repo.state.value.forOwner("a"))
    }
    @Test fun queuedOldAccountReadDoesNotAcknowledgeTheNewAccount() = runBlocking {
        var owner="a";val gate=CompletableDeferred<Unit>();var clears=0
        val repo=FriendRequestUnreadRepository({owner},{gate.await();1},{clears++})
        val get=launch{repo.refresh()};yield();val read=launch{repo.markRead("a")};yield()
        owner="b";gate.complete(Unit);get.join();read.join();assertEquals(0,clears)
    }
    @Test fun anotherDeviceCanClearUnreadOnTheNextSuccessfulRefresh() = runBlocking {
        var server=1;val repo=FriendRequestUnreadRepository({"a"},{server},{})
        repo.refresh();assertEquals(1,repo.state.value.count)
        server=0;repo.refresh();assertEquals(0,repo.state.value.count)
    }
}
