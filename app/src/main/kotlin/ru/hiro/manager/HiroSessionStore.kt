package ru.hiro.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HiroSessionStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun lastServerUrl(): String? = preferences.getString(LAST_SERVER_URL, null)

    fun save(session: HiroSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val saved = preferences.edit()
            .putString(ENCRYPTED_SESSION, payload)
            .putString(LAST_SERVER_URL, session.serverUrl)
            .commit()
        check(saved) { "Could not persist encrypted session" }
    }

    fun load(): HiroSession? {
        val payload = preferences.getString(ENCRYPTED_SESSION, null) ?: return null
        return try {
            val parts = payload.split('.', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            cipher.updateAAD(AAD)
            val cleartext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            json.decodeFromString<HiroSession>(cleartext.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    fun clearSession() {
        preferences.edit().remove(ENCRYPTED_SESSION).commit()
    }

    fun savePendingOIDC(pending: HiroPendingOIDC) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(OIDC_AAD)
        val encrypted = cipher.doFinal(json.encodeToString(pending).toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(ENCRYPTED_OIDC, payload).commit()) {
            "Could not persist OIDC login state"
        }
    }

    fun loadPendingOIDC(): HiroPendingOIDC? {
        val payload = preferences.getString(ENCRYPTED_OIDC, null) ?: return null
        return try {
            val parts = payload.split('.', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            cipher.updateAAD(OIDC_AAD)
            val cleartext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            json.decodeFromString<HiroPendingOIDC>(cleartext.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            clearPendingOIDC()
            null
        }
    }

    fun clearPendingOIDC() {
        preferences.edit().remove(ENCRYPTED_OIDC).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "hiro_server_session"
        private const val ENCRYPTED_SESSION = "encrypted_session"
        private const val ENCRYPTED_OIDC = "encrypted_oidc"
        private const val LAST_SERVER_URL = "last_server_url"
        private const val KEY_ALIAS = "ru.hiro.manager.server.session.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val AAD = "HI-RO Connect server session v1".toByteArray(Charsets.UTF_8)
        private val OIDC_AAD = "HI-RO Connect OIDC state v1".toByteArray(Charsets.UTF_8)
    }
}
