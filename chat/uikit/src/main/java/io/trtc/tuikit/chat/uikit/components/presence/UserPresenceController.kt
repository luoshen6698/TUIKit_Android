package io.trtc.tuikit.chat.uikit.components.presence

/** Owns the actual cache, callback ordering and SDK subscription lifetime. */
internal class UserPresenceController(private val runtime: Runtime) {
    interface Events {
        fun changed(values: List<Pair<String, UserPresence>>)
        fun disconnected(terminal: Boolean)
        fun connected()
    }
    interface Runtime {
        fun identity(): String
        fun sdkIdentity(): String
        fun selfUserID(): String
        fun dispatch(action: () -> Unit)
        fun later(delayMillis: Long, action: () -> Unit): () -> Unit
        fun listen(events: Events): () -> Unit
        fun subscribe(ids: List<String>, done: (Boolean) -> Unit)
        fun unsubscribe(ids: List<String>, done: (Boolean) -> Unit)
        fun query(ids: List<String>, done: (List<Pair<String, UserPresence>>?) -> Unit)
    }

    private val observers = linkedMapOf<Any, Pair<String, (UserPresence) -> Unit>>()
    private val statuses = mutableMapOf<String, UserPresence>()
    private val revisions = mutableMapOf<String, Long>()
    // Only acknowledged SDK leases belong here. Refresh never forgets them.
    private val subscribed = linkedSetOf<String>()
    private val retiring = linkedSetOf<String>()
    private var identity = ""
    private var sdkIdentity = ""
    private var queryEpoch = 0L
    private var revisionSequence = 0L
    private var sdkEpoch = 0L
    private var listenerEpoch = 0L
    private var unlisten: (() -> Unit)? = null
    private var pending: (() -> Unit)? = null
    private var timer: (() -> Unit)? = null
    private var busy = false
    private var suspended = false
    private var terminal = false
    private var needsQuery = false

    fun observe(owner: Any, userID: String, callback: (UserPresence) -> Unit) {
        if (identity != runtime.identity()) { reset(); identity = runtime.identity() }
        syncSdk()
        observers[owner] = userID to callback
        callback(statuses[userID] ?: UserPresence.UNKNOWN)
        bind()
        needsQuery = true
        schedule()
    }

    fun release(owner: Any) { observers.remove(owner); schedule() }

    fun refresh() {
        if (identity != runtime.identity()) { reset(); return }
        clearValues()
        needsQuery = true
        schedule()
    }

    fun reset() {
        clearValues()
        observers.clear()
        listenerEpoch++
        unlisten?.invoke(); unlisten = null
        identity = ""
        suspended = false; terminal = false; needsQuery = false
        syncSdk()
        // Retire old credential leases before a new scope can add any, even for the same SDK account.
        retiring.addAll(subscribed)
        schedule()
    }

    private fun clearValues() {
        queryEpoch++
        pending?.invoke(); pending = null
        timer?.invoke(); timer = null
        statuses.clear(); revisions.clear()
        publish()
    }

    private fun syncSdk() {
        if (sdkIdentity == runtime.sdkIdentity()) return
        sdkIdentity = runtime.sdkIdentity()
        sdkEpoch++
        busy = false
        subscribed.clear(); retiring.clear()
    }

    private fun bind() {
        if (unlisten != null) return
        val token = ++listenerEpoch
        fun accept(action: () -> Unit) = runtime.dispatch {
            if (token == listenerEpoch && identity == runtime.identity() && sdkIdentity == runtime.sdkIdentity()) action()
        }
        unlisten = runtime.listen(object : Events {
            override fun changed(values: List<Pair<String, UserPresence>>) = accept {
                if (!suspended && !terminal) {
                    val wanted = wanted()
                    values.filter { it.first in wanted && it.first in subscribed }.forEach { (user, value) ->
                        revisions[user] = ++revisionSequence
                        statuses[user] = value
                    }
                    publish()
                }
            }
            override fun disconnected(terminal: Boolean) = accept {
                clearValues()
                suspended = true
                this@UserPresenceController.terminal = this@UserPresenceController.terminal || terminal
            }
            override fun connected() = accept {
                if (!terminal) { suspended = false; needsQuery = true; schedule() }
            }
        })
    }

    private fun wanted(): Set<String> = if (terminal || suspended || identity != runtime.identity() || runtime.selfUserID().isBlank()) emptySet() else
        observers.values.map { it.first }.filter { it.isNotBlank() && it != runtime.selfUserID() }.distinct().take(100).toSet()

    private fun schedule() {
        pending?.invoke()
        // Coalesce consumer churn; each SDK operation is separated by at least 300ms.
        pending = runtime.later(300) { pending = null; reconcile() }
    }

    private fun retryLater() {
        timer?.invoke()
        timer = runtime.later(30_000) {
            timer = null
            if (observers.isEmpty()) schedule() else refresh()
        }
    }

    private fun reconcile() {
        syncSdk()
        if (runtime.selfUserID().isBlank() || suspended || terminal) return
        val wanted = wanted()
        statuses.keys.retainAll(wanted); revisions.keys.retainAll(wanted)
        if (wanted.isNotEmpty() || subscribed.isNotEmpty()) retryLater()
        if (busy) return
        val epoch = sdkEpoch
        val sdk = sdkIdentity
        fun currentSdk() = epoch == sdkEpoch && sdk == runtime.sdkIdentity()
        val removing = (retiring + (subscribed - wanted)).toList()
        if (removing.isNotEmpty()) {
            busy = true
            runtime.unsubscribe(removing) { success -> runtime.dispatch {
                if (!currentSdk()) return@dispatch
                busy = false
                if (success) {
                    subscribed.removeAll(removing.toSet()); retiring.removeAll(removing.toSet())
                    schedule()
                }
                // Failure retains the real leases and blocks additions until removal succeeds.
            } }
            return
        }
        val adding = (wanted - subscribed).toList()
        if (adding.isNotEmpty()) {
            busy = true
            val scope = identity
            runtime.subscribe(adding) { success -> runtime.dispatch {
                if (!currentSdk()) return@dispatch
                busy = false
                if (success) {
                    subscribed.addAll(adding)
                    if (scope != identity || identity.isEmpty()) retiring.addAll(adding)
                    needsQuery = true; schedule()
                } else {
                    adding.forEach { statuses.remove(it) }; publish()
                }
            } }
            return
        }
        if (wanted.isEmpty()) {
            timer?.invoke(); timer = null
            listenerEpoch++; unlisten?.invoke(); unlisten = null
            return
        }
        if (!needsQuery) return
        needsQuery = false
        val scope = identity
        val generation = queryEpoch
        // Each request and each newer event advances the same per-peer ordering fence.
        val requested = wanted.associateWith { user -> (++revisionSequence).also { revisions[user] = it } }
        runtime.query(wanted.toList()) { values -> runtime.dispatch {
            if (!currentSdk() || scope != identity || identity != runtime.identity() || generation != queryEpoch || terminal || suspended) return@dispatch
            val returned = values.orEmpty().toMap()
            requested.forEach { (user, revision) ->
                if (user in wanted() && user in subscribed && revisions[user] == revision) {
                    statuses[user] = returned[user] ?: UserPresence.UNKNOWN
                }
            }
            publish()
        } }
    }

    private fun publish() = observers.values.toList().forEach { (user, callback) -> callback(statuses[user] ?: UserPresence.UNKNOWN) }
}
