package com.codepad.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codepad.app.AppViewModel
import com.codepad.app.data.Note
import com.codepad.app.data.Notebook

@Composable
fun NotebooksScreen(
    vm: AppViewModel,
    onOpenNotebook: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
) {
    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val counts by vm.noteCounts.collectAsStateWithLifecycle()
    val notes by vm.allNotes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }

    val shown = if (query.isBlank()) notes.take(5)
    else notes.filter { it.title.contains(query, true) || it.content.contains(query, true) }
    val rows = (notebooks.map<Notebook, Notebook?> { it } + listOf(null)).chunked(2)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(onAssistant = onAssistant, onSettings = onSettings) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Notlarda ve kodlarda ara") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            if (query.isBlank()) {
                item { Text("Defterlerim", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                items(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { nb ->
                            if (nb == null) NewNotebookTile(Modifier.weight(1f)) { showNew = true }
                            else NotebookTile(nb, counts[nb.id] ?: 0, Modifier.weight(1f)) { onOpenNotebook(nb.id) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    if (query.isBlank()) "Son notlar" else "Sonuçlar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            if (shown.isEmpty()) {
                item { Text("Henüz not yok. Sağ alttan ilk notunu ekle.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(shown, key = { it.id }) { note ->
                NoteRow(note, notebooks.firstOrNull { it.id == note.notebookId }?.name.orEmpty()) { onOpenNote(note.id) }
            }
        }

        Row(
            Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingActionButton(
                onClick = onAssistant,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp),
            ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Asistan") }
            ExtendedFloatingActionButton(
                onClick = onNewNote,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Yeni not", fontWeight = FontWeight.Bold) },
            )
        }
    }

    if (showNew) NewNotebookDialog(onDismiss = { showNew = false }) { name ->
        vm.addNotebook(name)
        showNew = false
    }
}

/** Üst çubuk: solda uygulama adı, sağda Asistan ve Ayarlar. */
@Composable
private fun Header(onAssistant: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Mint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Code, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Code Pad",
            modifier = Modifier.weight(1f),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Ayarlar", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun NotebookTile(nb: Notebook, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = coverColors(nb.coverColor)
    Column(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.bg)
            .drawBehind { drawCoverDots(c.fg.copy(alpha = 0.12f)) }
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(nb.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.fg, maxLines = 2)
        Text("$count not", fontSize = 12.sp, color = c.fg.copy(alpha = 0.75f))
    }
}

@Composable
private fun NewNotebookTile(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(BorderStroke(1.5.dp, Mint), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ Yeni defter", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun NoteRow(note: Note, notebookName: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoteBadge(note.content)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                note.title.ifBlank { "Başlıksız not" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text(notebookName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun NewNotebookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni defter") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Örn. Kotlin ve Android") },
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Oluştur") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun NotebookScreen(
    vm: AppViewModel,
    notebookId: Long,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
) {
    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val notebook = notebooks.firstOrNull { it.id == notebookId }
    val cover = coverColors(notebook?.coverColor ?: "mint")
    val notes by remember(notebookId) { vm.notesIn(notebookId) }.collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(cover.bg).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri", tint = cover.fg)
                }
                Text(
                    notebook?.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = cover.fg,
                    maxLines = 1,
                )
                IconButton(onClick = { showStyle = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Defter görünümü", tint = cover.fg)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Defteri sil", tint = cover.fg)
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (notes.isEmpty()) item { Text("Bu defterde henüz not yok.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(notes, key = { it.id }) { note -> NoteRow(note, "", onClick = { onOpenNote(note.id) }) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewNote,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Yeni not", fontWeight = FontWeight.Bold) },
        )
    }

    if (showStyle && notebook != null) {
        NotebookStyleDialog(
            cover = notebook.coverColor,
            page = notebook.pageStyle,
            onDismiss = { showStyle = false },
            onSave = { c, p ->
                vm.updateNotebookStyle(notebook, c, p)
                showStyle = false
            },
        )
    }

    if (confirmDelete && notebook != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Defter silinsin mi?") },
            text = { Text("\"${notebook.name}\" ve içindeki tüm notlar kalıcı olarak silinir.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNotebook(notebook)
                    confirmDelete = false
                    onBack()
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun NotebookStyleDialog(cover: String, page: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var selCover by remember { mutableStateOf(cover) }
    var selPage by remember { mutableStateOf(page) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Defter görünümü") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Kapak rengi", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoverKeys.forEach { key ->
                        val c = coverColors(key)
                        val selected = selCover == key
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c.bg)
                                .border(
                                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) c.fg else MaterialTheme.colorScheme.outline),
                                    CircleShape,
                                )
                                .clickable { selCover = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(c.fg))
                        }
                    }
                }
                Text(
                    "Seçili: ${coverLabel(selCover)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Sayfa stili", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PageStyles.forEach { p ->
                        FilterChip(
                            selected = selPage == p,
                            onClick = { selPage = p },
                            label = { Text(pageLabel(p)) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selCover, selPage) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
