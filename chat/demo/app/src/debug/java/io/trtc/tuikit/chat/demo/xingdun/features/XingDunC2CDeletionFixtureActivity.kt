package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessage
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.C2cMediaPreviewFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Debug-only acceptance entry. Fixed loopback fixture; never loads or modifies a production session. */
open class XingDunC2CDeletionFixtureActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var user = "alice"
    private var offline = false
    private var loseNextResponse = false
    private var lastOutcome = "not-run"
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var markers: XingDunC2CDeletionMarkerStore
    private lateinit var requests: XingDunC2CDeletionRequests
    private lateinit var actions: XingDunC2CDeletionActions
    private var closeMedia: (() -> Unit)? = null

    protected open fun fixtureLocale(): Locale = Locale.SIMPLIFIED_CHINESE
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(fixtureLocale())
        }))
    }

    private fun fixtureAccount() = "debug-localhost-5311:fixture:12345:$user"
    private fun peer() = if (user == "alice") "bob" else "alice"
    private fun ownMessage() = if (user == "alice") message("fixture-msg-1", "alice") else message("fixture-msg-2", "bob")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        user = intent.getStringExtra("fixture_user")?.takeIf { it == "alice" || it == "bob" } ?: "alice"
        offline = getPreferences(MODE_PRIVATE).getBoolean("offline", false)
        markers = XingDunC2CDeletionMarkerStore(this, ::fixtureAccount) { runOnUiThread {
            SearchMessageVisibility.invalidate(); render()
        } }
        SearchMessageVisibility.setDeletedProvider { conversation, id ->
            conversation == "c2c_${peer()}" && id in markers.read(peer())
        }
        requests = XingDunC2CDeletionRequests(
            ::fixtureAccount,
            query = { peer, ids ->
                val response = post("c2cDeleted", JsonObject().apply {
                    addProperty("peer_user_id", peer)
                    add("message_ids", JsonParser.parseString(ids.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")))
                })
                XingDunC2CDeletionPolicy.deletedIDs(peer, ids, response)
            },
            remove = { peer, id ->
                val response = post("c2cDelete", JsonObject().apply {
                    addProperty("peer_user_id", peer); addProperty("message_server_id", id)
                })
                XingDunC2CDeletionPolicy.validateDeleted(peer, id, response)
            },
            remember = { account, peer, ids -> markers.remember(account, peer, ids) },
        )
        SearchMessageVisibility.setProvider { _, messages ->
            val ids = SearchMessageVisibility.candidateIDs(messages).filter(XingDunC2CDeletionPolicy::validID).toSet()
            if (ids.isNotEmpty()) withContext(Dispatchers.IO) { requests.check(peer(), ids) }
            messages.filterNot { it.msgID in markers.read(peer()) }
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 72, 24, 24)
        }
        setContentView(content)
        actions = XingDunC2CDeletionActions(this, scope, "c2c_${peer()}", { user }, ::fixtureAccount,
            delete = { _, id -> withContext(Dispatchers.IO) { requests.delete(peer(), id) } },
            onResult = { lastOutcome = if (it) "confirmed" else "unconfirmed"; render() },
            session = ::fixtureAccount,
        )
        render()
        refresh()
    }

    private fun render() {
        if (!::content.isInitialized || isDestroyed) return
        content.removeAllViews()
        content.addView(TextView(this).apply { text = "C2C deletion / localhost fixture"; textSize = 21f })
        status = TextView(this).apply {
            text = "scope=${fixtureAccount()}\nnetwork=${if (offline) "offline" else "online"}; result=$lastOutcome\nmarkers=${markers.read(peer()).sorted()}"
            textSize = 14f
        }.also(content::addView)
        val candidates = listOf(message("fixture-msg-1", "alice"), message("fixture-msg-2", "bob"),
            message("fixture-redpacket", user).copy(messageType = MessageType.CUSTOM,
                messagePayload = CustomMessagePayload(customData = """{"xd_type":"redpacket"}""")))
        candidates.filterNot { it.msgID in markers.read(peer()) }.forEach { message ->
            content.addView(Button(this).apply {
                text = "${message.from.userID}: ${message.msgID}"
                setOnClickListener { showMessageMenu(this, message) }
                setOnLongClickListener { showMessageMenu(this, message); true }
            })
        }
        val reply = message("fixture-reply", peer()).copy(quoteInfo = MessageQuoteInfo(
            msgID = "fixture-msg-1", status = MessageStatus.SEND_SUCCESS,
            messagePayload = TextMessagePayload(text = "fixture-original-snapshot")))
        val quote = SearchMessageVisibility.redactQuotes("c2c_${peer()}", listOf(reply)).single().quoteInfo
        content.addView(TextView(this).apply {
            text = "quote-only target=fixture-msg-1; status=${quote?.status}; snapshot=${(quote?.messagePayload as? TextMessagePayload)?.text.orEmpty()}"
        })
        val pins = XingDunPinnedMessagePolicy.visiblePins(
            listOf(XingDunPinnedMessage(messageId = "fixture-msg-1", isPinned = true)), markers.read(peer()))
        content.addView(TextView(this).apply { text = "persisted-pin visible=${pins.map { it.messageId }}" })
        addButton(if (offline) "Restore fixture connection" else "Disconnect fixture requests") {
            offline = !offline
            getPreferences(MODE_PRIVATE).edit().putBoolean("offline", offline).commit()
            render(); if (!offline) refresh()
        }
        addButton("Refresh authoritative tombstones") { refresh() }
        addButton("Open independent media preview") {
            closeMedia?.invoke()
            closeMedia = C2cMediaPreviewFixture.open(this, scope, "c2c_${peer()}")
        }
        addButton("Refresh quote-only target (original page unloaded)") {
            scope.launch {
                try {
                    val reply = message("fixture-reply", peer()).copy(quoteInfo = MessageQuoteInfo(msgID = "fixture-msg-1"))
                    val candidates = SearchMessageVisibility.candidateIDs(listOf(reply))
                    withContext(Dispatchers.IO) { requests.check(peer(), candidates) }
                    lastOutcome = "quote-only-authoritative-refresh"
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { lastOutcome = "quote-only-offline-kept-markers" }
                render()
            }
        }
        addButton("Repeat same own-message deletion") { actions.confirm(ownMessage()) }
        addButton("Forged hint for undeleted peer message") {
            val peerID = if (user == "alice") "fixture-msg-2" else "fixture-msg-1"
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { requests.check(peer(), setOf(peerID)) }
                    lastOutcome = "hint-checked-no-optimistic-delete"
                } catch (_: Exception) { lastOutcome = "hint-unconfirmed" }
                render()
            }
        }
        addButton("Lose next mutation response") { loseNextResponse = true; lastOutcome = "next-response-unknown"; render() }
    }

    private fun addButton(label: String, action: () -> Unit) = content.addView(Button(this).apply {
        text = label; setOnClickListener { action() }
    })

    private fun showMessageMenu(anchor: android.view.View, message: MessageInfo) {
        PopupMenu(this, anchor).apply {
            if (actions.canOffer(message)) menu.add(getString(R.string.xingdun_c2c_delete_action)).setOnMenuItemClickListener {
                actions.confirm(message); true
            }
            menu.add(getString(R.string.xingdun_c2c_delete_for_me))
            show()
        }
    }

    private fun refresh() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { requests.check(peer(), setOf("fixture-msg-1", "fixture-msg-2")) }
                lastOutcome = "authoritative-refresh"
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { lastOutcome = "offline-kept-existing-markers" }
            render()
        }
    }

    private fun message(id: String, sender: String) = MessageInfo(
        msgID = id, status = MessageStatus.SEND_SUCCESS,
        from = MessageSenderInfo(userID = sender), isSentBySelf = sender == user,
    )

    private fun post(action: String, body: JsonObject): JsonObject {
        if (offline) throw IOException("Fixture disconnected")
        val requestedUser = user
        val connection = URL("http://127.0.0.1:5311/im/v1/message/$action").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 4_000; connection.readTimeout = 4_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-Fixture-User-ID", if (requestedUser == "alice") "1" else "2")
        if (action == "c2cDelete" && loseNextResponse) {
            loseNextResponse = false
            connection.setRequestProperty("X-Fixture-Response-Outcome", "unknown")
        }
        connection.doOutput = true
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) throw IOException("Fixture response unavailable")
            val envelope = connection.inputStream.bufferedReader().use { JsonParser.parseString(it.readText()).asJsonObject }
            check(envelope["code"].asInt == 200)
            return envelope["data"].asJsonObject
        } finally { connection.disconnect() }
    }

    override fun onDestroy() { closeMedia?.invoke(); scope.cancel(); super.onDestroy() }
}

class XingDunC2CDeletionEnglishFixtureActivity : XingDunC2CDeletionFixtureActivity() {
    override fun fixtureLocale(): Locale = Locale.US
}
