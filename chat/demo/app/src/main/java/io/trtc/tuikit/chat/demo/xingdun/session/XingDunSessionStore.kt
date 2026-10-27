package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class XingDunSessionStore(context: Context) {

    private val appContext = context.applicationContext
    private val securePreferences = appContext.getSharedPreferences(SECURE_PREFERENCES, Context.MODE_PRIVATE)
    private val devicePreferences = appContext.getSharedPreferences(DEVICE_PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    // Same preference namespace shares one process-local write barrier across Store instances.
    private val readRevision = XingDunSessionStorageRevision.forStore(
        "${appContext.applicationInfo.dataDir}:${android.os.Build.VERSION.SDK_INT >= 24 && appContext.isDeviceProtectedStorage}:$SECURE_PREFERENCES"
    )
    internal fun currentReadRevision(): Long? = readRevision.current()


    fun save(session: XingDunStoredSession) {
        saveEncrypted(KEY_SESSION_IV, KEY_SESSION_DATA, session)
    }

    fun load(): XingDunStoredSession? =
        loadEncrypted(KEY_SESSION_IV, KEY_SESSION_DATA, XingDunStoredSession::class.java)

    /** Presence is separate from readability, including partially written encrypted records. */
    fun hasStoredSession(): Boolean =
        securePreferences.contains(KEY_SESSION_IV) || securePreferences.contains(KEY_SESSION_DATA)

    fun loadBoundSession(): XingDunStoredSession? = XingDunSessionBindingPolicy.resolve(
        session = load(),
        enterprise = loadEnterprise(),
        clearMismatchedSession = ::clearSession,
    )

    /** Read-only display snapshot: a mixed/unreadable binding must never clear new credentials. */
    internal fun loadDisplayBoundSession(): XingDunStoredSession? =
        XingDunSessionBindingPolicy.resolveForDisplay(load(), loadEnterprise())

    fun isBoundToCurrentEnterprise(session: XingDunStoredSession): Boolean =
        runCatching { XingDunTenantBoundary.matches(session, loadEnterprise()) }.getOrDefault(false)

    fun saveEnterprise(configuration: XingDunBootstrapConfiguration) {
        saveEncrypted(KEY_ENTERPRISE_IV, KEY_ENTERPRISE_DATA, configuration)
    }

    fun loadEnterprise(): XingDunBootstrapConfiguration? =
        loadEncrypted(KEY_ENTERPRISE_IV, KEY_ENTERPRISE_DATA, XingDunBootstrapConfiguration::class.java)

    fun clear() = clearSession()

    fun clearSession() = readRevision.write {
        securePreferences.edit()
            .remove(KEY_SESSION_IV)
            .remove(KEY_SESSION_DATA)
            .apply()
        Unit
    }

    fun clearEnterprise() = readRevision.write {
        securePreferences.edit()
            .remove(KEY_ENTERPRISE_IV)
            .remove(KEY_ENTERPRISE_DATA)
            .apply()
        Unit
    }

    fun deviceId(): String {
        val existing = devicePreferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString().lowercase()
        devicePreferences.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun saveEncrypted(ivKey: String, dataKey: String, value: Any) = readRevision.write {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(gson.toJson(value).toByteArray(Charsets.UTF_8))
        securePreferences.edit()
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(dataKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun <T> loadEncrypted(ivKey: String, dataKey: String, type: Class<T>): T? {
        return try {
            val encodedIv = securePreferences.getString(ivKey, null) ?: return null
            val encodedData = securePreferences.getString(dataKey, null) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(createIfMissing = false),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            val json = cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            gson.fromJson(json, type)
        } catch (_: Exception) {
            // Keystore or decoding failures are not proof that the server revoked this session.
            null
        }
    }

    private fun secretKey(createIfMissing: Boolean = true): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(createIfMissing) { "Stored session key is unavailable" }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val SECURE_PREFERENCES = "xingdun_secure_session"
        private const val DEVICE_PREFERENCES = "xingdun_device"
        private const val KEY_SESSION_IV = "session_iv"
        private const val KEY_SESSION_DATA = "session_data"
        private const val KEY_ENTERPRISE_IV = "enterprise_iv"
        private const val KEY_ENTERPRISE_DATA = "enterprise_data"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "xingdun.session.aes.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}

/** Unavailable or malformed snapshots must not be treated as a confirmed enterprise switch. */
internal object XingDunSessionBindingPolicy {
    fun resolveForDisplay(session: XingDunStoredSession?, enterprise: XingDunBootstrapConfiguration?): XingDunStoredSession? =
        resolve(session, enterprise, clearMismatchedSession = {})

    fun resolve(
        session: XingDunStoredSession?,
        enterprise: XingDunBootstrapConfiguration?,
        clearMismatchedSession: () -> Unit,
    ): XingDunStoredSession? {
        val sessionIdentity = runCatching { XingDunTenantBoundary.identity(session) }.getOrNull() ?: return null
        val enterpriseIdentity = runCatching { XingDunTenantBoundary.identity(enterprise) }.getOrNull() ?: return null
        if (!sessionIdentity.matches(enterpriseIdentity)) {
            clearMismatchedSession()
            return null
        }
        return session
    }
}

/** Metadata only: no session/token/key cache, and its monitor never surrounds storage or Cipher work.
 * This process-local barrier is not a cross-process SharedPreferences protocol. */
internal class XingDunSessionStorageRevision private constructor() {
    private val metadata = Any()
    private var version = 0L
    private var writers = 0
    fun current(): Long? = synchronized(metadata) { if (writers == 0) version else null }
    fun <T> write(action: () -> T): T {
        synchronized(metadata) { version++; writers++ }
        try { return action() }
        finally { synchronized(metadata) { version++; writers-- } }
    }
    companion object {
        private val stores = ConcurrentHashMap<String, XingDunSessionStorageRevision>()
        fun forStore(namespace: String): XingDunSessionStorageRevision =
            stores.getOrPut(namespace) { XingDunSessionStorageRevision() }
    }
}
