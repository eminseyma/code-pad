package com.codepad.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Asistan: sohbet listesi solda, mesajlar ve giriş sağda. */
@Composable
fun AssistantPane(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<String?>(Store.sessions.maxByOrNull { it.createdAt }?.id) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun send(q: String) {
        val text = q.trim()
        if (text.isBlank() || busy) return
        if (Store.apiKey.isBlank()) {
            error = "Önce Ayarlar'dan Gemini anahtarını gir."
            return
        }
        val sid = current ?: Store.newSession(text).id.also { current = it }
        Store.addMessage(sid, "user", text)
        input = ""
        busy = true
        error = ""
        scope.launch {
            val r = Assistant.ask(sid, text)
            busy = false
            r.fold(
                onSuccess = { Store.addMessage(sid, "model", it) },
                onFailure = { Store.addMessage(sid, "model", "Hata: ${it.message}") },
            )
        }
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier) {
        Column(Modifier.width(240.dp).fillMaxHeight().padding(12.dp)) {
            Button(onClick = {
                current = Store.newSession("").id
                error = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("+ Yeni sohbet") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(Store.sessions.sortedByDescending { it.createdAt }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (s.id == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                current = s.id
                                error = ""
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.title, Modifier.weight(1f), maxLines = 1, fontSize = 13.sp)
                        TextButton(onClick = {
                            Store.deleteSession(s.id)
                            if (current == s.id) current = null
                        }) { Text("Sil", fontSize = 12.sp) }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            val msgs = current?.let { id -> Store.messages.filter { it.sessionId == id }.sortedBy { it.createdAt } }.orEmpty()
            Text("Asistan", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("Notlarına dayanarak soru sorabilirsin.", fontSize = 13.sp, color = muted)
            Spacer(Modifier.height(10.dp))
            if (msgs.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Son notlarımdan kısa bir özet çıkar",
                        "Hangi konularda eksiğim var?",
                        "Notlarımdaki kodlardan bir quiz hazırla",
                    ).forEach { s ->
                        OutlinedButton(onClick = { send(s) }) { Text(s, fontSize = 12.sp) }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(msgs) { m ->
                    val mine = m.role == "user"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                    ) {
                        Text(if (mine) "Sen" else "Asistan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted)
                        Text(m.text, fontSize = 14.sp)
                    }
                }
                if (busy) {
                    item { Text("Düşünüyor…", color = muted, fontSize = 13.sp) }
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Notlarına bir şey sor (Enter ile gönder)") },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { e: KeyEvent ->
                        if (e.key == Key.Enter && e.type == KeyEventType.KeyDown) {
                            send(input)
                            true
                        } else {
                            false
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { send(input) }, enabled = !busy && input.isNotBlank()) { Text("Gönder") }
            }
        }
    }
}
