package com.codepad.desktop

import com.codepad.desktop.ai.ChatMessage
import com.codepad.desktop.ai.GeminiClient

/** Editörde görünen AI eylemi. Kullanıcı düzenleyebilir (telefonla aynı varsayılanlar). */
data class AiAction(val id: String, val label: String, val instruction: String)

val DefaultActions = listOf(
    AiAction("explain", "Açıkla", "Notu bir yazılım öğrencisine anlatır gibi sade Türkçe ile açıkla. Kısa ol."),
    AiAction("summary", "Özetle", "Notu en fazla 5 maddede özetle."),
    AiAction("shorten", "Kısalt", "Metni anlamını koruyarak yaklaşık yarı uzunluğa kısalt. Sadece kısaltılmış metni ver."),
    AiAction("expand", "Uzat", "Metni örneklerle ve açıklamalarla zenginleştirerek uzat. Sadece uzatılmış metni ver."),
    AiAction("example", "Örnek ekle", "Konuya uygun, kısa ve çalışır bir kod örneği yaz. Kodu ``` kod bloğu içinde ver."),
    AiAction("bugs", "Hata bul", "Kodda hata, eksik ya da yanlış bilgi var mı? Varsa düzeltmesiyle kısaca açıkla."),
)

const val DefaultModel = "gemini-3.6-flash"

object Ai {
    /** Verilen yönergeyle girdiyi Gemini'ye gönderir. */
    suspend fun run(instruction: String, title: String, input: String): Result<String> =
        GeminiClient.generate(
            Store.apiKey,
            Store.model,
            "Sen Code Pad adlı yazılım not uygulamasının asistanısın. Türkçe, kısa ve net yanıt ver. $instruction",
            listOf(ChatMessage("user", "Not başlığı: $title\n\n$input")),
        )
}
