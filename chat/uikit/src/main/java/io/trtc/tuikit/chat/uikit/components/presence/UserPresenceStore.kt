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

/** Visible consumers share one account-scoped controller. Main-thread only. */
object UserPresenceStore {
    private val main = Handler(Looper.getMainLooper())
    private var accountScope: () -> String = { "" }
    private val controller = UserPresenceController(object : UserPresenceController.Runtime {
        override fun sdkIdentity() = "${LoginStore.shared.sdkAppID}:${selfUserID()}"
        override fun identity() = "${accountScope()}:${sdkIdentity()}"
        override fun selfUserID() = V2TIMManager.getInstance().loginUser.orEmpty()
        override fun dispatch(action: () -> Unit) { main.post { action() } }
        override fun later(delayMillis: Long, action: () -> Unit): () -> Unit {
            val task = Runnable { action() }; main.postDelayed(task, delayMillis)
            return { main.removeCallbacks(task) }
        }
        override fun listen(events: UserPresenceController.Events): () -> Unit {
            val listener = object : V2TIMSDKListener() {
                override fun onUserStatusChanged(values: MutableList<V2TIMUserStatus>?) {
                    events.changed(values.orEmpty().map { it.userID to presence(it) })
                }
                override fun onConnecting() = events.disconnected(false)
                override fun onConnectFailed(code: Int, desc: String?) = events.disconnected(false)
                override fun onConnectSuccess() = events.connected()
                override fun onKickedOffline() = events.disconnected(true)
                override fun onUserSigExpired() = events.disconnected(true)
            }
            V2TIMManager.getInstance().addIMSDKListener(listener)
            return { V2TIMManager.getInstance().removeIMSDKListener(listener) }
        }
        override fun subscribe(ids: List<String>, done: (Boolean) -> Unit) {
            V2TIMManager.getInstance().subscribeUserStatus(ids, callback(done))
        }
        override fun unsubscribe(ids: List<String>, done: (Boolean) -> Unit) {
            V2TIMManager.getInstance().unsubscribeUserStatus(ids, callback(done))
        }
        override fun query(ids: List<String>, done: (List<Pair<String, UserPresence>>?) -> Unit) {
            V2TIMManager.getInstance().getUserStatus(ids, object : V2TIMValueCallback<List<V2TIMUserStatus>> {
                override fun onSuccess(values: List<V2TIMUserStatus>?) = done(values.orEmpty().map { it.userID to presence(it) })
                override fun onError(code: Int, desc: String?) = done(null)
            })
        }
    })
    private fun callback(done: (Boolean) -> Unit) = object : V2TIMCallback {
        override fun onSuccess() = done(true)
        override fun onError(code: Int, desc: String?) = done(false)
    }
    private fun presence(status: V2TIMUserStatus) = when (status.statusType) {
        V2TIMUserStatus.V2TIM_USER_STATUS_ONLINE -> UserPresence.ONLINE
        V2TIMUserStatus.V2TIM_USER_STATUS_OFFLINE, V2TIMUserStatus.V2TIM_USER_STATUS_UNLOGINED -> UserPresence.OFFLINE
        else -> UserPresence.UNKNOWN
    }
    fun configureAccountScope(provider: () -> String) { reset(); accountScope = provider }
    fun observe(owner: Any, userID: String, callback: (UserPresence) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        controller.observe(owner, userID, callback)
    }
    fun release(owner: Any) { controller.release(owner) }
    fun refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { refresh() }; return }
        controller.refresh()
    }
    fun reset() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { reset() }; return }
        controller.reset()
    }
}
