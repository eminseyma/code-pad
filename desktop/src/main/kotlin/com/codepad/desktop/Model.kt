package com.codepad.desktop

import org.json.JSONObject

/** Defter. Kapak ve sayfa stili telefondakiyle aynı değerlerdir. */
data class Nb(
    val id: String,
    val name: String,
    val cover: String,
    val page: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
)

/** Not. nbId: defter kimliği. */
data class Nt(
    val id: String,
    val nbId: String,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val deleted: Boolean,
)

/** Nottaki AI kartı. section boşsa "bölümsüz". */
data class Cd(
    val id: String,
    val noteId: String,
    val section: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
)

// Bulut (Supabase) ve yerel dosya aynı alan adlarını kullanır.
fun parseNb(o: JSONObject) = Nb(
    id = o.getString("id"),
    name = o.optString("name"),
    cover = o.optString("cover_color", "mint"),
    page = o.optString("page_style", "lined"),
    createdAt = o.optLong("created_at"),
    updatedAt = o.optLong("updated_at"),
    deleted = o.optBoolean("deleted"),
)

fun parseNt(o: JSONObject) = Nt(
    id = o.getString("id"),
    nbId = o.optString("notebook_id"),
    title = o.optString("title"),
    content = o.optString("content"),
    updatedAt = o.optLong("updated_at"),
    deleted = o.optBoolean("deleted"),
)

fun parseCd(o: JSONObject) = Cd(
    id = o.getString("id"),
    noteId = o.optString("note_id"),
    section = o.optString("section"),
    title = o.optString("title"),
    body = o.optString("body"),
    createdAt = o.optLong("created_at"),
    updatedAt = o.optLong("updated_at"),
    deleted = o.optBoolean("deleted"),
)

fun nbJson(n: Nb): JSONObject = JSONObject()
    .put("id", n.id)
    .put("name", n.name)
    .put("color_index", 0)
    .put("created_at", n.createdAt)
    .put("cover_color", n.cover)
    .put("page_style", n.page)
    .put("updated_at", n.updatedAt)
    .put("deleted", n.deleted)

fun ntJson(n: Nt): JSONObject = JSONObject()
    .put("id", n.id)
    .put("notebook_id", n.nbId)
    .put("title", n.title)
    .put("content", n.content)
    .put("updated_at", n.updatedAt)
    .put("deleted", n.deleted)

fun cdJson(c: Cd): JSONObject = JSONObject()
    .put("id", c.id)
    .put("note_id", c.noteId)
    .put("section", c.section)
    .put("title", c.title)
    .put("body", c.body)
    .put("created_at", c.createdAt)
    .put("updated_at", c.updatedAt)
    .put("deleted", c.deleted)
