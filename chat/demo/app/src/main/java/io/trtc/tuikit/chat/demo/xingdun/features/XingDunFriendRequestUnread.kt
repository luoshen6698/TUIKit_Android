package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class FriendRequestUnread(val owner: String?, val count: Int) {
    fun forOwner(current: String?) = if(current != null && owner == current) count else 0
}

/** Reads and acknowledgements are serialized so a stale GET cannot restore a cleared badge. */
internal class FriendRequestUnreadRepository(
    private val scope: () -> String?,
    private val request: suspend (String) -> Int,
    private val acknowledge: suspend (String) -> Unit,
) {
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(FriendRequestUnread(null,0))
    val state=mutable.asStateFlow()
    suspend fun refresh() {
        val owner=scope()
        if(mutable.value.owner != owner) mutable.value=FriendRequestUnread(owner,0)
        if(owner==null)return
        mutex.withLock {
            if(scope()!=owner)return@withLock
            val count=request(owner)
            require(count>=0)
            if(scope()==owner)mutable.value=FriendRequestUnread(owner,count)
        }
    }
    suspend fun markRead(owner:String) = mutex.withLock {
        if(scope()!=owner)return@withLock
        acknowledge(owner)
        if(scope()==owner)mutable.value=FriendRequestUnread(owner,0)
    }
}

internal object FriendRequestBadgePolicy {
    // The server and SDK can describe the same request; never count it twice.
    fun friends(nativeCount:Int,server:FriendRequestUnread,owner:String?) = maxOf(nativeCount.coerceAtLeast(0),server.forOwner(owner))
    fun total(chatCount:Long,friendCount:Int) = (chatCount.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()) + friendCount.coerceAtLeast(0).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    fun parse(data:JsonObject):Int {
        val v=data.get("unread_count")
        require(v!=null && v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asString.matches(Regex("0|[1-9][0-9]*")))
        return requireNotNull(v.asString.toIntOrNull())
    }
}

internal object XingDunFriendRequestUnread {
    private fun session(owner:String):io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession {
        check(XingDunSessionManager.currentUiSession()?.scopeKey==owner)
        val session=checkNotNull(XingDunSessionManager.currentSession())
        check(XingDunSessionManager.currentUiSession()?.scopeKey==owner)
        return session
    }
    val repository=FriendRequestUnreadRepository(
        scope={XingDunSessionManager.currentUiSession()?.scopeKey},
        request={owner->withContext(Dispatchers.IO) {
            val data=XingDunSessionManager.apiClient().get<JsonObject>(session(owner),"friend/applyUnreadCount",emptyMap(),JsonObject::class.java)
            FriendRequestBadgePolicy.parse(data)
        }},
        acknowledge={owner->withContext(Dispatchers.IO) {
            XingDunSessionManager.apiClient().postEmpty(session(owner),"friend/readApply",emptyMap<String,String>())
        }},
    )
}
