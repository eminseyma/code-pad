package com.codepad.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
    val cs = MaterialTheme.colorScheme
    var current by remember { mutableStateOf<String?>(Store.sessions.maxByOrNull { it.createdAt }?.id) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun ask(sid: String, text: String) {
        busy = true
        error = ""
        failed = null
        scope.launch {
            val r = Assistant.ask(sid, text)
            busy = false
            r.fold(
                onSuccess = { Store.addMessage(sid, "model", it) },
                onFailure = {
                    error = it.message ?: "Asistan yanıt vermedi."
                    failed = sid to text
                },
            )
        }
    }

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
        ask(sid, text)
    }

    val muted = cs.onSurfaceVariant

    Row(modifier) {
        Column(Modifier.width(240.dp).fillMaxHeight().padding(12.dp)) {
            PButton(onClick = {
                current = Store.newSession("").id
                error = ""
                failed = null
            }, modifier = Modifier.fillMaxWidth()) { Text("+ Yeni sohbet") }
            Spacer(Modifier.height(8.dp))
            val sessState = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state = sessState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(Store.sessions.sortedByDescending { it.createdAt }, key = { it.id }) { s ->
                        val src = remember { MutableInteractionSource() }
                        val hov by src.collectIsHoveredAsState()
                        val bg by animateColorAsState(
                            when {
                                s.id == current -> cs.primary.copy(alpha = 0.14f)
                                hov -> cs.primary.copy(alpha = 0.07f)
                                else -> Color.Transparent
                            },
                            tween(130),
                        )
                        Row(
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .hoverable(src)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(interactionSource = src, indication = null) {
                                    current = s.id
                                    error = ""
                                    failed = null
                                }
                                .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.title.ifBlank { "Yeni sohbet" }, Modifier.weight(1f), maxLines = 1, fontSize = 13.sp)
                            if (hov) {
                                IBtn(onClick = {
                                    Store.deleteSession(s.id)
                                    if (current == s.id) current = null
                                }, tooltip = "Sohbeti sil", danger = true) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Sil", modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Spacer(Modifier.height(42.dp))
                            }
                        }
                    }
                }
                ListScrollbar(sessState, Modifier.align(Alignment.CenterEnd))
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            val msgs = current
                ?.let { id -> Store.messages.filter { it.sessionId == id && !it.text.startsWith("Hata:") }.sortedBy { it.createdAt } }
                .orEmpty()
            Text("Asistan", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary)
            Text("Notlarına dayanarak soru sorabilirsin.", fontSize = 13.sp, color = muted)
            Spacer(Modifier.height(10.dp))

            val chatState = rememberLazyListState()
            LaunchedEffect(msgs.size, busy) {
                val total = msgs.size + if (busy) 1 else 0
                if (total > 0) chatState.animateScrollToItem(total - 1)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (msgs.isEmpty() && !busy) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState("Ne öğrenmek istersin?", "Notlarına bakarak yanıt veririm. Bir öneriyle başla:", symbol = "✦")
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            "Son notlarımdan kısa bir özet çıkar",
                            "Hangi konularda eksiğim var?",
                            "Notlarımdaki kodlardan bir quiz hazırla",
                        ).forEach { sug ->
                            SButton(onClick = { send(sug) }, compact = true, shape = RoundedCornerShape(50), modifier = Modifier.padding(vertical = 3.dp)) { Text(sug) }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(end = 8.dp), state = chatState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(msgs, key = { it.id }) { m ->
                            val mine = m.role == "user"
                            Row(Modifier.animateItem().fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                Column(
                                    Modifier
                                        .widthIn(max = 720.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (mine) cs.primary.copy(alpha = 0.16f) else cs.surface)
                                        .border(1.dp, if (mine) Color.Transparent else Color(0x14000000), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (mine) "Sen" else "Asistan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted, modifier = Modifier.weight(1f, fill = false))
                                        if (!mine) {
                                            Spacer(Modifier.width(8.dp))
                                            GButton(onClick = {
                                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(m.text), null)
                                            }) { Text("Kopyala", fontSize = 11.sp) }
                                        }
                                    }
                                    SelectionContainer { Text(m.text, fontSize = 14.sp, lineHeight = 21.sp) }
                                }
                            }
                        }
                        if (busy) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Düşünüyor…", color = muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (error.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.error.copy(alpha = 0.10f))
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(error, color = cs.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    failed?.let { f ->
                        Spacer(Modifier.width(8.dp))
                        SButton(onClick = { ask(f.first, f.second) }, enabled = !busy, compact = true, danger = true) { Text("Tekrar dene") }
                    }
                }
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
                PButton(onClick = { send(input) }, enabled = !busy && input.isNotBlank()) { Text("Gönder") }
            }
        }
    }
}
