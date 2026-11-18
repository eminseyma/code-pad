package com.codepad.desktop

import androidx.compose.runtime.mutableStateListOf
import com.codepad.desktop.sync.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Yerel veri: %APPDATA%\CodePad içinde iki JSON dosyası. */
object Store {
    private val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "CodePad").apply { mkdirs() }
    private val dataFile = File(dir, "data.json")
    private val cfgFile = File(dir, "config.json")

    val notebooks = mutableStateListOf<Nb>()
    val notes = mutableStateListOf<Nt>()

    var accessToken = ""
    var refreshToken = ""
    var userId = ""
    var userEmail = ""
    var lastSync = 0L

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
        }
        runCatching {
            val d = JSONObject(dataFile.readText())
            val nbs = d.getJSONArray("notebooks")
            for (i in 0 until nbs.length()) notebooks.add(parseNb(nbs.getJSONObject(i)))
            val nts = d.getJSONArray("notes")
            for (i in 0 until nts.length()) notes.add(parseNt(nts.getJSONObject(i)))
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
                    .toString(),
            )
            dataFile.writeText(
                JSONObject()
                    .put("notebooks", JSONArray(notebooks.map { nbJson(it) }))
                    .put("notes", JSONArray(notes.map { ntJson(it) }))
                    .toString(),
            )
        }
    }

    fun setSession(s: SupabaseClient.Session) {
        // Farklı hesaba girildiyse tüm kayıtlar yeniden gönderilsin.
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

    fun activeNotebooks(): List<Nb> = notebooks.filter { !it.deleted }.sortedBy { it.createdAt }

    fun notesIn(nbId: String): List<Nt> =
        notes.filter { !it.deleted && it.nbId == nbId }.sortedByDescending { it.updatedAt }

    fun addNotebook(name: String): Nb {
        val now = System.currentTimeMillis()
        val nb = Nb(UUID.randomUUID().toString(), name, "mint", now, now, false)
        notebooks.add(nb)
        save()
        Sync.schedule()
        return nb
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
        val i = notes.indexOfFirst { it.id == id }
        if (i < 0) return
        notes[i] = notes[i].copy(deleted = true, updatedAt = System.currentTimeMillis())
        save()
        Sync.schedule()
    }

    fun deleteNotebook(id: String) {
        val now = System.currentTimeMillis()
        val i = notebooks.indexOfFirst { it.id == id }
        if (i >= 0) notebooks[i] = notebooks[i].copy(deleted = true, updatedAt = now)
        for (j in notes.indices) {
            if (notes[j].nbId == id && !notes[j].deleted) notes[j] = notes[j].copy(deleted = true, updatedAt = now)
        }
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
}
