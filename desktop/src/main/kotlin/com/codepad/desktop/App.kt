package com.codepad.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.desktop.sync.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Mint = Color(0xFF8CCBA9)
private val Forest = Color(0xFF1E4D39)
private val Green = Color(0xFF2F7D5B)
private val Paper = Color(0xFFF2F8F4)
private val Muted = Color(0xFF5E7568)

@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            background = Paper,
            surface = Color.White,
            onSurface = Forest,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            var loggedIn by remember { mutableStateOf(Store.hasSession) }
            if (loggedIn) {
                Workspace(onLogout = {
                    Store.clearSession()
                    loggedIn = false
                })
            } else {
                LoginView(onDone = {
                    loggedIn = true
                    Sync.runNow()
                })
            }
        }
    }
}

@Composable
fun LoginView(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Code Pad", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Forest)
        Text(
            if (create) "Yeni hesap oluştur" else "Telefondaki hesabınla giriş yap",
            fontSize = 14.sp,
            color = Muted,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-posta") },
            singleLine = true,
            modifier = Modifier.width(340.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Şifre (en az 6 karakter)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(340.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.width(340.dp))
            Spacer(Modifier.height(6.dp))
        }
        Button(
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        val s = withContext(Dispatchers.IO) {
                            if (create) SupabaseClient.signUp(email.trim(), pass)
                            else SupabaseClient.signIn(email.trim(), pass)
                        }
                        Store.setSession(s)
                        onDone()
                    } catch (e: Exception) {
                        error = e.message ?: "Giriş yapılamadı"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && email.isNotBlank() && pass.length >= 6,
            modifier = Modifier.width(340.dp),
        ) { Text(if (create) "Hesap oluştur" else "Giriş yap", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { create = !create }) {
            Text(if (create) "Zaten hesabım var" else "Hesabım yok, oluştur")
        }
    }
}

@Composable
fun Workspace(onLogout: () -> Unit) {
    var selNb by remember { mutableStateOf<String?>(null) }
    var selNote by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val nbs = Store.activeNotebooks()
    val currentNb = selNb?.takeIf { id -> nbs.any { it.id == id } } ?: nbs.firstOrNull()?.id

    Row(Modifier.fillMaxSize()) {
        // Sol: defterler
        Column(Modifier.width(220.dp).fillMaxHeight().background(Color.White).padding(12.dp)) {
            Text("Code Pad", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Forest)
            Text(Sync.status.ifBlank { "Henüz eşitlenmedi" }, fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { Sync.runNow() }) { Text("Eşitle") }
                OutlinedButton(onClick = onLogout) { Text("Çıkış") }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) { Text("Ayarlar") }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val nb = Store.addNotebook("Yeni defter")
                    selNb = nb.id
                    selNote = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Yeni defter") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(nbs) { nb ->
                    val active = nb.id == currentNb
                    Text(
                        nb.name,
                        color = Forest,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (active) Mint.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable {
                                selNb = nb.id
                                selNote = null
                            }
                            .padding(10.dp),
                    )
                }
            }
        }

        // Orta: notlar
        Column(Modifier.width(280.dp).fillMaxHeight().padding(12.dp)) {
            Button(
                onClick = { currentNb?.let { selNote = Store.addNote(it).id } },
                enabled = currentNb != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Yeni not") }
            Spacer(Modifier.height(8.dp))
            val notes = currentNb?.let { Store.notesIn(it) } ?: emptyList()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(notes) { n ->
                    val active = n.id == selNote
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable { selNote = n.id }
                            .padding(10.dp),
                    ) {
                        Text(n.title.ifBlank { "Başlıksız not" }, fontWeight = FontWeight.Bold, color = Forest, maxLines = 1)
                        Text(
                            n.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                            fontSize = 12.sp,
                            color = Muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Sağ: editör
        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            val selected = selNote?.let { id -> Store.notes.firstOrNull { it.id == id && !it.deleted } }
            if (selected == null) {
                Text("Bir not seç ya da yeni not oluştur.", color = Muted)
            } else {
                EditorPane(selected.id)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(Store.apiKey) }
    var model by remember { mutableStateOf(Store.model) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ayarlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Gemini API anahtarını Google AI Studio'dan al. Anahtar bu bilgisayarda kayıtlı kalır.",
                    fontSize = 12.sp,
                    color = Muted,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { Text("API anahtarı") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.width(420.dp),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    label = { Text("Model adı") },
                    modifier = Modifier.width(420.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.apiKey = key.trim()
                Store.model = model.trim().ifBlank { DefaultModel }
                Store.save()
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
}

/** Parantez/tırnak otomatik kapanır, kapanış karakteri üzerine yazılır, Enter girintiyi korur. */
fun smartEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length != old.text.length + 1) return new
    val cur = new.selection.start
    if (cur < 1 || cur > new.text.length || !new.selection.collapsed) return new
    val ch = new.text[cur - 1]
    val prev = new.text.getOrNull(cur - 2)
    val next = new.text.getOrNull(cur)
    val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')
    val closers = setOf(')', ']', '}', '"', '\'', '`')

    if (ch in closers && next == ch) {
        return TextFieldValue(new.text.removeRange(cur, cur + 1), TextRange(cur))
    }
    if (ch in pairs) {
        val quote = ch == '"' || ch == '\'' || ch == '`'
        if (quote && prev != null && prev.isLetterOrDigit()) return new
        val t = new.text.substring(0, cur) + pairs.getValue(ch) + new.text.substring(cur)
        return TextFieldValue(t, TextRange(cur))
    }
    if (ch == '\n') {
        val lineStart = new.text.lastIndexOf('\n', cur - 2) + 1
        val indent = new.text.substring(lineStart, cur - 1).takeWhile { it == ' ' || it == '\t' }
        if (indent.isEmpty()) return new
        val t = new.text.substring(0, cur) + indent + new.text.substring(cur)
        return TextFieldValue(t, TextRange(cur + indent.length))
    }
    return new
}
