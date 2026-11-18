package com.codepad.app.drive

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive'ın uygulamaya özel gizli klasörü (appDataFolder) ile çalışır.
 * Bu klasör Drive arayüzünde görünmez ve yalnızca bu uygulama okuyabilir.
 */
object DriveApi {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val FILE_NAME = "codepad-backup.json"

    /**
     * Yetkilendirir. İzin daha önce verildiyse sessizce erişim jetonu döner;
     * verilmediyse hasResolution() true olur ve kullanıcıya bir kez giriş ekranı gösterilir.
     */
    suspend fun authorize(context: Context): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).await()
    }

    private suspend fun request(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
            }
        }
        try {
            body?.let { data -> conn.outputStream.use { it.write(data) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrDefault(text.take(200))
                error("Drive hatası ($code): $msg")
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun listBackups(token: String): List<String> {
        val q = URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q" +
            "&fields=files(id)&orderBy=modifiedTime%20desc&pageSize=20"
        val files = JSONObject(request("GET", url, token)).getJSONArray("files")
        return List(files.length()) { files.getJSONObject(it).getString("id") }
    }

    suspend fun hasBackup(token: String): Boolean = listBackups(token).isNotEmpty()

    /** Yeni yedeği yükler; başarılı olunca eskilerini siler (yükleme başarısızsa eski yedek korunur). */
    suspend fun upload(token: String, json: String) {
        val old = listBackups(token)
        val boundary = "codepad_" + System.currentTimeMillis()
        val meta = JSONObject().put("name", FILE_NAME).put("parents", JSONArray().put("appDataFolder")).toString()
        val body = (
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n" +
                "--$boundary--"
            ).toByteArray(Charsets.UTF_8)
        request(
            "POST",
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
            token,
            body,
            "multipart/related; boundary=$boundary",
        )
        old.forEach { id ->
            runCatching { request("DELETE", "https://www.googleapis.com/drive/v3/files/$id", token) }
        }
    }

    /** En son yedeği indirir; yedek yoksa null. */
    suspend fun download(token: String): String? {
        val id = listBackups(token).firstOrNull() ?: return null
        return request("GET", "https://www.googleapis.com/drive/v3/files/$id?alt=media", token)
    }
}
