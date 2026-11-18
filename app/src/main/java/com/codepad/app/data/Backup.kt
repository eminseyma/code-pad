package com.codepad.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

/** Tüm verileri tek bir JSON'a çevirir ve JSON'dan geri yükler. */
class BackupManager(private val db: AppDatabase, private val settings: SettingsStore) {
    private val dao = db.dao()

    suspend fun export(includeKey: Boolean): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        root.put("notebooks", JSONArray().also { a ->
            dao.allNotebooksOnce().forEach {
                a.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("colorIndex", it.colorIndex)
                        .put("createdAt", it.createdAt).put("coverColor", it.coverColor).put("pageStyle", it.pageStyle),
                )
            }
        })
        root.put("notes", JSONArray().also { a ->
            dao.allNotes().forEach {
                a.put(
                    JSONObject().put("id", it.id).put("notebookId", it.notebookId).put("title", it.title)
                        .put("content", it.content).put("updatedAt", it.updatedAt),
                )
            }
        })
        root.put("cards", JSONArray().also { a ->
            dao.allCardsOnce().forEach {
                a.put(
                    JSONObject().put("id", it.id).put("noteId", it.noteId).put("section", it.section)
                        .put("title", it.title).put("body", it.body).put("createdAt", it.createdAt),
                )
            }
        })
        root.put("sessions", JSONArray().also { a ->
            dao.allSessionsOnce().forEach {
                a.put(JSONObject().put("id", it.id).put("title", it.title).put("createdAt", it.createdAt))
            }
        })
        root.put("messages", JSONArray().also { a ->
            dao.allMessagesOnce().forEach {
                a.put(
                    JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("role", it.role)
                        .put("text", it.text).put("createdAt", it.createdAt),
                )
            }
        })

        val st = JSONObject()
        st.put("model", settings.model)
        st.put("theme", settings.theme.name)
        st.put("actions", JSONArray().also { a ->
            settings.actions().forEach { a.put(JSONObject().put("id", it.id).put("label", it.label).put("instruction", it.instruction)) }
        })
        if (includeKey && settings.apiKey.isNotBlank()) st.put("apiKey", settings.apiKey)
        root.put("settings", st)
        return root.toString()
    }

    /** Bu telefondaki tüm veriyi yedekle değiştirir (tek işlemde; hata olursa hiçbir şey değişmez). */
    suspend fun restore(json: String) {
        val root = JSONObject(json)
        require(root.optInt("version", 0) >= 1) { "Geçersiz yedek dosyası." }

        val notebooks = root.getJSONArray("notebooks").objects().map {
            Notebook(
                id = it.getLong("id"), name = it.getString("name"), colorIndex = it.optInt("colorIndex", 0),
                createdAt = it.getLong("createdAt"), coverColor = it.optString("coverColor", "mint"),
                pageStyle = it.optString("pageStyle", "lined"),
            )
        }
        val notes = root.getJSONArray("notes").objects().map {
            Note(
                id = it.getLong("id"), notebookId = it.getLong("notebookId"), title = it.optString("title", ""),
                content = it.optString("content", ""), updatedAt = it.getLong("updatedAt"),
            )
        }
        val cards = root.getJSONArray("cards").objects().map {
            NoteCard(
                id = it.getLong("id"), noteId = it.getLong("noteId"), section = it.optString("section", ""),
                title = it.getString("title"), body = it.getString("body"), createdAt = it.getLong("createdAt"),
            )
        }
        val sessions = root.getJSONArray("sessions").objects().map {
            ChatSession(id = it.getLong("id"), title = it.getString("title"), createdAt = it.getLong("createdAt"))
        }
        val messages = root.getJSONArray("messages").objects().map {
            SavedMessage(
                id = it.getLong("id"), sessionId = it.getLong("sessionId"), role = it.getString("role"),
                text = it.getString("text"), createdAt = it.getLong("createdAt"),
            )
        }

        db.withTransaction {
            dao.clearNotebooks() // notlar ve kartlar da silinir (CASCADE)
            dao.clearSessions() // mesajlar da silinir (CASCADE)
            notebooks.forEach { dao.insertNotebook(it) }
            notes.forEach { dao.upsertNote(it) }
            cards.forEach { dao.insertCard(it) }
            sessions.forEach { dao.insertSession(it) }
            messages.forEach { dao.insertMessage(it) }
        }

        root.optJSONObject("settings")?.let { st ->
            st.optString("apiKey", "").takeIf { it.isNotBlank() }?.let { settings.apiKey = it }
            st.optString("model", "").takeIf { it.isNotBlank() }?.let { settings.model = it }
            runCatching { ThemeChoice.valueOf(st.optString("theme", "")) }.getOrNull()?.let { settings.theme = it }
            st.optJSONArray("actions")?.let { arr ->
                settings.saveActions(arr.objects().map { AiAction(it.getString("id"), it.getString("label"), it.getString("instruction")) })
            }
        }
    }
}
