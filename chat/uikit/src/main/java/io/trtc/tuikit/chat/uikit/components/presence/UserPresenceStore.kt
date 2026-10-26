package io.trtc.tuikit.chat.uikit.components.presence

import android.os.Handler
import android.os.Looper
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMUserStatus
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

enum class UserPresence { UNKNOWN, ONLINE, OFFLINE }

/** Visible consumers share a bounded subscription; failure stays UNKNOWN. Main-thread only. */
object UserPresenceStore {
    private val main = Handler(Looper.getMainLooper())
    private val observers = linkedMapOf<Any, Pair<String, (UserPresence) -> Unit>>()
    private val statuses = mutableMapOf<String, UserPresence>()
    private var subscribed = emptySet<String>()
    private var identity = ""
    private var sdkIdentity = ""
    private var generation = 0L
    private var listening = false
    private var blocked = false
    private var pending: Runnable? = null
    private var accountScope: () -> String = { "" }
    private var refreshTimer: Runnable? = null
    private fun currentSdkIdentity() = "${LoginStore.shared.sdkAppID}:${V2TIMManager.getInstance().loginUser.orEmpty()}"
    private fun currentIdentity() = "${accountScope()}:${currentSdkIdentity()}"

    fun configureAccountScope(provider: () -> String) { reset(); accountScope = provider }

    private val listener = object : V2TIMSDKListener() {
        override fun onUserStatusChanged(values: MutableList<V2TIMUserStatus>?) { main.post {
            if (identity != currentIdentity() || blocked) return@post
            values.orEmpty().filter { it.userID in subscribed }.forEach { status ->
                statuses[status.userID] = when (status.statusType) {
                    V2TIMUserStatus.V2TIM_USER_STATUS_ONLINE -> UserPresence.ONLINE
                    V2TIMUserStatus.V2TIM_USER_STATUS_OFFLINE, V2TIMUserStatus.V2TIM_USER_STATUS_UNLOGINED -> UserPresence.OFFLINE
                    else -> UserPresence.UNKNOWN
                }
            }
            publish()
        } }
        override fun onConnecting() { main.post { invalidate(false) } }
        override fun onConnectSuccess() { main.post { blocked = false; schedule() } }
        override fun onConnectFailed(code: Int, desc: String?) { main.post { invalidate(false) } }
        override fun onKickedOffline() { main.post { invalidate(true) } }
        override fun onUserSigExpired() { main.post { invalidate(true) } }
    }

    fun observe(owner: Any, userID: String, callback: (UserPresence) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (identity != currentIdentity()) { invalidate(false); identity = currentIdentity() }
        sdkIdentity = currentSdkIdentity()
        observers[owner] = userID to callback
        callback(statuses[userID] ?: UserPresence.UNKNOWN)
        if (!listening) { V2TIMManager.getInstance().addIMSDKListener(listener); listening = true }
        schedule()
    }

    fun release(owner: Any) { observers.remove(owner); schedule() }

    fun refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { refresh() }; return }
        if (identity != currentIdentity()) { reset(); return }
        invalidate(false)
        schedule()
    }

    fun reset() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { reset() }; return }
        if (subscribed.isNotEmpty() && sdkIdentity == currentSdkIdentity()) {
            V2TIMManager.getInstance().unsubscribeUserStatus(subscribed.toList(), null)
        }
        invalidate(true)
        observers.clear()
        if (listening) V2TIMManager.getInstance().removeIMSDKListener(listener)
        listening = false
        identity = ""
    }

    private fun invalidate(stop: Boolean) {
        generation++
        pending?.let(main::removeCallbacks)
        pending = null
        refreshTimer?.let(main::removeCallbacks)
        refreshTimer = null
        statuses.clear()
        subscribed = emptySet()
        blocked = stop
        publish()
    }

    private fun schedule() {
        pending?.let(main::removeCallbacks)
        pending = Runnable { reconcile() }.also { main.postDelayed(it, 250) }
    }

    private fun reconcile() {
        pending = null
        if (blocked || identity != currentIdentity()) return
        // Native SDK permits 100 users per subscription call. Cap the visible set at 100.
        val wanted = observers.values.map { it.first }.filter { it.isNotBlank() && it != V2TIMManager.getInstance().loginUser }.distinct().take(100).toSet()
        val epoch = generation
        val removing = subscribed - wanted
        if (removing.isNotEmpty()) V2TIMManager.getInstance().unsubscribeUserStatus(removing.toList(), null)
        val adding = wanted - subscribed
        subscribed = wanted
        statuses.keys.retainAll(wanted)
        if (wanted.isEmpty()) {
            if (listening) V2TIMManager.getInstance().removeIMSDKListener(listener)
            listening = false
            return
        }
        refreshTimer?.let(main::removeCallbacks)
        refreshTimer = Runnable { refresh() }.also { main.postDelayed(it, 30_000L) }
        fun current() = epoch == generation && identity == currentIdentity() && !blocked
        fun query(ids: List<String>) {
            V2TIMManager.getInstance().getUserStatus(ids, object : V2TIMValueCallback<List<V2TIMUserStatus>> {
                override fun onSuccess(values: List<V2TIMUserStatus>?) { main.post {
                    if (!current()) return@post
                    values.orEmpty().filter { it.userID in subscribed }.forEach { status ->
                        statuses[status.userID] = when (status.statusType) {
                            V2TIMUserStatus.V2TIM_USER_STATUS_ONLINE -> UserPresence.ONLINE
                            V2TIMUserStatus.V2TIM_USER_STATUS_OFFLINE, V2TIMUserStatus.V2TIM_USER_STATUS_UNLOGINED -> UserPresence.OFFLINE
                            else -> UserPresence.UNKNOWN
                        }
                    }
                    publish()
                } }
                override fun onError(code: Int, desc: String?) { main.post {
                    if (!current()) return@post
                    ids.forEach { statuses.remove(it) }; publish()
                } }
            })
        }
        if (adding.isNotEmpty()) V2TIMManager.getInstance().subscribeUserStatus(adding.toList(), object : V2TIMCallback {
            override fun onSuccess() { main.post {
                if (!current()) return@post
                val expired = adding - subscribed
                if (expired.isNotEmpty()) V2TIMManager.getInstance().unsubscribeUserStatus(expired.toList(), null)
                val active = adding.intersect(subscribed).toList()
                if (active.isNotEmpty()) query(active)
            } }
            override fun onError(code: Int, desc: String?) { main.post {
                if (!current()) return@post
                adding.forEach { statuses.remove(it) }; publish()
            } }
        })
    }

    private fun publish() { observers.values.toList().forEach { (user, callback) -> callback(statuses[user] ?: UserPresence.UNKNOWN) } }
}
