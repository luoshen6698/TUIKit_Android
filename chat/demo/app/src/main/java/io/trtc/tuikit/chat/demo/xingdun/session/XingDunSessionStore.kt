package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
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

    fun save(session: XingDunStoredSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(gson.toJson(session).toByteArray(Charsets.UTF_8))
        securePreferences.edit()
            .putString(KEY_SESSION_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_SESSION_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): XingDunStoredSession? {
        val encodedIv = securePreferences.getString(KEY_SESSION_IV, null) ?: return null
        val encodedData = securePreferences.getString(KEY_SESSION_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            val json = cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            gson.fromJson(json, XingDunStoredSession::class.java)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        securePreferences.edit().clear().apply()
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

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

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
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "xingdun.session.aes.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
