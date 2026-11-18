package com.codepad.desktop

import org.json.JSONObject

/** Defter. deleted=true ise silinmiş sayılır (senkron için tutulur). */
data class Nb(
    val id: String,
    val name: String,
    val cover: String,
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

// Bulut (Supabase) ve yerel dosya aynı alan adlarını kullanır.
fun parseNb(o: JSONObject) = Nb(
    id = o.getString("id"),
    name = o.optString("name"),
    cover = o.optString("cover_color", "mint"),
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

fun nbJson(n: Nb): JSONObject = JSONObject()
    .put("id", n.id)
    .put("name", n.name)
    .put("color_index", 0)
    .put("created_at", n.createdAt)
    .put("cover_color", n.cover)
    .put("page_style", "lined")
    .put("updated_at", n.updatedAt)
    .put("deleted", n.deleted)

fun ntJson(n: Nt): JSONObject = JSONObject()
    .put("id", n.id)
    .put("notebook_id", n.nbId)
    .put("title", n.title)
    .put("content", n.content)
    .put("updated_at", n.updatedAt)
    .put("deleted", n.deleted)
