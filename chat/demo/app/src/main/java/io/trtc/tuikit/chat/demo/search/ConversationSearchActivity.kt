package io.trtc.tuikit.chat.demo.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.uikit.components.search.ui.SearchMessageInConversationPage
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageInConversationViewModel

class ConversationSearchActivity : BaseActivity() {

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_AVATAR_URL = "avatar_url"

        fun start(
            context: Context,
            conversationID: String,
            displayName: String,
            avatarURL: String? = null
        ) {
            context.startActivity(Intent(context, ConversationSearchActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_AVATAR_URL, avatarURL)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        val conversationID = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        if (conversationID.isBlank()) {
            finish()
            return
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        val root = FrameLayout(this)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        val page = SearchMessageInConversationPage(
            context = this,
            viewModel = ViewModelProvider(this)[SearchMessageInConversationViewModel::class.java]
        ).apply {
            onBack = { finish() }
            onMessageClick = { message ->
                ChatActivity.start(this@ConversationSearchActivity, conversationID, message)
                finish()
            }
        }
        root.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        page.start(
            conversationID = conversationID,
            displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty(),
            avatarURL = intent.getStringExtra(EXTRA_AVATAR_URL)
        )
    }
}
