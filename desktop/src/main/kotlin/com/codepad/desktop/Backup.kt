package com.codepad.desktop

import org.json.JSONArray
import org.json.JSONObject

/** Tüm verinin tek dosyada yedeği. Telefondaki "Yedek" ile aynı biçim. */
object Backup {
    fun export(includeKey: Boolean): String {
        val settings = JSONObject()
            .put("model", Store.model)
            .put("theme", Store.theme)
            .put("actions", JSONArray(Store.actions.map { JSONObject().put("id", it.id).put("label", it.label).put("instruction", it.instruction) }))
        if (includeKey) settings.put("apiKey", Store.apiKey)
        return JSONObject()
            .put("version", 1)
            .put("notebooks", JSONArray(Store.notebooks.map { nbJson(it) }))
            .put("notes", JSONArray(Store.notes.map { ntJson(it) }))
            .put("cards", JSONArray(Store.cards.map { cdJson(it) }))
            .put("sessions", JSONArray(Store.sessions.map { sessJson(it) }))
            .put("messages", JSONArray(Store.messages.map { msgJson(it) }))
            .put("settings", settings)
            .toString(2)
    }

    /** Yedeği yükler; bu bilgisayardaki veriler dosyadakiyle değiştirilir. */
    fun restore(text: String) {
        val o = JSONObject(text)
        Store.notebooks.clear()
        Store.notes.clear()
        Store.cards.clear()
        Store.sessions.clear()
        Store.messages.clear()
        o.optJSONArray("notebooks")?.let { a -> for (i in 0 until a.length()) Store.notebooks.add(parseNb(a.getJSONObject(i))) }
        o.optJSONArray("notes")?.let { a -> for (i in 0 until a.length()) Store.notes.add(parseNt(a.getJSONObject(i))) }
        o.optJSONArray("cards")?.let { a -> for (i in 0 until a.length()) Store.cards.add(parseCd(a.getJSONObject(i))) }
        o.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) Store.sessions.add(parseSess(a.getJSONObject(i))) }
        o.optJSONArray("messages")?.let { a -> for (i in 0 until a.length()) Store.messages.add(parseMsg(a.getJSONObject(i))) }
        o.optJSONObject("settings")?.let { s ->
            Store.model = s.optString("model").ifBlank { DefaultModel }
            Store.theme = s.optString("theme", "mint")
            if (s.has("apiKey")) Store.apiKey = s.optString("apiKey")
            s.optJSONArray("actions")?.let { a ->
                Store.actions.clear()
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    Store.actions.add(AiAction(x.getString("id"), x.getString("label"), x.getString("instruction")))
                }
            }
        }
        // Yedekteki kayıtlar bu cihazın kimliğiyle eşleşmez; sonraki eşitlemede yeniden gönderilir.
        Store.lastSync = 0L
        Store.save()
    }
}
