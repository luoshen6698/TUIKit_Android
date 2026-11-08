package io.trtc.tuikit.chat.demo.xingdun.features.travel

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

/** GET and writes share one lock: an old GET can never overwrite a write receipt. */
internal class TravelRepository(
    private val scope: () -> String?,
    private val request: suspend (JsonObject?) -> JsonObject,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var owner: String? = null
    private var loadedAt = 0L
    private val mutable = MutableStateFlow<TravelCatalog?>(null)
    val snapshots = mutable.asStateFlow()
    private val pending = linkedMapOf<String, String>()
    val current: TravelCatalog? get() = mutable.value.takeIf { owner != null && owner == scope() }
    private fun capture(): String {
        val key = checkNotNull(scope()) { "Travel session unavailable" }
        if (owner != key) { owner = key; mutable.value = null; loadedAt = 0; pending.clear() }
        return key
    }
    suspend fun load(force: Boolean = false) = mutex.withLock {
        val key = capture()
        if (!force && current != null && clock() - loadedAt < 300_000) return@withLock
        val result = TravelCatalog.parse(request(null))
        check(scope() == key) { "Travel session changed" }
        mutable.value = result; loadedAt = clock()
    }
    suspend fun action(body: JsonObject) {
        val submittedScope = checkNotNull(scope()) { "Travel session unavailable" }
        mutex.withLock {
        check(scope() == submittedScope) { "Travel session changed before write" }
        val key = capture()
        val payload = body.toString()
        if (pending.size >= 128 && payload !in pending) pending.remove(pending.keys.first())
        val id = pending.getOrPut(payload) { ByteArray(16).apply { SecureRandom().nextBytes(this) }.joinToString("") { "%02x".format(it.toInt() and 255) } }
        try {
            val result = TravelCatalog.parse(request(body.deepCopy().apply { addProperty("request_id", id) }))
            check(scope() == key) { "Travel session changed" }
            mutable.value = result; loadedAt = clock(); pending.remove(payload)
        } catch (error: XingDunApiException) {
            if (error.businessCode in listOf(400, 409) || error.httpStatus in listOf(400, 409)) pending.remove(payload)
            throw error
        }
        }
    }
}
internal object TravelStore {
    val repository = TravelRepository(
        scope = { XingDunSessionManager.currentUiSession()?.scopeKey },
        request = { body -> withContext(Dispatchers.IO) {
            val session = checkNotNull(XingDunSessionManager.currentSession())
            val api = XingDunSessionManager.apiClient()
            val data: JsonElement = if (body == null) api.get(session, "travel/catalog", emptyMap(), JsonElement::class.java)
            else api.post(session, "travel/action", body, JsonElement::class.java)
            require(data.isJsonObject) { "Invalid travel document" }
            data.asJsonObject
        } },
    )
}
