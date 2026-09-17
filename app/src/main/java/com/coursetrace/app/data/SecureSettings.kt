package com.coursetrace.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("secure_settings", Context.MODE_PRIVATE)
    private val keyAlias = "coursetrace.local.secrets.v1"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    fun putApiKey(value: String) {
        if (value.isBlank()) {
            preferences.edit().remove("api_key").apply()
            return
        }
        preferences.edit().putString("api_key", encrypt(value)).apply()
    }

    fun getApiKey(): String? = preferences.getString("api_key", null)?.let(::decrypt)

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun putRelayToken(value: String) {
        if (value.isBlank()) preferences.edit().remove("relay_token").apply()
        else preferences.edit().putString("relay_token", encrypt(value)).apply()
    }

    fun getRelayToken(): String? = preferences.getString("relay_token", null)?.let(::decrypt)

    fun hasRelayToken(): Boolean = !getRelayToken().isNullOrBlank()

    fun getRelaySnapshotHash(): String? = preferences.getString("relay_snapshot_hash", null)

    fun setRelaySnapshotHash(value: String) = preferences.edit().putString("relay_snapshot_hash", value).apply()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val (iv, body) = encoded.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrNull()
}
