package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility

/** The shared UI routes each conversation to its own protocol and durable namespace. */
internal object XingDunMessageDeletionVisibility {
    fun initialize(context: Context) {
        XingDunConversationPreviewResolver.initialize(context)
        XingDunC2CDeletionRepository.initialize(context)
        XingDunGroupDeletionRepository.initialize(context)
        SearchMessageVisibility.setDeletedProvider(::isDeleted)
        SearchMessageVisibility.setConfirmedDeletedProvider { conversation, id ->
            if (conversation.startsWith("group_")) XingDunGroupDeletionRepository.isDeleted(conversation, id)
            else XingDunC2CDeletionRepository.isDeleted(conversation, id)
        }
        SearchMessageVisibility.setQuoteHiddenProvider(XingDunGroupDeletionRepository::quoteHidden)
        SearchMessageVisibility.setVerificationFailureProvider(XingDunGroupDeletionRepository::verificationFailed)
        SearchMessageVisibility.setSessionProvider {
            val account = XingDunC2CDeletionRepository.accountKey()
            val generation = io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager.currentAuthenticationGeneration()
            if (account == null || generation == null) null else "$account:$generation"
        }
        SearchMessageVisibility.setProvider { conversation, messages -> filterMessages(context, conversation, messages) }
    }
    fun isDeleted(conversation: String, id: String) = if (conversation.startsWith("group_"))
        XingDunGroupDeletionRepository.isHidden(conversation, id) else XingDunC2CDeletionRepository.isDeleted(conversation, id)
    suspend fun filterMessages(context: Context, conversation: String, messages: List<MessageInfo>) = if (conversation.startsWith("group_"))
        XingDunGroupDeletionRepository.filterMessages(context, conversation, messages)
        else XingDunC2CDeletionRepository.filterMessages(context, conversation, messages)
    fun addListener(listener: (String) -> Unit) {
        XingDunC2CDeletionRepository.addListener(listener); XingDunGroupDeletionRepository.addListener(listener)
    }
    fun removeListener(listener: (String) -> Unit) {
        XingDunC2CDeletionRepository.removeListener(listener); XingDunGroupDeletionRepository.removeListener(listener)
    }
}
