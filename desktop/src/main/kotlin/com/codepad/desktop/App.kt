package com.codepad.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.desktop.sync.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Mint = Color(0xFF8CCBA9)
private val Forest = Color(0xFF1E4D39)
private val Green = Color(0xFF2F7D5B)
private val Paper = Color(0xFFF2F8F4)
private val Muted = Color(0xFF5E7568)

private fun schemeFor(theme: String): ColorScheme = when (theme) {
    "white" -> lightColorScheme(
        primary = Green, onPrimary = Color.White, background = Color.White,
        surface = Color(0xFFF4F6F5), onSurface = Color(0xFF1B1F1D), onSurfaceVariant = Color(0xFF5E6B64),
    )
    "black" -> darkColorScheme(
        primary = Mint, onPrimary = Color(0xFF0B1410), background = Color(0xFF0B0F0D),
        surface = Color(0xFF161C19), onSurface = Color(0xFFE6EFE9), onSurfaceVariant = Color(0xFF9AAAA0),
    )
    else -> lightColorScheme(
        primary = Green, onPrimary = Color.White, background = Paper,
        surface = Color.White, onSurface = Forest, onSurfaceVariant = Muted,
    )
}

/** Defter kapağı rengi (telefondakiyle aynı anahtarlar). */
fun coverBg(key: String): Color = when (key) {
    "sky" -> Color(0xFFCFE6F5)
    "sand" -> Color(0xFFF2E6CC)
    "rose" -> Color(0xFFF5D6DC)
    "ink" -> Color(0xFF26332D)
    else -> Color(0xFFD6F0E2)
}

@Composable
fun App() {
    MaterialTheme(colorScheme = schemeFor(Store.theme)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    var showReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Code Pad", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(
            if (create) "Yeni hesap oluştur" else "Telefondaki hesabınla giriş yap",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-posta") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
        Row {
            TextButton(onClick = { create = !create }) {
                Text(if (create) "Zaten hesabım var" else "Hesabım yok, oluştur")
            }
            if (!create) {
                TextButton(onClick = { showReset = true }) { Text("Şifremi unuttum") }
            }
        }
    }

    if (showReset) {
        ResetDialog(
            initialEmail = email,
            onDismiss = { showReset = false },
            onDone = {
                showReset = false
                onDone()
            },
        )
    }
}

/** E-postadaki kodla şifre sıfırlama (telefondaki akışla aynı). */
@Composable
private fun ResetDialog(initialEmail: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var sent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!sent) "Şifreni sıfırla" else "Kodu ve yeni şifreni gir") },
        text = {
            Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!sent) {
                    Text("E-posta adresine 6 haneli bir kod gönderelim.", fontSize = 13.sp)
                    OutlinedTextField(value = email, onValueChange = { email = it }, singleLine = true, label = { Text("E-posta") })
                } else {
                    Text("$email adresine gelen kodu yaz ve yeni şifreni belirle.", fontSize = 13.sp)
                    OutlinedTextField(value = code, onValueChange = { code = it }, singleLine = true, label = { Text("Kod") })
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        singleLine = true,
                        label = { Text("Yeni şifre (en az 6 karakter)") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (!sent && email.isNotBlank() || sent && code.isNotBlank() && newPass.length >= 6),
                onClick = {
                    busy = true
                    error = ""
                    scope.launch {
                        try {
                            if (!sent) {
                                withContext(Dispatchers.IO) { SupabaseClient.sendRecoveryCode(email.trim()) }
                                sent = true
                            } else {
                                val s = withContext(Dispatchers.IO) {
                                    val x = SupabaseClient.verifyRecovery(email.trim(), code.trim())
                                    SupabaseClient.updatePassword(x.accessToken, newPass)
                                    x
                                }
                                Store.setSession(s)
                                onDone()
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "İşlem başarısız"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (!sent) "Kod gönder" else "Şifreyi değiştir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun Workspace(onLogout: () -> Unit) {
    var selNb by remember { mutableStateOf<String?>(null) }
    var selNote by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("notes") }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val nbs = Store.activeNotebooks()
    val currentNb = selNb?.takeIf { id -> nbs.any { it.id == id } } ?: nbs.firstOrNull()?.id
    val nb = nbs.firstOrNull { it.id == currentNb }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e: KeyEvent ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.N -> {
                        currentNb?.let {
                            selNote = Store.addNote(it).id
                            mode = "notes"
                        }
                        true
                    }
                    Key.F -> {
                        mode = "notes"
                        true
                    }
                    Key.H -> {
                        mode = if (mode == "assistant") "notes" else "assistant"
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Sol kenar çubuğu
        Column(
            Modifier.width(240.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp),
        ) {
            Text("Code Pad", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(Sync.status.ifBlank { "Henüz eşitlenmedi" }, fontSize = 12.sp, color = muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { Sync.runNow() }) { Text("Eşitle", fontSize = 13.sp) }
                OutlinedButton(onClick = onLogout) { Text("Çıkış", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { mode = if (mode == "assistant") "notes" else "assistant" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (mode == "assistant") "Notlara dön" else "Asistan (Ctrl+H)") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) { Text("Ayarlar") }
            Spacer(Modifier.height(16.dp))
            Text("DEFTERLER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = muted)
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val n = Store.addNotebook("Yeni defter")
                    selNb = n.id
                    selNote = null
                    mode = "notes"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Yeni defter") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(nbs) { item ->
                    val active = item.id == currentNb && mode == "notes"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                selNb = item.id
                                selNote = null
                                mode = "notes"
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(coverBg(item.cover)).border(1.dp, muted.copy(alpha = 0.4f), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        if (mode == "assistant") {
            AssistantPane(Modifier.weight(1f).fillMaxHeight())
        } else {
            // Orta: not listesi
            Column(Modifier.width(300.dp).fillMaxHeight().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nb?.name ?: "Defter yok",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (nb != null) {
                        TextButton(onClick = { showStyle = true }) { Text("Görünüm", fontSize = 12.sp) }
                        TextButton(onClick = {
                            Store.deleteNotebook(nb.id)
                            selNb = null
                            selNote = null
                        }) { Text("Sil", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Ara (Ctrl+F)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { currentNb?.let { selNote = Store.addNote(it).id } },
                    enabled = currentNb != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ Yeni not (Ctrl+N)") }
                Spacer(Modifier.height(8.dp))
                val list = currentNb?.let { Store.notesIn(it) }.orEmpty()
                    .filter { query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list) { n ->
                        val active = n.id == selNote
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selNote = n.id }
                                .padding(10.dp),
                        ) {
                            Text(
                                n.title.ifBlank { "Başlıksız not" },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                n.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                                fontSize = 12.sp,
                                color = muted,
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
                    Text("Bir not seç ya da yeni not oluştur.", color = muted)
                } else {
                    EditorPane(selected.id)
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
    if (showStyle && nb != null) {
        StyleDialog(nb = nb, onDismiss = { showStyle = false })
    }
}

@Composable
private fun StyleDialog(nb: Nb, onDismiss: () -> Unit) {
    var cover by remember { mutableStateOf(nb.cover) }
    var page by remember { mutableStateOf(nb.page) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Defter görünümü") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kapak", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("mint", "sky", "sand", "rose", "ink").forEach { k ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(coverBg(k))
                                .border(if (cover == k) 3.dp else 1.dp, if (cover == k) Green else Color(0x33000000), CircleShape)
                                .clickable { cover = k },
                        )
                    }
                }
                Text("Sayfa", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("lined" to "Çizgili", "dotted" to "Noktalı", "grid" to "Kareli", "blank" to "Boş").forEach { (k, label) ->
                        FilterChip(selected = page == k, onClick = { page = k }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.setNotebookStyle(nb.id, cover, page)
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(Store.apiKey) }
    var model by remember { mutableStateOf(Store.model) }
    var includeKey by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AiAction?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ayarlar", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                Modifier.width(560.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Görünüm", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mint" to "Nane", "white" to "Beyaz", "black" to "Siyah").forEach { (k, label) ->
                        FilterChip(
                            selected = Store.theme == k,
                            onClick = {
                                Store.theme = k
                                Store.save()
                            },
                            label = { Text(label) },
                        )
                    }
                }

                Text("Gemini", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("API anahtarını Google AI Studio'dan al. Bu bilgisayarda düz metin olarak saklanır.", fontSize = 12.sp, color = muted)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { Text("API anahtarı") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    label = { Text("Model adı") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    Store.apiKey = key.trim()
                    Store.model = model.trim().ifBlank { DefaultModel }
                    Store.save()
                    status = "Gemini ayarları kaydedildi."
                }) { Text("Gemini ayarlarını kaydet") }

                Text("AI eylemleri", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Editördeki düğmeler. Değiştirebilir, silebilir ya da yenisini ekleyebilirsin.", fontSize = 12.sp, color = muted)
                Store.actions.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontWeight = FontWeight.Bold)
                            Text(a.instruction, fontSize = 12.sp, color = muted, maxLines = 2)
                        }
                        TextButton(onClick = { editing = a }) { Text("Düzenle", fontSize = 12.sp) }
                        TextButton(onClick = {
                            Store.actions.remove(a)
                            Store.save()
                        }) { Text("Sil", fontSize = 12.sp) }
                    }
                }
                Row {
                    TextButton(onClick = { creating = true }) { Text("+ Yeni eylem") }
                    TextButton(onClick = {
                        Store.actions.clear()
                        Store.actions.addAll(DefaultActions)
                        Store.save()
                    }) { Text("Varsayılanlara dön") }
                }

                Text("Yedek", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Notlar, kartlar, sohbetler ve ayarlar tek dosyada. Dosyayı istediğin yere kaydedebilirsin.", fontSize = 12.sp, color = muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("API anahtarını dosyaya ekle", Modifier.weight(1f))
                    Switch(checked = includeKey, onCheckedChange = { includeKey = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val path = pickFile(save = true)
                        if (path != null) {
                            status = runCatching {
                                File(path).writeText(Backup.export(includeKey), Charsets.UTF_8)
                                "Yedek kaydedildi."
                            }.getOrElse { "Kaydedilemedi: ${it.message}" }
                        }
                    }) { Text("Yedek dosyası oluştur", fontSize = 13.sp) }
                    OutlinedButton(onClick = {
                        val path = pickFile(save = false)
                        if (path != null) pendingRestore = path
                    }) { Text("Yedek dosyasından yükle", fontSize = 13.sp) }
                }
                if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )

    if (creating) {
        ActionDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { label, instruction ->
                Store.actions.add(AiAction(UUID.randomUUID().toString(), label, instruction))
                Store.save()
                creating = false
            },
        )
    }
    editing?.let { current ->
        ActionDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { label, instruction ->
                val i = Store.actions.indexOfFirst { it.id == current.id }
                if (i >= 0) Store.actions[i] = current.copy(label = label, instruction = instruction)
                Store.save()
                editing = null
            },
        )
    }
    pendingRestore?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Yedekten yüklensin mi?") },
            text = { Text("Bu bilgisayardaki tüm notlar, kartlar ve sohbetler dosyadaki yedekle değiştirilir.") },
            confirmButton = {
                TextButton(onClick = {
                    status = runCatching {
                        Backup.restore(File(path).readText(Charsets.UTF_8))
                        "Yedek yüklendi."
                    }.getOrElse { "Yüklenemedi: ${it.message}" }
                    pendingRestore = null
                }) { Text("Yükle") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun ActionDialog(initial: AiAction?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var instruction by remember { mutableStateOf(initial?.instruction.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni AI eylemi" else "Eylemi düzenle") },
        text = {
            Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true, label = { Text("Düğme adı") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instruction, onValueChange = { instruction = it }, minLines = 3, label = { Text("Yönerge (ne yapsın?)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.trim(), instruction.trim()) },
                enabled = label.isNotBlank() && instruction.isNotBlank(),
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Yerel dosya seçici (Windows'ta kaydet / aç penceresi). */
private fun pickFile(save: Boolean): String? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        if (save) "Yedeği kaydet" else "Yedek dosyasını seç",
        if (save) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD,
    )
    if (save) dialog.file = "codepad-yedek.json"
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name).absolutePath
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
