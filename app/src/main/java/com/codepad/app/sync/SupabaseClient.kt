package com.codepad.app.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase için ince HTTP istemcisi (ek kütüphane yok).
 * Kimlik doğrulama: e-posta + şifre. Veri: PostgREST (tablo API'si), RLS sayesinde sadece kendi satırların.
 */
object SupabaseClient {
    // Herkese açık (publishable) anahtar: uygulamaya gömülmesi güvenlidir; asıl koruma RLS kurallarıdır.
    const val BASE_URL = "https://okgnefzgvkzbzrzizoqv.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_4CXN5jZqdYxhBqsnDGprOw_75WFHM2U"

    data class Session(val accessToken: String, val refreshToken: String, val userId: String, val email: String)

    class ApiException(message: String, val code: Int = 0) : Exception(message)

    private fun call(
        method: String,
        url: String,
        body: String? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: PUBLISHABLE_KEY}")
            setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) throw ApiException(friendlyError(text, code), code)
        return text
    }

    private fun friendlyError(text: String, code: Int): String {
        val msg = runCatching {
            val o = JSONObject(text)
            o.optString("msg").ifBlank { o.optString("error_description") }.ifBlank { o.optString("message") }
        }.getOrNull().orEmpty()
        return when {
            msg.contains("Invalid login credentials", true) -> "E-posta veya şifre hatalı."
            msg.contains("already registered", true) -> "Bu e-posta ile zaten bir hesap var. Giriş yap."
            msg.contains("Password should be", true) -> "Şifre en az 6 karakter olmalı."
            msg.contains("Email not confirmed", true) -> "E-posta henüz onaylanmamış. Supabase'de bu kullanıcıyı onaylamalısın."
            msg.contains("rate limit", true) || msg.contains("too many", true) -> "Çok fazla deneme yapıldı. Birkaç dakika sonra tekrar dene."
            msg.isNotBlank() -> msg
            else -> "Sunucu hatası ($code)."
        }
    }

    /** Yeni hesap açar. Onay kapalıysa hemen oturum döner. */
    fun signUp(email: String, password: String): Session {
        val body = JSONObject().put("email", email).put("password", password).toString()
        return parseSession(call("POST", "$BASE_URL/auth/v1/signup", body))
    }

    fun signIn(email: String, password: String): Session {
        val body = JSONObject().put("email", email).put("password", password).toString()
        return parseSession(call("POST", "$BASE_URL/auth/v1/token?grant_type=password", body))
    }

    /** Şifre sıfırlama kodunu e-postaya gönderir. */
    fun sendRecoveryCode(email: String) {
        call("POST", "$BASE_URL/auth/v1/recover", JSONObject().put("email", email).toString())
    }

    /** E-postaya gelen kodu doğrular ve oturum açar. */
    fun verifyRecovery(email: String, code: String): Session {
        val body = JSONObject().put("type", "recovery").put("email", email).put("token", code).toString()
        return parseSession(call("POST", "$BASE_URL/auth/v1/verify", body))
    }

    /** Oturum açıldıktan sonra yeni şifreyi kaydeder. */
    fun updatePassword(accessToken: String, newPassword: String) {
        call("PUT", "$BASE_URL/auth/v1/user", JSONObject().put("password", newPassword).toString(), accessToken)
    }

    /** Süresi dolan erişim jetonunu yeniler. */
    fun refresh(refreshToken: String): Session {
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        return parseSession(call("POST", "$BASE_URL/auth/v1/token?grant_type=refresh_token", body))
    }

    private fun parseSession(text: String): Session {
        val o = JSONObject(text)
        if (!o.has("access_token")) {
            throw ApiException("Hesap açıldı. Giriş yapmadan önce Supabase'de Confirm email seçeneğinin kapalı olması gerekiyor.")
        }
        val user = o.getJSONObject("user")
        return Session(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            userId = user.getString("id"),
            email = user.optString("email"),
        )
    }

    /** Tablodan satırları çeker. `since` verilirse sadece o zamandan sonra değişenleri getirir. */
    fun select(accessToken: String, table: String, sinceUpdatedAt: Long): JSONArray {
        val url = "$BASE_URL/rest/v1/$table?select=*&updated_at=gt.$sinceUpdatedAt&order=updated_at.asc"
        return JSONArray(call("GET", url, accessToken = accessToken))
    }

    /** Satırları ekler veya günceller (id çakışırsa üzerine yazar). */
    fun upsert(accessToken: String, table: String, rows: JSONArray) {
        if (rows.length() == 0) return
        call(
            "POST",
            "$BASE_URL/rest/v1/$table?on_conflict=id",
            body = rows.toString(),
            accessToken = accessToken,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }
}
