package com.codepad.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.codepad.desktop.sync.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Yerel veri: %APPDATA%\CodePad içinde JSON dosyaları. */
object Store {
    private val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "CodePad").apply { mkdirs() }
    private val dataFile = File(dir, "data.json")
    private val cfgFile = File(dir, "config.json")

    val notebooks = mutableStateListOf<Nb>()
    val notes = mutableStateListOf<Nt>()
    val cards = mutableStateListOf<Cd>()

    var accessToken = ""
    var refreshToken = ""
    var userId = ""
    var userEmail = ""
    var lastSync = 0L
    var ownerId = ""
    var apiKey = ""
    var model = DefaultModel
    val actions = mutableStateListOf<AiAction>()
    var theme by mutableStateOf("mint")
    val sessions = mutableStateListOf<ChatSess>()
    val messages = mutableStateListOf<ChatMsg>()

    val hasSession: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()

    init {
        load()
    }

    private fun load() {
        runCatching {
            val c = JSONObject(cfgFile.readText())
            accessToken = c.optString("access")
            refreshToken = c.optString("refresh")
            userId = c.optString("user")
            userEmail = c.optString("email")
            lastSync = c.optLong("lastSync")
            ownerId = c.optString("owner")
            apiKey = c.optString("apiKey")
            theme = c.optString("theme", "mint")
            model = c.optString("model").ifBlank { DefaultModel }
            c.optJSONArray("actions")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    actions.add(AiAction(o.getString("id"), o.getString("label"), o.getString("instruction")))
                }
            }
        }
        if (actions.isEmpty()) actions.addAll(DefaultActions)
        runCatching {
            val d = JSONObject(dataFile.readText())
            d.optJSONArray("notebooks")?.let { a -> for (i in 0 until a.length()) notebooks.add(parseNb(a.getJSONObject(i))) }
            d.optJSONArray("notes")?.let { a -> for (i in 0 until a.length()) notes.add(parseNt(a.getJSONObject(i))) }
            d.optJSONArray("cards")?.let { a -> for (i in 0 until a.length()) cards.add(parseCd(a.getJSONObject(i))) }
            d.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) sessions.add(parseSess(a.getJSONObject(i))) }
            d.optJSONArray("messages")?.let { a -> for (i in 0 until a.length()) messages.add(parseMsg(a.getJSONObject(i))) }
        }
    }

    fun save() {
        runCatching {
            cfgFile.writeText(
                JSONObject()
                    .put("access", accessToken)
                    .put("refresh", refreshToken)
                    .put("user", userId)
                    .put("email", userEmail)
                    .put("lastSync", lastSync)
                    .put("owner", ownerId)
                    .put("apiKey", apiKey)
                    .put("model", model)
                    .put("theme", theme)
                    .put("actions", JSONArray(actions.map { JSONObject().put("id", it.id).put("label", it.label).put("instruction", it.instruction) }))
                    .toString(),
            )
            dataFile.writeText(
                JSONObject()
                    .put("notebooks", JSONArray(notebooks.map { nbJson(it) }))
                    .put("notes", JSONArray(notes.map { ntJson(it) }))
                    .put("cards", JSONArray(cards.map { cdJson(it) }))
                    .put("sessions", JSONArray(sessions.map { sessJson(it) }))
                    .put("messages", JSONArray(messages.map { msgJson(it) }))
                    .toString(),
            )
        }
    }

    fun setSession(s: SupabaseClient.Session) {
        // Başka hesaba girildiyse yerel kayıtlara yeni kimlik ver; eski hesabın verisiyle çakışmasın.
        if (ownerId.isNotBlank() && ownerId != s.userId) renewIds()
        ownerId = s.userId
        if (s.userId != userId) lastSync = 0L
        accessToken = s.accessToken
        refreshToken = s.refreshToken
        userId = s.userId
        userEmail = s.email
        save()
    }

    fun clearSession() {
        accessToken = ""
        refreshToken = ""
        userId = ""
        userEmail = ""
        lastSync = 0L
        save()
    }

    /** Tüm yerel kayıtlara yeni kimlik verir ve ilişkileri (defter/not) yeni kimliklere çevirir. */
    private fun renewIds() {
        val nbMap = notebooks.associate { it.id to UUID.randomUUID().toString() }
        val noteMap = notes.associate { it.id to UUID.randomUUID().toString() }
        val nbNew = notebooks.map { it.copy(id = nbMap.getValue(it.id)) }
        val ntNew = notes.map { it.copy(id = noteMap.getValue(it.id), nbId = nbMap[it.nbId] ?: it.nbId) }
        val cdNew = cards.mapNotNull { c ->
            noteMap[c.noteId]?.let { c.copy(id = UUID.randomUUID().toString(), noteId = it) }
        }
        notebooks.clear(); notebooks.addAll(nbNew)
        notes.clear(); notes.addAll(ntNew)
        cards.clear(); cards.addAll(cdNew)
        lastSync = 0L
    }

    fun newSession(title: String): ChatSess {
        val s = ChatSess(UUID.randomUUID().toString(), title.ifBlank { "Yeni sohbet" }.take(40), System.currentTimeMillis())
        sessions.add(s)
        save()
        return s
    }

    fun deleteSession(id: String) {
        sessions.removeAll { it.id == id }
        messages.removeAll { it.sessionId == id }
        save()
    }

    fun addMessage(sessionId: String, role: String, text: String) {
        messages.add(ChatMsg(UUID.randomUUID().toString(), sessionId, role, text, System.currentTimeMillis()))
        save()
    }

    fun activeNotebooks(): List<Nb> = notebooks.filter { !it.deleted }.sortedBy { it.createdAt }

    fun notesIn(nbId: String): List<Nt> =
        notes.filter { !it.deleted && it.nbId == nbId }.sortedByDescending { it.updatedAt }

    fun cardsOf(noteId: String): List<Cd> =
        cards.filter { !it.deleted && it.noteId == noteId }.sortedBy { it.createdAt }

    fun addNotebook(name: String): Nb {
        val now = System.currentTimeMillis()
        val nb = Nb(UUID.randomUUID().toString(), name, "mint", "lined", now, now, false)
        notebooks.add(nb)
        save()
        Sync.schedule()
        return nb
    }

    fun setNotebookStyle(id: String, cover: String, page: String) {
        val i = notebooks.indexOfFirst { it.id == id }
        if (i < 0) return
        notebooks[i] = notebooks[i].copy(cover = cover, page = page, updatedAt = System.currentTimeMillis())
        save()
        Sync.schedule()
    }

    fun addNote(nbId: String): Nt {
        val now = System.currentTimeMillis()
        val n = Nt(UUID.randomUUID().toString(), nbId, "", "", now, false)
        notes.add(n)
        save()
        Sync.schedule()
        return n
    }

    fun updateNote(id: String, title: String, content: String) {
        val i = notes.indexOfFirst { it.id == id }
        if (i < 0) return
        notes[i] = notes[i].copy(title = title, content = content, updatedAt = System.currentTimeMillis())
        save()
        Sync.schedule()
    }

    fun deleteNote(id: String) {
        val now = System.currentTimeMillis()
        val i = notes.indexOfFirst { it.id == id }
        if (i >= 0) notes[i] = notes[i].copy(deleted = true, updatedAt = now)
        for (j in cards.indices) {
            if (cards[j].noteId == id && !cards[j].deleted) cards[j] = cards[j].copy(deleted = true, updatedAt = now)
        }
        save()
        Sync.schedule()
    }

    fun deleteNotebook(id: String) {
        val now = System.currentTimeMillis()
        val i = notebooks.indexOfFirst { it.id == id }
        if (i >= 0) notebooks[i] = notebooks[i].copy(deleted = true, updatedAt = now)
        val noteIds = notes.filter { it.nbId == id && !it.deleted }.map { it.id }.toSet()
        for (j in notes.indices) {
            if (notes[j].id in noteIds) notes[j] = notes[j].copy(deleted = true, updatedAt = now)
        }
        for (j in cards.indices) {
            if (cards[j].noteId in noteIds && !cards[j].deleted) cards[j] = cards[j].copy(deleted = true, updatedAt = now)
        }
        save()
        Sync.schedule()
    }

    /** Nota AI kartı ekler. */
    fun addCard(noteId: String, title: String, body: String, section: String = "") {
        val now = System.currentTimeMillis()
        cards.add(Cd(UUID.randomUUID().toString(), noteId, section.trim(), title, body, now, now, false))
        save()
        Sync.schedule()
    }

    fun moveCard(id: String, section: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0) return
        cards[i] = cards[i].copy(section = section.trim(), updatedAt = System.currentTimeMillis())
        save()
        Sync.schedule()
    }

    fun deleteCard(id: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0) return
        cards[i] = cards[i].copy(deleted = true, updatedAt = System.currentTimeMillis())
        save()
        Sync.schedule()
    }

    /** Uzaktan gelen kaydı yerele işler; en son değiştirilen kazanır. */
    fun mergeNb(r: Nb) {
        val i = notebooks.indexOfFirst { it.id == r.id }
        if (i < 0) notebooks.add(r) else if (r.updatedAt > notebooks[i].updatedAt) notebooks[i] = r
    }

    fun mergeNt(r: Nt) {
        val i = notes.indexOfFirst { it.id == r.id }
        if (i < 0) notes.add(r) else if (r.updatedAt > notes[i].updatedAt) notes[i] = r
    }

    fun mergeCd(r: Cd) {
        val i = cards.indexOfFirst { it.id == r.id }
        if (i < 0) cards.add(r) else if (r.updatedAt > cards[i].updatedAt) cards[i] = r
    }
}
