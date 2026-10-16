package io.trtc.tuikit.chat.demo.xingdun.launch

import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** A push can prioritize startup, but only authenticated SDK state can open the call UI. */
internal object XingDunIncomingCallLaunchPolicy {
    suspend fun awaitNormalStartup(incomingCall: Flow<Boolean>, activityResumed: Flow<Boolean>) {
        combine(incomingCall, activityResumed) { active, resumed -> !active && resumed }.first { it }
    }

    fun hasCallHint(extension: String?): Boolean {
        if (extension.isNullOrBlank() || extension.length > 16_384) return false
        return runCatching {
            val entity = JsonParser.parseString(extension).asJsonObject.getAsJsonObject("entity")
            entity?.get("action")?.asInt == 2
        }.getOrDefault(false)
    }

    fun canHandOff(
        identityMatches: Boolean,
        callId: String?,
        selfIsCallee: Boolean,
        isWaitingOrAccepted: Boolean,
        hasMediaType: Boolean,
    ): Boolean = identityMatches && !callId.isNullOrBlank() && selfIsCallee &&
        isWaitingOrAccepted && hasMediaType
}
