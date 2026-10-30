package io.trtc.tuikit.chat.uikit.components.presence

import org.junit.Assert.*
import org.junit.Test

class UserPresenceControllerTest {
    private class FakeRuntime : UserPresenceController.Runtime {
        var scope = "tenant-a:credential-1"
        var sdk = "1:self"
        var self = "self"
        var now = 0L
        private var order = 0L
        private data class Task(val at: Long, val order: Long, val action: () -> Unit)
        private val tasks = mutableListOf<Task>()
        val leases = linkedSetOf<String>()
        val subscriptions = mutableListOf<List<String>>()
        val removals = mutableListOf<List<String>>()
        val queries = mutableListOf<Pair<List<String>, (List<Pair<String, UserPresence>>?) -> Unit>>()
        val listeners = mutableListOf<UserPresenceController.Events>()
        val calls = mutableListOf<Pair<String, Long>>()
        var unsubscribedListeners = 0
        var subscribeSucceeds = true
        var unsubscribeSucceeds = true
        var holdSubscribe = false
        var holdUnsubscribe = false
        val pendingSubscribe = mutableListOf<() -> Unit>()
        val pendingUnsubscribe = mutableListOf<() -> Unit>()
        override fun identity() = "$scope:$sdk"
        override fun sdkIdentity() = sdk
        override fun selfUserID() = self
        override fun dispatch(action: () -> Unit) { later(0, action) }
        override fun later(delayMillis: Long, action: () -> Unit): () -> Unit {
            val task = Task(now + delayMillis, order++, action); tasks.add(task)
            return { tasks.remove(task) }
        }
        override fun listen(events: UserPresenceController.Events): () -> Unit {
            listeners.add(events)
            return { unsubscribedListeners++ }
        }
        override fun subscribe(ids: List<String>, done: (Boolean) -> Unit) {
            subscriptions.add(ids); calls.add("subscribe" to now)
            val finish = { if (subscribeSucceeds) leases.addAll(ids); done(subscribeSucceeds) }
            if (holdSubscribe) pendingSubscribe.add(finish) else finish()
        }
        override fun unsubscribe(ids: List<String>, done: (Boolean) -> Unit) {
            removals.add(ids); calls.add("unsubscribe" to now)
            val finish = { if (unsubscribeSucceeds) leases.removeAll(ids.toSet()); done(unsubscribeSucceeds) }
            if (holdUnsubscribe) pendingUnsubscribe.add(finish) else finish()
        }
        override fun query(ids: List<String>, done: (List<Pair<String, UserPresence>>?) -> Unit) {
            queries.add(ids to done); calls.add("query" to now)
        }
        fun advance(ms: Long = 0) {
            val end = now + ms
            while (true) {
                val task = tasks.filter { it.at <= end }.minWithOrNull(compareBy<Task> { it.at }.thenBy { it.order }) ?: break
                tasks.remove(task); now = task.at; task.action()
            }
            now = end
        }
        fun event(user: String = "peer", value: UserPresence = UserPresence.ONLINE) {
            listeners.last().changed(listOf(user to value)); advance()
        }
        fun reply(index: Int = queries.lastIndex, user: String = "peer", value: UserPresence = UserPresence.OFFLINE) {
            queries[index].second(listOf(user to value)); advance()
        }
    }
    private val runtime = FakeRuntime()
    private val controller = UserPresenceController(runtime)
    private val owner = Any()
    private val values = mutableListOf<UserPresence>()
    private fun observe(peer: String = "peer", who: Any = owner) = controller.observe(who, peer) { values.add(it) }
    private fun settled() = runtime.advance(900)

    @Test fun `newer event survives older query success`() {
        observe(); settled(); runtime.event(); runtime.reply()
        assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `newer event survives older query failure`() {
        observe(); settled(); runtime.event(); runtime.queries.single().second(null); runtime.advance()
        assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `refresh preserves leases until final consumer release`() {
        observe("a"); settled(); controller.refresh(); observe("b"); settled()
        assertEquals(setOf("b"), runtime.leases)
        controller.release(owner); settled()
        assertTrue(runtime.leases.isEmpty()); assertEquals(1, runtime.unsubscribedListeners)
    }
    @Test fun `failed subscribe stays unknown and visible consumer can retry`() {
        runtime.subscribeSucceeds = false; observe(); settled()
        assertEquals(UserPresence.UNKNOWN, values.last()); assertTrue(runtime.leases.isEmpty())
        runtime.subscribeSucceeds = true; observe(); settled(); runtime.reply(value = UserPresence.ONLINE)
        assertEquals(2, runtime.subscriptions.size); assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `failed release does not spend new subscription slots`() {
        observe("a"); settled(); runtime.unsubscribeSucceeds = false; observe("b"); settled()
        assertEquals(setOf("a"), runtime.leases); assertEquals(1, runtime.subscriptions.size)
        runtime.unsubscribeSucceeds = true; controller.refresh(); settled()
        assertEquals(setOf("b"), runtime.leases)
    }
    @Test fun `release waits for SDK acknowledgement before subscribing replacement`() {
        observe("a"); settled(); runtime.holdUnsubscribe = true; observe("b"); settled()
        assertEquals(1, runtime.subscriptions.size)
        runtime.pendingUnsubscribe.removeAt(0)(); runtime.advance(); settled()
        assertEquals(setOf("b"), runtime.leases)
    }
    @Test fun `late subscribe after consumer leaves is released without querying`() {
        runtime.holdSubscribe = true; observe(); runtime.advance(300); controller.release(owner)
        settled(); runtime.pendingSubscribe.removeAt(0)(); runtime.advance(); settled()
        assertTrue(runtime.leases.isEmpty()); assertTrue(runtime.queries.isEmpty())
    }
    @Test fun `refresh during delayed subscribe does not forget successful lease`() {
        runtime.holdSubscribe = true; observe(); runtime.advance(300); controller.refresh()
        runtime.pendingSubscribe.removeAt(0)(); runtime.advance(); settled()
        assertEquals(1, runtime.subscriptions.size); assertEquals(setOf("peer"), runtime.leases)
        controller.release(owner); settled(); assertTrue(runtime.leases.isEmpty())
    }
    @Test fun `credential change retires delayed same SDK subscription before new scope`() {
        runtime.holdSubscribe = true; observe(); runtime.advance(300)
        runtime.scope = "tenant-b:credential-2"; observe(); runtime.holdSubscribe = false
        runtime.pendingSubscribe.removeAt(0)(); runtime.advance(); settled()
        assertEquals(listOf(listOf("peer")), runtime.removals)
        assertEquals(2, runtime.subscriptions.size)
    }
    @Test fun `old listener cannot paint new credential even with same peer id`() {
        observe(); settled(); val old = runtime.listeners.single()
        runtime.scope = "tenant-b:credential-2"; observe(); settled()
        old.changed(listOf("peer" to UserPresence.ONLINE)); runtime.advance()
        assertEquals(UserPresence.UNKNOWN, values.last())
        runtime.event(); assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `queued old listener event is rejected after reset`() {
        observe(); settled(); runtime.listeners.last().changed(listOf("peer" to UserPresence.ONLINE))
        controller.reset(); runtime.scope = "tenant-b:credential-2"; observe(); settled()
        assertEquals(UserPresence.UNKNOWN, values.last())
    }
    @Test fun `old account callbacks never unsubscribe newly logged in SDK account`() {
        runtime.holdSubscribe = true; observe(); runtime.advance(300)
        runtime.sdk = "1:other"; runtime.self = "other"; observe("next"); runtime.holdSubscribe = false
        runtime.pendingSubscribe.removeAt(0)(); runtime.advance(); settled()
        assertTrue(runtime.removals.isEmpty()); assertEquals(listOf("next"), runtime.queries.single().first)
    }
    @Test fun `old query is ignored after refresh even without an event`() {
        observe(); settled(); controller.refresh(); settled()
        runtime.reply(1, value = UserPresence.ONLINE); runtime.reply(0)
        assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `newer query wins even if earlier request finishes last`() {
        observe(); settled(); observe(); settled()
        runtime.reply(1, value = UserPresence.ONLINE); runtime.reply(0)
        assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `release and reobserve same peer cannot reuse an old query revision`() {
        observe(); settled()
        controller.release(owner); settled()
        observe(); settled(); runtime.reply(1, value = UserPresence.ONLINE); runtime.reply(0)
        assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `missing requested user and failed query stay unknown`() {
        observe(); settled(); runtime.queries.single().second(emptyList()); runtime.advance()
        assertEquals(UserPresence.UNKNOWN, values.last())
        controller.refresh(); settled(); runtime.queries.last().second(null); runtime.advance()
        assertEquals(UserPresence.UNKNOWN, values.last())
    }
    @Test fun `foreign event and foreign query result cannot populate visible peer`() {
        observe(); settled(); runtime.event("foreign"); runtime.reply(user = "foreign", value = UserPresence.ONLINE)
        assertEquals(UserPresence.UNKNOWN, values.last())
    }
    @Test fun `disconnect is unknown and reconnect requeries without losing lease`() {
        observe(); settled(); runtime.event(); runtime.listeners.last().disconnected(false); runtime.advance()
        assertEquals(UserPresence.UNKNOWN, values.last()); runtime.event()
        assertEquals(UserPresence.UNKNOWN, values.last())
        runtime.listeners.last().connected(); runtime.advance(); settled(); runtime.reply(value = UserPresence.ONLINE)
        assertEquals(1, runtime.subscriptions.size); assertEquals(UserPresence.ONLINE, values.last())
    }
    @Test fun `expired or kicked session cannot be revived by connect success`() {
        observe(); settled(); runtime.listeners.last().disconnected(true); runtime.advance()
        runtime.listeners.last().connected(); runtime.advance(); controller.refresh(); settled(); runtime.event()
        assertEquals(UserPresence.UNKNOWN, values.last()); assertEquals(1, runtime.queries.size)
    }
    @Test fun `shared peer remains subscribed until its last owner leaves`() {
        val second = Any(); observe(); observe(who = second); settled()
        controller.release(owner); settled(); assertTrue(runtime.removals.isEmpty())
        controller.release(second); settled(); assertTrue(runtime.leases.isEmpty())
    }
    @Test fun `self empty duplicates and excess visible users do not exceed one hundred leases`() {
        observe(""); observe("self", Any())
        repeat(120) { observe("peer-$it", Any()); observe("peer-$it", Any()) }; settled()
        assertEquals(100, runtime.leases.size); assertTrue(runtime.subscriptions.all { it.size <= 100 })
        assertFalse("self" in runtime.leases)
    }
    @Test fun `empty login performs no SDK I O`() {
        runtime.self = ""; observe(); settled(); assertTrue(runtime.calls.isEmpty())
    }
    @Test fun `release failure retries with bounded timer even without consumers`() {
        observe(); settled(); runtime.unsubscribeSucceeds = false; controller.release(owner); settled()
        assertFalse(runtime.leases.isEmpty()); runtime.unsubscribeSucceeds = true; runtime.advance(30_900)
        assertTrue(runtime.leases.isEmpty()); assertEquals(1, runtime.unsubscribedListeners)
    }
    @Test fun `periodic refresh requeries without duplicate subscriptions`() {
        observe(); settled(); runtime.advance(30_000)
        assertEquals(1, runtime.subscriptions.size); assertEquals(2, runtime.queries.size)
    }
    @Test fun `visible churn and repeated refresh remain below default SDK frequency`() {
        repeat(100) { observe("peer-$it"); runtime.advance(60) }; settled()
        repeat(100) { controller.refresh(); runtime.advance(300) }
        for (kind in listOf("subscribe", "unsubscribe", "query")) {
            val times = runtime.calls.filter { it.first == kind }.map { it.second }
            assertTrue(times.all { start -> times.count { it >= start && it < start + 5000 } <= 20 })
        }
    }
}
