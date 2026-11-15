package com.codepad.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Gemini API anahtarını cihazda şifreli saklar. */
class SettingsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "codepad_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply()

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"

        // Google model adlarını zaman zaman günceller; ayarlardan değiştirilebilir.
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }
}
