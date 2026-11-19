package com.codepad.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private val Forest = Color(0xFF1E4D39)
private val Green = Color(0xFF2F7D5B)
private val Muted = Color(0xFF5E7568)

/** AI sonucu: etiket, metin ve çalıştırıldığı seçim aralığı. */
private data class AiResult(val label: String, val text: String, val selStart: Int, val selEnd: Int, val hadSelection: Boolean)

@Composable
fun EditorPane(noteId: String) {
    val note = Store.notes.firstOrNull { it.id == noteId && !it.deleted } ?: return
    val scope = rememberCoroutineScope()

    var title by remember(noteId) { mutableStateOf(note.title) }
    var body by remember(noteId) { mutableStateOf(TextFieldValue(note.content)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AiResult?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    // Kart ekleme / bölüm değiştirme
    var pendingCardText by remember { mutableStateOf<String?>(null) }
    var movingCardId by remember { mutableStateOf<String?>(null) }
    var sectionText by remember { mutableStateOf("") }

    fun saveBody(v: TextFieldValue) {
        body = v
        Store.updateNote(note.id, title, v.text)
    }

    fun runAi(label: String, instruction: String) {
        if (Store.apiKey.isBlank()) {
            error = "Önce Ayarlar'dan Gemini anahtarını gir."
            return
        }
        val sel = body.selection
        val selected = if (sel.collapsed) "" else body.text.substring(sel.min, sel.max)
        val input = selected.ifBlank { body.text }
        if (input.isBlank()) {
            error = "Önce biraz metin yaz."
            return
        }
        busy = true
        error = ""
        scope.launch {
            val r = Ai.run(instruction, title, input)
            busy = false
            r.fold(
                onSuccess = { result = AiResult(label, it, sel.min, sel.max, selected.isNotEmpty()) },
                onFailure = { error = it.message ?: "AI yanıt vermedi" },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Başlık + paylaş
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    Store.updateNote(note.id, it, body.text)
                },
                singleLine = true,
                label = { Text("Başlık") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val text = (if (title.isBlank()) "Not" else title) + "\n\n" + body.text
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            }) { Text("Paylaş (kopyala)") }
            TextButton(onClick = {
                Store.deleteNote(note.id)
            }) { Text("Sil") }
        }

        // AI eylemleri
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Store.actions.forEach { a ->
                OutlinedButton(onClick = { runAi(a.label, a.instruction) }, enabled = !busy) { Text(a.label, fontSize = 13.sp) }
            }
            OutlinedButton(onClick = { showCustom = true }, enabled = !busy) { Text("Özel istek", fontSize = 13.sp) }
            if (busy) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        // Metin
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = body,
            onValueChange = { typed -> saveBody(smartEdit(body, typed)) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind { drawPageStyle(Store.notebooks.firstOrNull { it.id == note.nbId }?.page ?: "lined", Color(0x33808F85)) }
                .padding(12.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )

        // Kartlar (bölümlere göre)
        Spacer(Modifier.height(10.dp))
        CardsPanel(
            noteId = note.id,
            onAppend = { text -> saveBody(TextFieldValue(body.text.trimEnd() + "\n\n" + text + "\n")) },
            onMove = { id, current ->
                movingCardId = id
                sectionText = current
            },
        )
    }

    // AI sonucu
    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(r.label) },
            text = {
                LazyColumn(Modifier.width(520.dp).height(320.dp)) {
                    item { Text(r.text, fontSize = 14.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    saveBody(TextFieldValue(body.text.trimEnd() + "\n\n" + r.text + "\n"))
                    result = null
                }) { Text("Nota ekle") }
            },
            dismissButton = {
                Row {
                    if (r.hadSelection) {
                        TextButton(onClick = {
                            val t = body.text
                            val merged = t.substring(0, r.selStart) + r.text + t.substring(r.selEnd)
                            saveBody(TextFieldValue(merged, TextRange(r.selStart + r.text.length)))
                            result = null
                        }) { Text("Seçimin yerine koy") }
                    }
                    TextButton(onClick = {
                        pendingCardText = r.text
                        sectionText = ""
                        result = null
                    }) { Text("Karta ekle") }
                    TextButton(onClick = { result = null }) { Text("Kapat") }
                }
            },
        )
    }

    // Özel istek
    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Özel istek") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    minLines = 3,
                    label = { Text("Ne yapmamı istersin?") },
                    modifier = Modifier.width(480.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = customText.isNotBlank(),
                    onClick = {
                        val instr = customText.trim()
                        showCustom = false
                        runAi("Özel istek", instr)
                    },
                ) { Text("Çalıştır") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Vazgeç") } },
        )
    }

    // Karta ekleme (AI sonucundan)
    pendingCardText?.let { text ->
        SectionDialog(
            title = "Karta ekle",
            initial = "",
            confirmLabel = "Ekle",
            onDismiss = { pendingCardText = null },
            onConfirm = { section ->
                Store.addCard(note.id, title.ifBlank { "AI sonucu" }, text, section)
                pendingCardText = null
            },
        )
    }

    // Bölüm değiştirme
    movingCardId?.let { id ->
        SectionDialog(
            title = "Bölüm",
            initial = sectionText,
            confirmLabel = "Kaydet",
            onDismiss = { movingCardId = null },
            onConfirm = { section ->
                Store.moveCard(id, section)
                movingCardId = null
            },
        )
    }
}

@Composable
private fun CardsPanel(noteId: String, onAppend: (String) -> Unit, onMove: (String, String) -> Unit) {
    val cards = Store.cardsOf(noteId)
    if (cards.isEmpty()) return
    val grouped = cards.groupBy { it.section.ifBlank { "Bölümsüz" } }
        .toList()
        .sortedWith(compareBy({ it.first == "Bölümsüz" }, { it.first }))

    Column(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surface).padding(10.dp)) {
        Text("Kartlar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grouped.forEach { (section, list) ->
                item { Text(section, color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                items(list) { c ->
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp)) {
                        Text(c.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(c.body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onAppend(c.body) }) { Text("Nota ekle", fontSize = 12.sp) }
                            TextButton(onClick = { onMove(c.id, c.section) }) { Text("Bölüm", fontSize = 12.sp) }
                            TextButton(onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(c.body), null)
                            }) { Text("Kopyala", fontSize = 12.sp) }
                            TextButton(onClick = { Store.deleteCard(c.id) }) { Text("Sil", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionDialog(title: String, initial: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var section by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = section,
                onValueChange = { section = it },
                singleLine = true,
                label = { Text("Bölüm adı (boş bırakabilirsin)") },
                modifier = Modifier.width(360.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(section.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}


/** Sayfa stili: çizgili, noktalı, kareli ya da boş. */
fun DrawScope.drawPageStyle(style: String, color: Color) {
    val step = 28.dp.toPx()
    when (style) {
        "lined" -> {
            var y = step
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        "dotted" -> {
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) {
                    drawCircle(color, radius = 1.6f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        "grid" -> {
            var y = step
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            var x = step
            while (x < size.width) {
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
        }
    }
}
