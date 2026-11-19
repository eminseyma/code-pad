package com.codepad.desktop

import com.codepad.desktop.ai.ChatMessage
import com.codepad.desktop.ai.GeminiClient
import org.json.JSONObject

/** Asistan sohbeti (yalnızca bu bilgisayarda saklanır). */
data class ChatSess(val id: String, val title: String, val createdAt: Long)

/** Sohbetteki tek mesaj. role: "user" veya "model". */
data class ChatMsg(val id: String, val sessionId: String, val role: String, val text: String, val createdAt: Long)

fun parseSess(o: JSONObject) = ChatSess(o.getString("id"), o.optString("title"), o.optLong("created_at"))

fun sessJson(s: ChatSess): JSONObject = JSONObject().put("id", s.id).put("title", s.title).put("created_at", s.createdAt)

fun parseMsg(o: JSONObject) = ChatMsg(o.getString("id"), o.optString("session_id"), o.optString("role"), o.optString("text"), o.optLong("created_at"))

fun msgJson(m: ChatMsg): JSONObject = JSONObject()
    .put("id", m.id)
    .put("session_id", m.sessionId)
    .put("role", m.role)
    .put("text", m.text)
    .put("created_at", m.createdAt)

object Assistant {
    /** Soruyla en ilgili notları bulur (anahtar kelime puanlaması). Eşleşme yoksa en son güncellenenler. */
    private fun relevant(query: String, notes: List<Nt>): List<Nt> {
        val live = notes.filter { !it.deleted }
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        if (words.isEmpty()) return live.sortedByDescending { it.updatedAt }.take(5)
        val ranked = live
            .map { n ->
                val t = n.title.lowercase()
                val b = n.content.lowercase()
                n to words.sumOf { w -> (if (t.contains(w)) 3 else 0) + (if (b.contains(w)) 1 else 0) }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        return ranked.ifEmpty { live.sortedByDescending { it.updatedAt }.take(5) }
    }

    /** Sohbet geçmişiyle birlikte soruyu yanıtlar; kullanıcı notlarını bağlam olarak verir. */
    suspend fun ask(sessionId: String, question: String): Result<String> {
        val context = relevant(question, Store.notes.toList())
        val system = buildString {
            append("Sen Code Pad adlı yazılım not uygulamasının asistanısın. Türkçe yanıt ver. ")
            append("Önce kullanıcının notlarına dayan; notlarda yoksa bunu söyle, sonra genel bilginle tamamla. ")
            append("Hangi notlardan yararlandığını sonda 'Kaynak:' satırında not başlıklarıyla belirt.\n\n")
            append("KULLANICI NOTLARI:\n")
            if (context.isEmpty()) append("(ilgili not bulunamadı)")
            context.forEach { append("### ${it.title.ifBlank { "Başlıksız" }}\n${it.content.take(1_500)}\n\n") }
        }
        val history = Store.messages
            .filter { it.sessionId == sessionId }
            .sortedBy { it.createdAt }
            .map { ChatMessage(if (it.role == "user") "user" else "model", it.text) }
        return GeminiClient.generate(Store.apiKey, Store.model, system, history)
    }
}
