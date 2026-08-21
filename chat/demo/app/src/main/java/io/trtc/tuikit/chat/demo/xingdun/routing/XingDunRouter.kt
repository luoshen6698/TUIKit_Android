package io.trtc.tuikit.chat.demo.xingdun.routing

import android.content.Context
import android.content.Intent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager

object XingDunRouter {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun routeNotification(extension: String?) {
        val payload = extension?.trim().orEmpty()
        if (payload.isEmpty() || payload.toByteArray().size > MAX_PAYLOAD_BYTES) return
        MMKV.defaultMMKV().encode(KEY_PENDING_PUSH_ROUTE, payload)
        if (XingDunSessionManager.currentSession() == null) {
            launch(XingDunLaunchActivity::class.java)
            return
        }
        consumePendingRoute()
    }

    fun consumePendingRoute() {
        if (!::appContext.isInitialized || XingDunSessionManager.currentSession() == null) return
        val raw = MMKV.defaultMMKV().decodeString(KEY_PENDING_PUSH_ROUTE, "").orEmpty()
        if (raw.isEmpty()) return
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: run {
            MMKV.defaultMMKV().removeValueForKey(KEY_PENDING_PUSH_ROUTE)
            return
        }
        MMKV.defaultMMKV().removeValueForKey(KEY_PENDING_PUSH_ROUTE)

        val route = json.string("route")?.lowercase()
        val pushVersion = json.int("xd_push_version")
        val voipVersion = json.int("xd_voip_version")
        val conversationType = json.string("conversation_type")?.lowercase()
        val target = json.string("target")
        when {
            voipVersion == VOIP_PAYLOAD_VERSION -> {
                val conversationID = json.string("conversation_id")
                if (conversationID?.startsWith("c2c_") == true || conversationID?.startsWith("group_") == true) {
                    ChatActivity.start(appContext, conversationID)
                } else {
                    launchMain(MainActivity.TAB_MESSAGES)
                }
            }
            pushVersion != PUSH_PAYLOAD_VERSION && route == null -> Unit
            target != null && conversationType in setOf("c2c", "direct", "single") ->
                ChatActivity.start(appContext, "c2c_$target")
            target != null && conversationType == "customer_service" ->
                ChatActivity.start(appContext, "c2c_$target")
            target != null && conversationType in setOf("group", "team") ->
                ChatActivity.start(appContext, "group_$target")
            route in setOf("friend_application", "group_application", "contacts") ->
                launchMain(MainActivity.TAB_CONTACTS)
            route in setOf("workspace", "customer_service") ->
                launchMain(MainActivity.TAB_WORKSPACE)
            route == "profile" -> launchMain(MainActivity.TAB_PROFILE)
            else -> launchMain(MainActivity.TAB_MESSAGES)
        }
    }

    private fun launchMain(tab: String) {
        appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_TARGET_TAB, tab)
        })
    }

    private fun launch(activity: Class<*>) {
        appContext.startActivity(Intent(appContext, activity).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt

    private const val KEY_PENDING_PUSH_ROUTE = "xingdun.pending.push.route"
    private const val PUSH_PAYLOAD_VERSION = 1
    private const val VOIP_PAYLOAD_VERSION = 1
    private const val MAX_PAYLOAD_BYTES = 1024
}
