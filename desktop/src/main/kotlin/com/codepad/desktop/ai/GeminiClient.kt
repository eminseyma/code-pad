package com.codepad.desktop.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gemini mesajı: role "user" veya "model". */
data class ChatMessage(val role: String, val text: String)

/**
 * Ek kütüphane gerektirmeyen, doğrudan Gemini REST istemcisi.
 * Geçici hatalarda (503/429/500) kısa beklemeyle tekrar dener; hâlâ olmuyorsa
 * hesabın kullanabildiği diğer "flash" modellerine otomatik geçer.
 */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private var fallbackCache: List<String>? = null

    private class Reply(val code: Int, val raw: String)

    private fun post(apiKey: String, model: String, body: JSONObject): Reply {
        val conn = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            Reply(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    private fun isTransient(code: Int) = code == 429 || code == 500 || code == 502 || code == 503 || code == 504

    /** Hesabın erişebildiği, yazı üreten "flash" modellerini (kararlı olanlar önce) listeler. */
    private fun fallbackModels(apiKey: String, current: String): List<String> {
        fallbackCache?.let { cache -> return cache.filter { it != current }.take(2) }
        val list = runCatching {
            val conn = (URL("$BASE?pageSize=200").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val raw = try {
                if (conn.responseCode !in 200..299) return@runCatching emptyList<String>()
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val arr = JSONObject(raw).optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                val canGenerate = (0 until (methods?.length() ?: 0)).any { methods!!.getString(it) == "generateContent" }
                val bad = listOf("image", "tts", "live", "audio", "embedding", "vision", "robotics", "computer", "exp")
                if (canGenerate && "flash" in name && bad.none { it in name }) names += name
            }
            // Kararlı (preview olmayan) önce, sonra yeni sürüm önce
            names.sortedWith(compareBy<String>({ "preview" in it }, { "lite" in it }).thenByDescending { it })
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) fallbackCache = list
        return list.filter { it != current }.take(2)
    }

    suspend fun generate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Önce Ayarlar'dan Gemini API anahtarını gir." }

            val contents = JSONArray()
            messages.forEach { m ->
                contents.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text))),
                )
            }
            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                )
                .put("contents", contents)

            val models = mutableListOf(model)
            var i = 0
            var last: Reply? = null
            while (i < models.size) {
                val m = models[i]
                var attempt = 0
                var reply: Reply
                while (true) {
                    reply = post(apiKey, m, body)
                    if (reply.code in 200..299 || !isTransient(reply.code) || attempt >= 1) break
                    attempt++
                    Thread.sleep(1_500L * attempt)
                }
                last = reply
                if (reply.code in 200..299) {
                    return@runCatching parse(reply.raw)
                }
                // Bu model yoğun ya da yok: yedek modellere geç
                if (isTransient(reply.code) || reply.code == 404) {
                    if (i == 0) models += fallbackModels(apiKey, model)
                    i++
                    continue
                }
                break
            }

            val l = last!!
            if (isTransient(l.code)) {
                error("Gemini şu an çok yoğun (kod ${l.code}). Birkaç dakika sonra tekrar dene; istersen Ayarlar'dan başka bir model seçebilirsin.")
            }
            val msg = runCatching { JSONObject(l.raw).getJSONObject("error").getString("message") }
                .getOrDefault(l.raw.take(200))
            error("Gemini hatası (${l.code}): $msg")
        }
    }

    private fun parse(raw: String): String {
        val cand = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
            ?: error("Gemini bu isteğe yanıt vermedi (içerik filtrelenmiş olabilir).")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
            ?: error("Gemini boş yanıt döndürdü.")
        return buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }.trim().ifBlank { error("Gemini boş yanıt döndürdü.") }
    }
}
