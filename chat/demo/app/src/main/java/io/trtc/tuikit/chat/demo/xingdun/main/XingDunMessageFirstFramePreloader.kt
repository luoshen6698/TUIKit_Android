package io.trtc.tuikit.chat.demo.xingdun.main

import android.content.Context
import com.bumptech.glide.Glide
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationLoadOption
import io.trtc.tuikit.atomicxcore.api.group.GroupStore

/** Starts message-tab data and image work after IM login without delaying navigation. */
object XingDunMessageFirstFramePreloader {

    fun preload(context: Context) {
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
        GroupStore.shared.loadApplications(IGNORE_RESULT)
        ContactStore.shared.loadFriendApplications(IGNORE_RESULT)
    }

    private val IGNORE_RESULT = object : CompletionHandler {
        override fun onSuccess() = Unit
        override fun onFailure(code: Int, desc: String) = Unit
    }

    private const val AVATAR_PRELOAD_LIMIT = 12
}
