package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import com.google.gson.JsonParser
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class TypingIndicatorControllerTest {
    // This schedules the real controller's Main coroutines; no controller state is copied.
    private class QueuedMain : MainCoroutineDispatcher() {
        private val pending = ConcurrentLinkedQueue<Runnable>()
        private val inMain = ThreadLocal.withInitial { false }
        override val immediate = object : MainCoroutineDispatcher() {
            override val immediate: MainCoroutineDispatcher get() = this
            override fun isDispatchNeeded(context: CoroutineContext) = !inMain.get()
            override fun dispatch(context: CoroutineContext, block: Runnable) { pending.add(block) }
        }
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending.add(block) }
        fun drain() {
            check(!inMain.get())
            inMain.set(true)
            try { while (true) (pending.poll() ?: break).run() }
            finally { inMain.set(false) }
        }
    }

    private class FakeRuntime : TypingIndicatorController.Runtime {
        var identity = "123:owner"
        var loggedIn = true
        var binds = 0
        var unbinds = 0
        val sends = mutableListOf<Pair<String, String>>()
        lateinit var raw: (String, String?, String?, Boolean) -> Unit
        lateinit var event: (MessageInfo) -> Unit
        lateinit var snapshot: (List<MessageInfo>) -> Unit
        override fun currentIdentity() = identity
        override fun isLoggedIn() = loggedIn
        override fun bind(conversationID: String, scope: CoroutineScope,
                          onRaw: (String, String?, String?, Boolean) -> Unit,
                          onStoreMessage: (MessageInfo) -> Unit,
                          onStoreSnapshot: (List<MessageInfo>) -> Unit): () -> Unit {
            binds++
            raw = onRaw
            event = onStoreMessage
            snapshot = onStoreSnapshot
            return { unbinds++ }
        }
        override fun send(peerUserID: String, data: String) { sends.add(peerUserID to data) }
        fun peerTyping(active: Boolean, sender: String? = "peer", group: String? = null, self: Boolean = false) =
            raw(TypingMessageProtocol.buildTypingMessageData(active), sender, group, self)
        fun statuses() = sends.map { TypingMessageProtocol.parseTypingStatus(it.second) }
    }

    private val main = QueuedMain()
    private val runtime = FakeRuntime()
    private var now = 100_000L
    private fun obtain(conversation: String = "c2c_peer", sdk: FakeRuntime = runtime) =
        TypingIndicatorController.obtain(conversation, sdk, main) { now }
    private fun ordinary(sender: String = "peer", seconds: Long = now / 1000) =
        MessageInfo(from = MessageSenderInfo(userID = sender), timestamp = seconds)
    private fun control(active: Boolean, seconds: Long = now / 1000) =
        MessageInfo(from = MessageSenderInfo(userID = "peer"), timestamp = seconds,
            messageType = MessageType.CUSTOM,
            messagePayload = CustomMessagePayload(customData = TypingMessageProtocol.buildTypingMessageData(active)))
    private fun await(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        do {
            main.drain()
            if (predicate()) return
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        fail("Controller condition did not settle within bounded wait")
    }

    @After fun cleanup() {
        TypingIndicatorController.resetAll()
        main.drain()
    }

    @Test fun `ordinary peer activity and exact four second start interval execute in controller`() {
        val controller = obtain()
        controller.sendTypingStatus(true)
        assertTrue(runtime.sends.isEmpty())
        runtime.event(ordinary())
        controller.sendTypingStatus(true)
        now += 3999
        controller.sendTypingStatus(true)
        assertEquals(listOf(true), runtime.statuses())
        now += 1
        controller.sendTypingStatus(true)
        assertEquals(listOf(true, true), runtime.statuses())
        assertTrue(runtime.sends.all { it.first == "peer" })
        runtime.sends.forEach { (_, payload) ->
            val json = JsonParser.parseString(payload).asJsonObject
            assertEquals(setOf("businessID", "typingStatus", "version", "userAction", "actionParam"), json.keySet())
            assertEquals(14, json["userAction"].asInt)
            assertEquals("EIMAMSG_InputStatus_Ing", json["actionParam"].asString)
        }
    }

    @Test fun `thirty second activity boundary is strict and snapshot epoch is respected`() {
        val controller = obtain()
        runtime.snapshot(listOf(ordinary(seconds = 100)))
        now = 129_999
        controller.sendTypingStatus(true)
        assertEquals(listOf(true), runtime.statuses())
        now = 130_000
        controller.sendTypingStatus(false)
        assertEquals("Existing false-after30 baseline is unchanged", listOf(true), runtime.statuses())
        now = 134_000
        controller.sendTypingStatus(true)
        assertEquals(listOf(true), runtime.statuses())
        runtime.event(ordinary())
        controller.sendTypingStatus(true)
        assertEquals(listOf(true, true), runtime.statuses())
    }

    @Test fun `explicit clear emits one end and keeps four second cross clear throttle`() {
        val controller = obtain()
        runtime.event(ordinary())
        controller.sendTypingStatus(false)
        assertTrue(runtime.sends.isEmpty())
        controller.sendTypingStatus(true)
        now += 10
        controller.sendTypingStatus(false)
        controller.sendTypingStatus(false)
        controller.sendTypingStatus(true)
        assertEquals(listOf(true, false), runtime.statuses())
        val end = JsonParser.parseString(runtime.sends.last().second).asJsonObject
        assertEquals(0, end["userAction"].asInt)
        assertEquals("EIMAMSG_InputStatus_End", end["actionParam"].asString)
        now = 104_000
        controller.sendTypingStatus(true)
        assertEquals(listOf(true, false, true), runtime.statuses())
    }

    @Test fun `valid raw start and end change actual state and both arm peer activity`() {
        val controller = obtain()
        runtime.peerTyping(true)
        assertFalse(controller.typingState.value)
        main.drain()
        assertTrue(controller.typingState.value)
        runtime.peerTyping(false)
        main.drain()
        assertFalse(controller.typingState.value)
        controller.sendTypingStatus(true)
        assertEquals(listOf(true), runtime.statuses())
    }

    @Test fun `raw other peer group self malformed and missing login cannot arm state or sends`() {
        val controller = obtain()
        runtime.peerTyping(true, sender = "other")
        runtime.peerTyping(true, group = "group")
        runtime.peerTyping(true, self = true)
        runtime.raw("{\"businessID\":\"user_typing_status\",\"typingStatus\":2}", "peer", null, false)
        runtime.loggedIn = false
        runtime.peerTyping(true)
        main.drain()
        runtime.loggedIn = true
        controller.sendTypingStatus(true)
        assertFalse(controller.typingState.value)
        assertTrue(runtime.sends.isEmpty())
        val groupRuntime = FakeRuntime()
        val groupController = obtain("group_example", groupRuntime)
        groupController.sendTypingStatus(true)
        assertEquals(0, groupRuntime.binds)
        assertTrue(groupRuntime.sends.isEmpty())
    }

    @Test fun `late Store controls cannot relight or extend thirty second activity`() {
        val controller = obtain()
        runtime.event(ordinary())
        now = 129_999
        runtime.event(control(true, 999))
        runtime.event(control(false, 999))
        runtime.snapshot(listOf(control(true, 999), control(false, 1000)))
        assertFalse(controller.typingState.value)
        now = 130_000
        controller.sendTypingStatus(true)
        assertTrue(runtime.sends.isEmpty())
    }

    @Test fun `lost stop clears actual state through original five second cooperative timer`() {
        val controller = obtain()
        val started = System.nanoTime()
        runtime.peerTyping(true)
        main.drain()
        assertTrue(controller.typingState.value)
        await(8_000) { !controller.typingState.value }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        assertTrue("Timer must not clear before its original interval", elapsed >= 4_950)
    }

    @Test fun `owner AppID and login change after raw acceptance reject queued Main work`() {
        val controller = obtain()
        runtime.peerTyping(true)
        runtime.identity = "123:next-owner"
        main.drain()
        assertFalse(controller.typingState.value)
        runtime.identity = "123:owner"
        runtime.peerTyping(true)
        runtime.identity = "456:owner"
        main.drain()
        assertFalse(controller.typingState.value)
        runtime.identity = "123:owner"
        runtime.peerTyping(true)
        runtime.loggedIn = false
        main.drain()
        assertFalse(controller.typingState.value)
        controller.sendTypingStatus(true)
        assertTrue(runtime.sends.isEmpty())
    }

    @Test fun `shared registry keeps live reference and last release closes exact binding`() {
        val first = obtain()
        val second = obtain()
        assertSame(first, second)
        assertEquals(1, runtime.binds)
        TypingIndicatorController.release(first)
        assertEquals(0, runtime.unbinds)
        runtime.peerTyping(true)
        main.drain()
        assertTrue(second.typingState.value)
        TypingIndicatorController.release(second)
        assertEquals(1, runtime.unbinds)
        assertFalse(second.typingState.value)
        runtime.peerTyping(true)
        main.drain()
        assertFalse(second.typingState.value)
    }

    @Test fun `last release rejects already queued raw and old scope work`() {
        val controller = obtain()
        runtime.peerTyping(true)
        TypingIndicatorController.release(controller)
        main.drain()
        assertFalse(controller.typingState.value)
        assertEquals(1, runtime.unbinds)
        controller.sendTypingStatus(true)
        assertTrue(runtime.sends.isEmpty())
    }

    @Test fun `reset closes old binding and old release cannot remove new registry instance`() {
        val old = obtain()
        runtime.peerTyping(true)
        TypingIndicatorController.resetAll()
        main.drain()
        assertFalse(old.typingState.value)
        assertEquals(1, runtime.unbinds)
        val replacementRuntime = FakeRuntime()
        val replacement = obtain(sdk = replacementRuntime)
        assertNotSame(old, replacement)
        TypingIndicatorController.release(old)
        assertSame(replacement, obtain(sdk = replacementRuntime))
        replacementRuntime.peerTyping(true)
        main.drain()
        assertTrue(replacement.typingState.value)
    }

    @Test fun `stop clears current display but new raw update remains allowed by existing contract`() {
        val controller = obtain()
        runtime.peerTyping(true)
        main.drain()
        controller.stop()
        assertFalse(controller.typingState.value)
        runtime.peerTyping(true)
        main.drain()
        assertTrue("stop has never been a permanent close API", controller.typingState.value)
    }

    @Test fun `raw accepted before stop must not republish that obsolete display work`() {
        val controller = obtain()
        runtime.peerTyping(true)
        controller.stop()
        assertFalse(controller.typingState.value)
        main.drain()
        assertFalse("stop clears existing work; accepted pre-stop callback is obsolete", controller.typingState.value)
    }
}
