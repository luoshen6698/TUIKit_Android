package io.trtc.tuikit.chat.demo.xingdun.features

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.widget.Toast

internal object XingDunGroupSettingsAccess {
    private val historyChecks = java.util.WeakHashMap<ComponentActivity, Job>()
    fun matches(value: JsonObject, group: String, action: String, target: String): Boolean =
        value.get("allowed")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true &&
            value.get("group_id")?.takeIf { it.isJsonPrimitive }?.asString == group &&
            value.get("action")?.takeIf { it.isJsonPrimitive }?.asString == action &&
            value.get("target_id")?.takeIf { it.isJsonPrimitive }?.asString == target

    suspend fun authorize(group: String, action: String, target: String = "") {
        val session = XingDunSessionManager.currentSession() ?: error("No session")
        val generation = XingDunSessionManager.currentAuthenticationGeneration()
        val response = XingDunSessionManager.apiClient().get<JsonObject>(session, "team/settingsAccess",
            mapOf("team_id" to group, "action" to action, "target_user_id" to target), JsonObject::class.java)
        check(generation != null && generation == XingDunSessionManager.currentAuthenticationGeneration())
        check(matches(response, group, action, target))
    }

    fun history(activity: ComponentActivity, conversation: String, onAllowed: () -> Unit) {
        stopHistory(activity)
        if (!conversation.startsWith("group_")) { onAllowed(); return }
        activity.window.decorView.alpha = 0f
        historyChecks[activity] = activity.lifecycleScope.launch {
            while (isActive) {
                val result = runCatching { authorize(conversation.removePrefix("group_"), "history") }
                if (!isActive || activity.isFinishing || activity.isDestroyed) return@launch
                if (result.isSuccess) { activity.window.decorView.alpha = 1f; onAllowed() }
                else {
                    Toast.makeText(activity, R.string.xingdun_group_access_denied, Toast.LENGTH_LONG).show()
                    activity.finish()
                    return@launch
                }
                // Also reauthorize an already-open page after another client changes its policy.
                delay(10_000L)
            }
        }
    }

    fun stopHistory(activity: ComponentActivity) { historyChecks.remove(activity)?.cancel() }
}
