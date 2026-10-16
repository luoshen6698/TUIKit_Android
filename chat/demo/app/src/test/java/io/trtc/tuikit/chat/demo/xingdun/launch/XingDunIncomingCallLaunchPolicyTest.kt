package io.trtc.tuikit.chat.demo.xingdun.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class XingDunIncomingCallLaunchPolicyTest {
    @Test fun officialCallExtensionPrioritizesStartup() {
        assertTrue(XingDunIncomingCallLaunchPolicy.hasCallHint("""{"entity":{"action":2}}"""))
    }

    @Test fun normalMessagesAndMalformedExtensionsKeepNormalStartup() {
        listOf(null, "", "not json", "[]", "null", "{}", """{"entity":{"action":1}}""",
            """{"notifyMode":"2"}""", """{"entity":{"action":{}}}""", "x".repeat(16_385))
            .forEach { assertFalse(XingDunIncomingCallLaunchPolicy.hasCallHint(it)) }
    }

    @Test fun aCallHintAloneCannotAuthorizeCallUi() {
        assertFalse(handOff(identityMatches = false))
        assertFalse(handOff(callId = ""))
        assertFalse(handOff(selfIsCallee = false))
        assertFalse(handOff(isWaitingOrAccepted = false))
        assertFalse(handOff(hasMediaType = false))
    }

    @Test fun onlyTheAuthenticatedCurrentIncomingCallCanTakeOver() {
        assertTrue(handOff())
    }

    @Test fun normalNavigationWaitsForCallToEndAndStartupToReturnToForeground() = runBlocking {
        val call = MutableStateFlow(true)
        val resumed = MutableStateFlow(false)
        val navigation = async(start = CoroutineStart.UNDISPATCHED) {
            XingDunIncomingCallLaunchPolicy.awaitNormalStartup(call, resumed)
        }
        yield()
        assertFalse(navigation.isCompleted)
        call.value = false
        yield()
        assertFalse(navigation.isCompleted)
        resumed.value = true
        withTimeout(1_000) { navigation.await() }
    }

    @Test fun foregroundStartupCannotCoverAnActiveIncomingCall() = runBlocking {
        val call = MutableStateFlow(true)
        val resumed = MutableStateFlow(true)
        val navigation = async(start = CoroutineStart.UNDISPATCHED) {
            XingDunIncomingCallLaunchPolicy.awaitNormalStartup(call, resumed)
        }
        yield()
        assertFalse(navigation.isCompleted)
        call.value = false
        withTimeout(1_000) { navigation.await() }
    }

    private fun handOff(
        identityMatches: Boolean = true,
        callId: String? = "sdk-call",
        selfIsCallee: Boolean = true,
        isWaitingOrAccepted: Boolean = true,
        hasMediaType: Boolean = true,
    ) = XingDunIncomingCallLaunchPolicy.canHandOff(
        identityMatches, callId, selfIsCallee, isWaitingOrAccepted, hasMediaType,
    )
}
