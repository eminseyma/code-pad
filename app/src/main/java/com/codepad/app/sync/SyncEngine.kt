package com.codepad.app.sync

import com.codepad.app.data.Note
import com.codepad.app.data.NoteCard
import com.codepad.app.data.NoteDao
import com.codepad.app.data.Notebook
import com.codepad.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Çakışma kuralı: en son değiştirilen kayıt kazanır (updated_at).
 * Sıra: önce uzaktaki yeni değişiklikler çekilir, sonra yerel değişiklikler gönderilir,
 * en son silinenler bildirilir.
 */
object SyncEngine {

    suspend fun sync(dao: NoteDao, settings: SettingsStore) = withContext(Dispatchers.IO) {
        try {
            syncOnce(dao, settings)
        } catch (e: SupabaseClient.ApiException) {
            if (e.code != 401) throw e
            // Erişim jetonu süresi dolmuş: yenile ve bir kez daha dene.
            settings.saveSession(SupabaseClient.refresh(settings.refreshToken))
            syncOnce(dao, settings)
        }
    }

    private suspend fun syncOnce(dao: NoteDao, settings: SettingsStore) {
        val token = settings.accessToken
        val since = settings.lastSync
        val startedAt = System.currentTimeMillis()

        pull(dao, token, since)
        push(dao, token, since)
        pushTombstones(dao, token)

        settings.lastSync = startedAt
    }

    // ---------- Çekme ----------

    private suspend fun pull(dao: NoteDao, token: String, since: Long) {
        // Defterler
        SupabaseClient.select(token, "notebooks", since).forEachObject { o ->
            val sid = o.getString("id")
            val local = dao.allNotebooksOnce().firstOrNull { it.syncId == sid }
            if (o.optBoolean("deleted")) {
                if (local != null) dao.deleteNotebookBySync(sid)
                return@forEachObject
            }
            val remoteTime = o.optLong("updated_at")
            if (local != null && local.updatedAt >= remoteTime) return@forEachObject
            dao.upsertNotebook(
                Notebook(
                    id = local?.id ?: 0,
                    name = o.optString("name"),
                    colorIndex = o.optInt("color_index"),
                    createdAt = o.optLong("created_at"),
                    coverColor = o.optString("cover_color", "mint"),
                    pageStyle = o.optString("page_style", "lined"),
                    syncId = sid,
                    updatedAt = remoteTime,
                ),
            )
        }

        // Notlar (defter kimliği yerel id'ye çevrilir)
        SupabaseClient.select(token, "notes", since).forEachObject { o ->
            val sid = o.getString("id")
            val local = dao.allNotesOnce().firstOrNull { it.syncId == sid }
            if (o.optBoolean("deleted")) {
                if (local != null) dao.deleteNoteBySync(sid)
                return@forEachObject
            }
            val remoteTime = o.optLong("updated_at")
            if (local != null && local.updatedAt >= remoteTime) return@forEachObject
            val nbId = dao.allNotebooksOnce().firstOrNull { it.syncId == o.optString("notebook_id") }?.id ?: return@forEachObject
            dao.upsertNote(
                Note(
                    id = local?.id ?: 0,
                    notebookId = nbId,
                    title = o.optString("title"),
                    content = o.optString("content"),
                    updatedAt = remoteTime,
                    syncId = sid,
                ),
            )
        }

        // Kartlar
        SupabaseClient.select(token, "note_cards", since).forEachObject { o ->
            val sid = o.getString("id")
            val local = dao.allCardsOnce().firstOrNull { it.syncId == sid }
            if (o.optBoolean("deleted")) {
                if (local != null) dao.deleteCardBySync(sid)
                return@forEachObject
            }
            val remoteTime = o.optLong("updated_at")
            if (local != null && local.updatedAt >= remoteTime) return@forEachObject
            val noteId = dao.allNotesOnce().firstOrNull { it.syncId == o.optString("note_id") }?.id ?: return@forEachObject
            dao.upsertCard(
                NoteCard(
                    id = local?.id ?: 0,
                    noteId = noteId,
                    section = o.optString("section"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    createdAt = o.optLong("created_at"),
                    updatedAt = remoteTime,
                    syncId = sid,
                ),
            )
        }
    }

    // ---------- Gönderme ----------

    private suspend fun push(dao: NoteDao, token: String, since: Long) {
        val nbs = dao.allNotebooksOnce()
        val nbSync = nbs.associate { it.id to it.syncId }
        val changedNb = nbs.filter { it.updatedAt > since }
        SupabaseClient.upsert(token, "notebooks", JSONArray().also { arr ->
            changedNb.forEach { nb ->
                arr.put(
                    JSONObject()
                        .put("id", nb.syncId)
                        .put("name", nb.name)
                        .put("color_index", nb.colorIndex)
                        .put("created_at", nb.createdAt)
                        .put("cover_color", nb.coverColor)
                        .put("page_style", nb.pageStyle)
                        .put("updated_at", nb.updatedAt)
                        .put("deleted", false),
                )
            }
        })

        val notes = dao.allNotesOnce()
        val noteSync = notes.associate { it.id to it.syncId }
        val changedNotes = notes.filter { it.updatedAt > since && nbSync.containsKey(it.notebookId) }
        SupabaseClient.upsert(token, "notes", JSONArray().also { arr ->
            changedNotes.forEach { n ->
                arr.put(
                    JSONObject()
                        .put("id", n.syncId)
                        .put("notebook_id", nbSync[n.notebookId])
                        .put("title", n.title)
                        .put("content", n.content)
                        .put("updated_at", n.updatedAt)
                        .put("deleted", false),
                )
            }
        })

        val cards = dao.allCardsOnce()
        val changedCards = cards.filter { it.updatedAt > since && noteSync.containsKey(it.noteId) }
        SupabaseClient.upsert(token, "note_cards", JSONArray().also { arr ->
            changedCards.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("id", c.syncId)
                        .put("note_id", noteSync[c.noteId])
                        .put("section", c.section)
                        .put("title", c.title)
                        .put("body", c.body)
                        .put("created_at", c.createdAt)
                        .put("updated_at", c.updatedAt)
                        .put("deleted", false),
                )
            }
        })
    }

    private suspend fun pushTombstones(dao: NoteDao, token: String) {
        val stones = dao.tombstones()
        if (stones.isEmpty()) return
        // Uzak tabloda NOT NULL alanlar için boş değer gönderiyoruz; satır zaten silinmiş işaretlenecek.
        val byKind = stones.groupBy { it.kind }
        byKind["notebook"]?.let { list ->
            SupabaseClient.upsert(token, "notebooks", JSONArray().also { arr ->
                list.forEach { arr.put(JSONObject().put("id", it.syncId).put("name", "").put("updated_at", it.deletedAt).put("deleted", true)) }
            })
        }
        byKind["note"]?.let { list ->
            SupabaseClient.upsert(token, "notes", JSONArray().also { arr ->
                list.forEach { arr.put(JSONObject().put("id", it.syncId).put("notebook_id", "").put("updated_at", it.deletedAt).put("deleted", true)) }
            })
        }
        byKind["card"]?.let { list ->
            SupabaseClient.upsert(token, "note_cards", JSONArray().also { arr ->
                list.forEach { arr.put(JSONObject().put("id", it.syncId).put("note_id", "").put("updated_at", it.deletedAt).put("deleted", true)) }
            })
        }
        dao.clearTombstones(stones.map { it.syncId })
    }
}

private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
    for (i in 0 until length()) action(getJSONObject(i))
}
