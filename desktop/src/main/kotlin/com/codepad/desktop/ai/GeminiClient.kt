package com.codepad.desktop.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gemini mesajı: role "user" veya "model". */
data class ChatMessage(val role: String, val text: String)

/** Ek kütüphane gerektirmeyen, doğrudan Gemini REST istemcisi. */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

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

            val conn = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream.bufferedReader().use { it.readText() }
            conn.disconnect()

            if (code !in 200..299) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }
                    .getOrDefault(raw.take(200))
                error("Gemini hatası ($code): $msg")
            }

            val parts = JSONObject(raw)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            buildString {
                for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
            }.trim().ifBlank { error("Gemini boş yanıt döndürdü.") }
        }
    }
}
