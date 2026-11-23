package com.codepad.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileDownload
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codepad.app.AiResult
import com.codepad.app.AiState
import com.codepad.app.AppViewModel
import com.codepad.app.data.AiAction
import com.codepad.app.data.Note
import com.codepad.app.data.NoteCard
import kotlinx.coroutines.delay

/** İmleç/seçim yerine metin koyar. cursorBack > 0 ise imleci geriye alır. */
/** Editörde akıllı yazım: parantez/tırnak otomatik kapanır, kapanış karakteri üzerine yazılır, Enter girintiyi korur. */
private fun smartEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    pairDelete(old, new)?.let { return it }
    if (new.text.length != old.text.length + 1) return new
    val cur = new.selection.start
    if (cur < 1 || cur > new.text.length || !new.selection.collapsed) return new
    val ch = new.text[cur - 1]
    val prev = new.text.getOrNull(cur - 2)
    val next = new.text.getOrNull(cur)
    val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')
    val closers = setOf(')', ']', '}', '"', '\'', '`')

    // Kapanış karakterini yazınca, zaten oradaysa üzerine yaz (çift yazma olmasın)
    if (ch in closers && next == ch) {
        val t = new.text.removeRange(cur, cur + 1)
        return TextFieldValue(t, TextRange(cur))
    }

    // Açılış karakteri: karşılığını ekle
    if (ch in pairs) {
        val quote = ch == '"' || ch == '\'' || ch == '`'
        // Kelimenin ortasındaki tırnak (örn. don't) için otomatik kapatma yapma
        if (quote && prev != null && prev.isLetterOrDigit()) return new
        val t = new.text.substring(0, cur) + pairs.getValue(ch) + new.text.substring(cur)
        return TextFieldValue(t, TextRange(cur))
    }

    // Enter: önceki satırın girintisini koru
    if (ch == '\n') {
        val lineStart = new.text.lastIndexOf('\n', cur - 2) + 1
        val indent = new.text.substring(lineStart, cur - 1).takeWhile { it == ' ' || it == '\t' }
        if (indent.isEmpty()) return new
        val t = new.text.substring(0, cur) + indent + new.text.substring(cur)
        return TextFieldValue(t, TextRange(cur + indent.length))
    }
    return new
}

private fun TextFieldValue.replaceSel(s: String, cursorBack: Int = 0): TextFieldValue {
    val a = selection.min
    val b = selection.max
    val newText = text.replaceRange(a, b, s)
    return TextFieldValue(newText, TextRange(a + s.length - cursorBack))
}

@Composable
fun EditorScreen(vm: AppViewModel, noteId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cards by remember(noteId) { vm.cardsFor(noteId) }.collectAsStateWithLifecycle(emptyList())

    var note by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var loaded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var movingCard by remember { mutableStateOf<NoteCard?>(null) }
    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val pageStyle = notebooks.firstOrNull { it.id == note?.notebookId }?.pageStyle ?: "lined"
    val lineColor = MaterialTheme.colorScheme.outline

    LaunchedEffect(noteId) {
        vm.loadNote(noteId)?.let {
            note = it
            title = it.title
            content = TextFieldValue(it.content)
        }
        loaded = true
    }

    // Yazmayı bıraktıktan 0,6 sn sonra otomatik kaydet.
    LaunchedEffect(title, content.text) {
        val n = note
        if (loaded && n != null && (n.title != title || n.content != content.text)) {
            delay(600)
            val updated = n.copy(title = title, content = content.text)
            vm.saveNote(updated)
            note = updated
        }
    }

    fun leave() {
        val n = note
        if (n != null) {
            if (title.isBlank() && content.text.isBlank()) vm.deleteNote(n)
            else vm.saveNote(n.copy(title = title, content = content.text))
        }
        onBack()
    }
    BackHandler(onBack = ::leave)

    // Seçim varsa yalnızca seçili metne, yoksa tüm nota uygulanır.
    val selection = content.selection
    val selectedText = if (selection.collapsed) "" else content.text.substring(selection.min, selection.max)
    val inputText = selectedText.ifBlank { content.text }
    val excerpt = inputText.trim().lineSequence().firstOrNull().orEmpty().take(40)

    fun runAction(action: AiAction) = vm.runAi(action.label, action.instruction, inputText, excerpt)

    fun edit(s: String, back: Int = 0) {
        content = content.replaceSel(s, back)
    }

    fun appendToNote(s: String) {
        content = TextFieldValue(content.text.trimEnd() + "\n\n" + s + "\n")
    }

    fun markdownText(): String = buildString {
        append("# ").append(title.ifBlank { "Not" }).append("\n\n").append(content.text)
        cards.groupBy { it.section }.forEach { (section, list) ->
            append("\n\n")
            if (section.isNotBlank()) append("## ").append(section).append("\n")
            list.forEach { append("\n### ").append(it.title).append("\n").append(it.body).append("\n") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(markdownText().toByteArray(Charsets.UTF_8)) } }
        }
    }

    fun share() {
        val body = buildString {
            append(title.ifBlank { "Not" }).append("\n\n").append(content.text)
            cards.groupBy { it.section }.forEach { (section, list) ->
                append("\n\n")
                if (section.isNotBlank()) append("## ").append(section).append("\n")
                list.forEach { append("\n### ").append(it.title).append("\n").append(it.body).append("\n") }
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Notu paylaş"))
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { share() }) { Icon(Icons.Outlined.Share, contentDescription = "Paylaş") }
            IconButton(onClick = {
                val safe = title.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().ifBlank { "not" }
                exportLauncher.launch("$safe.md")
            }) { Icon(Icons.Outlined.FileDownload, contentDescription = "Markdown olarak kaydet") }
            IconButton(onClick = { preview = !preview }) {
                Icon(
                    if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                    contentDescription = if (preview) "Düzenle" else "Önizle",
                )
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Notu sil") }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            val transparent = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            )
            Column(Modifier.fillMaxWidth().drawBehind { drawPage(pageStyle, lineColor) }) {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Başlık", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                colors = transparent,
            )
            if (preview) {
                NoteBody(content.text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } else {
                TextField(
                    value = content,
                    onValueChange = { content = smartEdit(content, it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    placeholder = { Text("Yaz… Kod için ``` ile blok aç ya da alttaki </> düğmesine bas.") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    colors = transparent,
                )
            }
            val wordCount = content.text.split(Regex("\\s+")).count { it.isNotBlank() }
            val lineCount = if (content.text.isEmpty()) 0 else content.text.count { it == '\n' } + 1
            Text(
                "$wordCount kelime · $lineCount satır",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )

            }

            if (cards.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Kartlar",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val groups = cards.groupBy { it.section }
                        .toSortedMap(compareBy<String>({ it.isEmpty() }, { it }))
                    groups.forEach { (section, list) ->
                        Text(
                            if (section.isBlank()) "Bölümsüz" else section,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        list.forEach { card ->
                            CardItem(
                                card = card,
                                onAppend = { appendToNote(card.body) },
                                onMove = { movingCard = card },
                                onCopy = { clipboard.setText(AnnotatedString(card.body)) },
                                onDelete = { vm.deleteCard(card) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // AI eylemleri: seçim varsa seçili metne uygulanır.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                if (selectedText.isNotBlank()) "Seçili metne uygulanır" else "Tüm nota uygulanır",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.actions.forEach { action ->
                    AssistChip(
                        onClick = { runAction(action) },
                        label = { Text(action.label, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.primary,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.secondary),
                    )
                }
                AssistChip(
                    onClick = { showCustom = true },
                    label = { Text("Özel istek", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(14.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ToolButton(Icons.Outlined.Title, "Başlık") { edit("## ") }
            ToolButton(Icons.Outlined.FormatBold, "Kalın") { edit("****", 2) }
            ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Liste") { edit("- ") }
            ToolButton(Icons.Outlined.CheckBoxOutlineBlank, "Yapılacak kutusu") { edit("- [ ] ") }
            ToolButton(Icons.Outlined.Code, "Kod bloğu", highlighted = true) { edit("```\n\n```\n", 5) }
        }
    }

    AiResultDialog(
        vm = vm,
        hasSelection = selectedText.isNotBlank(),
        onAddCard = { r -> vm.addCard(noteId, "${r.label}: ${r.excerpt}", r.text) },
        onAppend = { appendToNote(it) },
        onReplace = { edit(it) },
    )

    if (showCustom) {
        CustomPromptDialog(
            onDismiss = { showCustom = false },
            onRun = { prompt ->
                vm.runAi("Özel istek", prompt, inputText, excerpt)
                showCustom = false
            },
        )
    }

    movingCard?.let { card ->
        MoveCardDialog(
            existing = cards.map { it.section }.filter { it.isNotBlank() }.distinct(),
            onDismiss = { movingCard = null },
            onPick = { section ->
                vm.moveCard(card, section)
                movingCard = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Not silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    note?.let { vm.deleteNote(it, undoable = true) }
                    confirmDelete = false
                    onBack()
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun CardItem(
    card: NoteCard,
    onAppend: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(card.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        NoteBody(card.body)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onAppend) { Text("Nota'ya ekle") }
            TextButton(onClick = onMove) { Text("Bölüm") }
            TextButton(onClick = onCopy) { Text("Kopyala") }
            TextButton(onClick = onDelete) { Text("Sil") }
        }
    }
}

@Composable
private fun MoveCardDialog(existing: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bölüme taşı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onPick("") }) { Text("Bölümsüz") }
                existing.forEach { s -> TextButton(onClick = { onPick(s) }) { Text(s) } }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Yeni bölüm adı") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(newName) }, enabled = newName.isNotBlank()) { Text("Oluştur ve taşı") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun CustomPromptDialog(onDismiss: () -> Unit, onRun: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Özel istek") },
        text = {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                minLines = 3,
                placeholder = { Text("Örn. Bunu bir sınav sorusuna çevir") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRun(prompt) }, enabled = prompt.isNotBlank()) { Text("Çalıştır") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun AiResultDialog(
    vm: AppViewModel,
    hasSelection: Boolean,
    onAddCard: (AiResult) -> Unit,
    onAppend: (String) -> Unit,
    onReplace: (String) -> Unit,
) {
    when (val s = vm.aiState) {
        AiState.Idle -> Unit
        else -> AlertDialog(
            onDismissRequest = { vm.clearAi() },
            title = { Text("AI sonucu") },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (s) {
                        AiState.Loading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Text("Düşünüyor…")
                        }
                        is AiState.Done -> {
                            Text(s.result.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            NoteBody(s.result.text)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { onAppend(s.result.text); vm.clearAi() }) { Text("Nota ekle") }
                                if (hasSelection) {
                                    TextButton(onClick = { onReplace(s.result.text); vm.clearAi() }) { Text("Seçimin yerine koy") }
                                }
                                TextButton(onClick = { onAddCard(s.result); vm.clearAi() }) { Text("Karta ekle") }
                            }
                        }
                        is AiState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                        AiState.Idle -> Unit
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearAi() }) { Text("Kapat") } },
        )
    }
}

private val SMART_PAIRS = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')

/** Açılış karakteri silinince karşılığı da silinir. */
private fun pairDelete(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
    if (new.text.length != old.text.length - 1) return null
    var p = 0
    while (p < new.text.length && old.text[p] == new.text[p]) p++
    val open = old.text[p]
    if (SMART_PAIRS[open] == null || old.text.getOrNull(p + 1) != SMART_PAIRS.getValue(open)) return null
    return TextFieldValue(old.text.removeRange(p, p + 2), TextRange(p))
}
