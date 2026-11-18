package com.codepad.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Editörde görünen AI eylemi. Kullanıcı bunları düzenleyebilir. */
data class AiAction(val id: String, val label: String, val instruction: String)

val DefaultActions = listOf(
    AiAction("explain", "Açıkla", "Notu bir yazılım öğrencisine anlatır gibi sade Türkçe ile açıkla. Kısa ol."),
    AiAction("summary", "Özetle", "Notu en fazla 5 maddede özetle."),
    AiAction("shorten", "Kısalt", "Metni anlamını koruyarak yaklaşık yarı uzunluğa kısalt. Sadece kısaltılmış metni ver."),
    AiAction("expand", "Uzat", "Metni örneklerle ve açıklamalarla zenginleştirerek uzat. Sadece uzatılmış metni ver."),
    AiAction("example", "Örnek ekle", "Konuya uygun, kısa ve çalışır bir kod örneği yaz. Kodu ``` kod bloğu içinde ver."),
    AiAction("bugs", "Hata bul", "Kodda hata, eksik ya da yanlış bilgi var mı? Varsa düzeltmesiyle kısaca açıkla."),
)

enum class ThemeChoice { MINT, WHITE, BLACK }

/** Gemini anahtarı, model, tema ve AI eylemlerini cihazda şifreli saklar. */
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

    var theme: ThemeChoice
        get() = runCatching { ThemeChoice.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeChoice.MINT)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    // ---- Bulut oturumu ----
    var accessToken: String
        get() = prefs.getString(KEY_ACCESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** Son başarılı senkronun başlangıç zamanı (ms). 0 ise her şey yeniden gönderilir. */
    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    val hasSession: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()

    fun saveSession(s: com.codepad.app.sync.SupabaseClient.Session) {
        // Farklı bir hesaba girildiyse tüm yerel kayıtlar yeniden gönderilsin diye senkron zamanını sıfırla.
        if (s.userId != userId) lastSync = 0L
        accessToken = s.accessToken
        refreshToken = s.refreshToken
        userId = s.userId
        userEmail = s.email
    }

    fun clearSession() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_USER).remove(KEY_EMAIL).remove(KEY_LAST_SYNC).apply()
    }

    fun actions(): List<AiAction> {
        val raw = prefs.getString(KEY_ACTIONS, null) ?: return DefaultActions
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AiAction(o.getString("id"), o.getString("label"), o.getString("instruction"))
            }
        } catch (e: Exception) {
            DefaultActions
        }
    }

    fun saveActions(list: List<AiAction>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("label", a.label).put("instruction", a.instruction))
        }
        prefs.edit().putString(KEY_ACTIONS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_THEME = "theme"
        private const val KEY_ACTIONS = "ai_actions"
        private const val KEY_ACCESS = "sb_access"
        private const val KEY_REFRESH = "sb_refresh"
        private const val KEY_USER = "sb_user"
        private const val KEY_EMAIL = "sb_email"
        private const val KEY_LAST_SYNC = "sb_last_sync"

        // Google model adlarını zaman zaman günceller; ayarlardan değiştirilebilir.
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }
}
