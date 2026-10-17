package com.vidgravity.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DownloadPreferences(
    val autoDownloadWithPreferredPreset: Boolean = false,
    val preferredFormatSelector: String = "bv*+ba/b",
    val unmeteredOnly: Boolean = false,
    val instantDownloadSharedLinks: Boolean = false
)

object SecureDownloadPreferences {
    private const val STORE = "vg_secure_download_preferences"
    private const val DATA = "ciphertext"
    private const val IV = "iv"
    private const val ALIAS = "vg-preferences-aes"

    @Synchronized
    fun load(context: Context): DownloadPreferences {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val data = prefs.getString(DATA, null) ?: return DownloadPreferences()
        val iv = prefs.getString(IV, null) ?: return DownloadPreferences()
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val json = JSONObject(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8))
            DownloadPreferences(
                autoDownloadWithPreferredPreset = json.optBoolean("autoDownloadWithPreferredPreset", false),
                preferredFormatSelector = json.optString("preferredFormatSelector", "bv*+ba/b").take(200),
                unmeteredOnly = json.optBoolean("unmeteredOnly", false),
                instantDownloadSharedLinks = json.optBoolean("instantDownloadSharedLinks", false)
            )
        }.getOrDefault(DownloadPreferences())
    }

    @Synchronized
    fun save(context: Context, value: DownloadPreferences) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(
            JSONObject()
                .put("autoDownloadWithPreferredPreset", value.autoDownloadWithPreferredPreset)
                .put("preferredFormatSelector", value.preferredFormatSelector.take(200))
                .put("unmeteredOnly", value.unmeteredOnly)
                .put("instantDownloadSharedLinks", value.instantDownloadSharedLinks)
                .toString().toByteArray(Charsets.UTF_8)
        )
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
