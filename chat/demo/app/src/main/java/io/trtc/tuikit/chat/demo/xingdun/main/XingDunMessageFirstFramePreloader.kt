package io.trtc.tuikit.chat.demo.xingdun.main

import android.content.Context
import com.bumptech.glide.Glide
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal enum class XingDunJoinedGroupLoadStatus {
    NOT_STARTED,
    LOADING,
    SUCCEEDED,
    FAILED,
}

internal data class XingDunJoinedGroupLoadScope(
    val sdkAppId: Int,
    val timUserId: String,
    val companyCode: String,
)

internal data class XingDunJoinedGroupLoadState(
    val scope: XingDunJoinedGroupLoadScope? = null,
    val status: XingDunJoinedGroupLoadStatus = XingDunJoinedGroupLoadStatus.NOT_STARTED,
)

internal class XingDunJoinedGroupLoadTracker {
    private val mutableState = MutableStateFlow(XingDunJoinedGroupLoadState())
    val state: StateFlow<XingDunJoinedGroupLoadState> = mutableState.asStateFlow()

    fun begin(scope: XingDunJoinedGroupLoadScope, preserveLoaded: Boolean = false) {
        val current = mutableState.value
        if (preserveLoaded && current.scope == scope && current.status == XingDunJoinedGroupLoadStatus.SUCCEEDED) {
            return
        }
        mutableState.value = XingDunJoinedGroupLoadState(scope, XingDunJoinedGroupLoadStatus.LOADING)
    }

    fun finish(scope: XingDunJoinedGroupLoadScope, success: Boolean) {
        val current = mutableState.value
        if (current.scope != scope) return
        if (!success && current.status == XingDunJoinedGroupLoadStatus.SUCCEEDED) return
        mutableState.value = XingDunJoinedGroupLoadState(
            scope,
            if (success) XingDunJoinedGroupLoadStatus.SUCCEEDED else XingDunJoinedGroupLoadStatus.FAILED,
        )
    }

    fun status(scope: XingDunJoinedGroupLoadScope): XingDunJoinedGroupLoadStatus =
        mutableState.value.takeIf { it.scope == scope }?.status ?: XingDunJoinedGroupLoadStatus.NOT_STARTED
}

internal enum class XingDunStartupPreloadOutcome {
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
}

internal class XingDunStartupPreloadGate(
    private val timeoutMillis: Long,
) {
    suspend fun awaitJoinedGroups(
        startLoad: (CompletionHandler) -> Unit,
        onFinished: (Boolean) -> Unit,
    ): XingDunStartupPreloadOutcome = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            fun finish(success: Boolean) {
                if (!finished.compareAndSet(false, true)) return
                onFinished(success)
                if (continuation.isActive) {
                    continuation.resume(
                        if (success) XingDunStartupPreloadOutcome.SUCCEEDED
                        else XingDunStartupPreloadOutcome.FAILED,
                    )
                }
            }
            runCatching {
                startLoad(object : CompletionHandler {
                    override fun onSuccess() = finish(true)
                    override fun onFailure(code: Int, desc: String) = finish(false)
                })
            }.onFailure { finish(false) }
        }
    } ?: XingDunStartupPreloadOutcome.TIMED_OUT
}

/** Starts first-frame data work after IM login and waits only for the joined-group boundary. */
object XingDunMessageFirstFramePreloader {
    private val joinedGroupTracker = XingDunJoinedGroupLoadTracker()
    internal val joinedGroupLoadState: StateFlow<XingDunJoinedGroupLoadState> = joinedGroupTracker.state

    internal suspend fun preload(
        context: Context,
        session: XingDunStoredSession,
    ): XingDunStartupPreloadOutcome {
        val applicationContext = context.applicationContext
        val conversationStore = ConversationListStore.create()
        conversationStore.loadConversations(
            ConversationLoadOption(),
            object : CompletionHandler {
                override fun onSuccess() {
                    conversationStore.state.conversationList.value
                        .asSequence()
                        .mapNotNull { it.avatarURL }
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(AVATAR_PRELOAD_LIMIT)
                        .forEach { avatarURL ->
                            Glide.with(applicationContext).load(avatarURL).preload()
                        }
                }

                override fun onFailure(code: Int, desc: String) = Unit
            },
        )
        ContactStore.shared.loadFriends(IGNORE_RESULT)
        GroupStore.shared.loadApplications(IGNORE_RESULT)
        ContactStore.shared.loadFriendApplications(IGNORE_RESULT)
        val scope = session.groupLoadScope()
        joinedGroupTracker.begin(scope)
        return XingDunStartupPreloadGate(GROUP_PRELOAD_TIMEOUT_MILLIS).awaitJoinedGroups(
            startLoad = GroupStore.shared::loadJoinedGroups,
            onFinished = { success -> joinedGroupTracker.finish(scope, success) },
        )
    }

    internal fun joinedGroupLoadStatus(session: XingDunStoredSession?): XingDunJoinedGroupLoadStatus =
        session?.groupLoadScope()?.let(joinedGroupTracker::status)
            ?: XingDunJoinedGroupLoadStatus.NOT_STARTED

    internal fun beginJoinedGroupRefresh(session: XingDunStoredSession, preserveLoaded: Boolean) {
        joinedGroupTracker.begin(session.groupLoadScope(), preserveLoaded)
    }

    internal fun finishJoinedGroupRefresh(session: XingDunStoredSession, success: Boolean) {
        joinedGroupTracker.finish(session.groupLoadScope(), success)
    }

    private val IGNORE_RESULT = object : CompletionHandler {
        override fun onSuccess() = Unit
        override fun onFailure(code: Int, desc: String) = Unit
    }

    private const val AVATAR_PRELOAD_LIMIT = 12
    internal const val GROUP_PRELOAD_TIMEOUT_MILLIS = 1_500L
}

private fun XingDunStoredSession.groupLoadScope() = XingDunJoinedGroupLoadScope(
    sdkAppId = sdkAppId,
    timUserId = timUserId,
    companyCode = companyCode.trim().lowercase(),
)
