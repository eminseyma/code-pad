package com.codepad.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.codepad.desktop.sync.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telefondaki senkronla aynı kurallar: önce çek, sonra gönder; en son değişiklik kazanır.
 * Not: Kartlar (AI kartları) bu sürümde henüz senkronize edilmiyor; telefondaki kartlar korunur.
 */
object Sync {
    var status by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private var job: Job? = null

    /** Değişiklikten kısa süre sonra eşitler; art arda değişiklikler tek eşitlemeye birleşir. */
    fun schedule() {
        if (!Store.hasSession) return
        job?.cancel()
        job = scope.launch {
            delay(2_500)
            runNow()
        }
    }

    fun runNow() {
        if (!Store.hasSession) return
        scope.launch {
            mutex.withLock {
                status = "Eşitleniyor…"
                status = try {
                    sync()
                    "Son eşitleme: " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                } catch (e: Exception) {
                    "Eşitlenemedi: " + (e.message ?: "bağlantıyı kontrol et")
                }
            }
        }
    }

    private suspend fun sync() {
        try {
            once()
        } catch (e: SupabaseClient.ApiException) {
            if (e.code != 401) throw e
            val s = withContext(Dispatchers.IO) { SupabaseClient.refresh(Store.refreshToken) }
            Store.setSession(s)
            once()
        }
    }

    private suspend fun once() {
        val token = Store.accessToken
        val since = Store.lastSync
        val start = System.currentTimeMillis()
        val localNb = Store.notebooks.filter { it.updatedAt > since }.toList()
        val localNt = Store.notes.filter { it.updatedAt > since }.toList()

        val pulled = withContext(Dispatchers.IO) {
            val remoteNb = SupabaseClient.select(token, "notebooks", since).map2 { parseNb(it) }
            val remoteNt = SupabaseClient.select(token, "notes", since).map2 { parseNt(it) }
            // Uzakta daha yeni olan bir kaydın üzerine eski yerel sürümü yazma.
            val newerNb = remoteNb.associateBy { it.id }
            val newerNt = remoteNt.associateBy { it.id }
            val pushNb = localNb.filter { (newerNb[it.id]?.updatedAt ?: -1L) <= it.updatedAt }
            val pushNt = localNt.filter { (newerNt[it.id]?.updatedAt ?: -1L) <= it.updatedAt }
            SupabaseClient.upsert(token, "notebooks", JSONArray(pushNb.map { nbJson(it) }))
            SupabaseClient.upsert(token, "notes", JSONArray(pushNt.map { ntJson(it) }))
            remoteNb to remoteNt
        }
        pulled.first.forEach { Store.mergeNb(it) }
        pulled.second.forEach { Store.mergeNt(it) }
        Store.lastSync = start
        Store.save()
    }
}

private fun <T> JSONArray.map2(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
