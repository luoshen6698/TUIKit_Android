package io.trtc.tuikit.chat.demo.xingdun.call

/** Only calls observed as a one-to-one callee may become a retained call result. */
internal class XingDunCallHistoryNotificationPolicy {
    enum class EndReason { HANGUP, REJECT, NO_RESPONSE, CANCELED, LINE_BUSY, OTHER }
    enum class Result { ENDED, REJECTED, MISSED, CANCELED }

    data class Call(
        val accountKey: String,
        val callId: String,
        val callerId: String,
        val callerName: String,
        val video: Boolean,
        val accepted: Boolean,
    )

    data class Record(val call: Call, val result: Result)

    private val calls = linkedMapOf<String, Call>()
    private val completed = linkedSetOf<String>()
    private var accountKey: String? = null

    fun observe(call: Call, selfId: String, oneToOne: Boolean) {
        if (call.accountKey.isBlank() || call.callId.isBlank() || selfId.isBlank() ||
            call.callerId.isBlank() || call.callerId == selfId || !oneToOne) return
        if (accountKey != call.accountKey) {
            clear()
            accountKey = call.accountKey
        }
        if (call.callId in completed) return
        val previous = calls[call.callId]
        calls[call.callId] = call.copy(
            callerName = call.callerName.ifBlank { previous?.callerName.orEmpty() },
            accepted = call.accepted || previous?.accepted == true,
        )
        while (calls.size > MAX_PENDING) calls.remove(calls.keys.first())
    }

    fun finish(currentAccountKey: String?, callId: String, reason: EndReason): Record? {
        if (currentAccountKey == null || currentAccountKey != accountKey || callId in completed) return null
        val call = calls.remove(callId) ?: return null
        completed += callId
        while (completed.size > MAX_COMPLETED) completed.remove(completed.first())
        val result = when {
            call.accepted -> Result.ENDED
            reason == EndReason.REJECT -> Result.REJECTED
            reason == EndReason.CANCELED -> Result.CANCELED
            reason == EndReason.NO_RESPONSE || reason == EndReason.LINE_BUSY -> Result.MISSED
            else -> Result.ENDED
        }
        return Record(call, result)
    }

    fun clear() {
        accountKey = null
        calls.clear()
        completed.clear()
    }

    private companion object {
        const val MAX_PENDING = 8
        const val MAX_COMPLETED = 64
    }
}
