package com.codepad.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.app.AiAction
import com.codepad.app.AiState
import com.codepad.app.AppViewModel
import com.codepad.app.data.Note
import kotlinx.coroutines.delay

private fun TextFieldValue.insert(text: String, cursorBack: Int = 0): TextFieldValue {
    val s = selection.min
    val e = selection.max
    val newText = this.text.replaceRange(s, e, text)
    return TextFieldValue(newText, TextRange(s + text.length - cursorBack))
}

@Composable
fun EditorScreen(vm: AppViewModel, noteId: Long, onBack: () -> Unit) {
    var note by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var loaded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri") }
            Row(Modifier.weight(1f)) {}
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
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Başlık", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, color = Ink),
                colors = transparent,
            )
            if (preview) {
                NoteBody(content.text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } else {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
                    placeholder = { Text("Yaz… Kod için ``` ile blok aç ya da alttaki </> düğmesine bas.") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    keyboardOptions = KeyboardOptions.Default,
                    colors = transparent,
                )
            }
        }

        // AI hızlı eylemleri
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiAction.entries.forEach { action ->
                AssistChip(
                    onClick = { vm.runAction(action, title, content.text) },
                    label = { Text(action.label, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White, labelColor = Forest, leadingIconContentColor = Green),
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = Mint),
                )
            }
        }

        HorizontalDivider(color = Line)
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ToolButton(Icons.Outlined.Title, "Başlık") { content = content.insert("## ") }
            ToolButton(Icons.Outlined.FormatBold, "Kalın") { content = content.insert("****", cursorBack = 2) }
            ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Liste") { content = content.insert("- ") }
            ToolButton(Icons.Outlined.CheckBoxOutlineBlank, "Yapılacak kutusu") { content = content.insert("- [ ] ") }
            ToolButton(Icons.Outlined.Code, "Kod bloğu", highlighted = true) {
                content = content.insert("```kotlin\n\n```\n", cursorBack = 5)
            }
        }
    }

    AiResultDialog(vm, onAppend = { text -> content = TextFieldValue(content.text.trimEnd() + "\n\n---\n" + text + "\n") })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Not silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    note?.let { vm.deleteNote(it) }
                    confirmDelete = false
                    onBack()
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
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
            containerColor = if (highlighted) MintLight else Color.Transparent,
            contentColor = if (highlighted) Forest else Muted,
        ),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun AiResultDialog(vm: AppViewModel, onAppend: (String) -> Unit) {
    when (val s = vm.aiState) {
        AiState.Idle -> Unit
        else -> AlertDialog(
            onDismissRequest = vm::clearAi,
            title = { Text("AI asistan") },
            text = {
                when (s) {
                    AiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text("Düşünüyor…")
                    }
                    is AiState.Done -> Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { NoteBody(s.text) }
                    is AiState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    AiState.Idle -> Unit
                }
            },
            confirmButton = {
                if (s is AiState.Done) {
                    TextButton(onClick = {
                        onAppend(s.text)
                        vm.clearAi()
                    }) { Text("Nota ekle") }
                }
            },
            dismissButton = { TextButton(onClick = vm::clearAi) { Text("Kapat") } },
        )
    }
}
