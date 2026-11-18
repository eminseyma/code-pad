package com.codepad.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.app.AppViewModel
import com.codepad.app.data.AiAction
import com.codepad.app.data.ThemeChoice
import com.codepad.app.drive.DriveApi
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.util.UUID

private fun themeLabel(t: ThemeChoice) = when (t) {
    ThemeChoice.MINT -> "Nane"
    ThemeChoice.WHITE -> "Beyaz"
    ThemeChoice.BLACK -> "Siyah"
}

private fun formatDate(ms: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }
    var saved by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiAction?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Ayarlardan çıkarken yazılmış ama kaydedilmemiş anahtar/model kaybolmasın.
    DisposableEffect(Unit) {
        onDispose { vm.saveSettings(key, model) }
    }

    // Google'ın tek seferlik izin ekranı bitince buraya döner.
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
            vm.onDriveConnected()
        } catch (e: ApiException) {
            vm.reportBackup("Giriş tamamlanamadı (kod ${e.statusCode}).")
        }
    }

    fun connectDrive() {
        scope.launch {
            try {
                val r = DriveApi.authorize(context)
                if (r.hasResolution()) {
                    authLauncher.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
                } else {
                    vm.onDriveConnected()
                }
            } catch (e: Exception) {
                vm.reportBackup("Bağlanılamadı: ${e.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri") }
            Text("Ayarlar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("Görünüm")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = vm.theme == choice,
                        onClick = { vm.changeTheme(choice) },
                        label = { Text(themeLabel(choice)) },
                    )
                }
            }

            SectionTitle("Gemini API")
            Text(
                "AI özellikleri için Google AI Studio'dan ücretsiz bir API anahtarı al (aistudio.google.com/apikey) ve buraya yapıştır. Anahtar yalnızca bu cihazda, şifreli olarak saklanır.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API anahtarı") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model adı") },
                supportingText = { Text("Google model adlarını güncelleyebilir; hata alırsan güncel adı gir.") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            if (vm.apiKey.isNotBlank()) {
                Text(
                    "Anahtar bu telefonda kayıtlı. Uygulamayı güncellediğinde silinmez, tekrar girmen gerekmez.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = {
                    vm.saveSettings(key, model)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (saved) "Kaydedildi" else "Kaydet", fontWeight = FontWeight.Bold) }

            SectionTitle("Google Drive yedeği")
            Text(
                "Notların, kartların ve sohbetlerin Drive'ında uygulamaya özel gizli bir alana yedeklenir. Bir kez giriş yaparsın; sonra değişiklikler kendiliğinden yedeklenir.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            if (!vm.driveConnected) {
                Button(
                    onClick = { connectDrive() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Google Drive'a bağlan", fontWeight = FontWeight.Bold) }
            } else {
                Text(
                    "Bağlı. " + if (vm.lastBackup > 0) "Son yedek: ${formatDate(vm.lastBackup)}" else "Henüz yedek alınmadı.",
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.backupNow(manual = true) },
                        enabled = !vm.backupBusy,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Şimdi yedekle", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = !vm.backupBusy,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Geri yükle", fontWeight = FontWeight.Bold) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("API anahtarını da yedekle", fontWeight = FontWeight.Bold)
                        Text(
                            "Yeni telefonda ya da yeniden kurulumda anahtarı tekrar girmene gerek kalmaz.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = vm.backupKeyEnabled, onCheckedChange = { vm.changeBackupKey(it) })
                }
                TextButton(onClick = { vm.disconnectDrive() }) { Text("Bağlantıyı kes") }
            }
            if (vm.backupStatus.isNotBlank()) {
                Text(vm.backupStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("AI eylemleri")
            Text(
                "Editörde görünen hızlı düğmeler. Değiştir, kısalt veya yenisini ekle. Metin, eylemin yönergesi olarak modele gider.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            vm.actions.forEach { action ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(action.label, fontWeight = FontWeight.Bold)
                        Text(
                            action.instruction,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 2,
                        )
                    }
                    IconButton(onClick = { editing = action }) { Icon(Icons.Outlined.Edit, contentDescription = "Düzenle") }
                    IconButton(onClick = { vm.saveActions(vm.actions.filter { it.id != action.id }) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Sil")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { creating = true }) { Text("+ Yeni eylem") }
                TextButton(onClick = { vm.resetActions() }) { Text("Varsayılanlara dön") }
            }

            Text(
                "Not: AI kullandığında not içeriği Google'a gönderilir. Hassas bilgileri notlara yazmamaya dikkat et.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Yedekten geri yüklensin mi?") },
            text = { Text("Bu telefondaki tüm notlar, kartlar ve sohbetler Drive'daki yedekle değiştirilir.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreFromDrive()
                    confirmRestore = false
                }) { Text("Geri yükle") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Vazgeç") } },
        )
    }

    if (creating) {
        ActionEditDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { label, instruction ->
                vm.saveActions(vm.actions + AiAction(UUID.randomUUID().toString(), label, instruction))
                creating = false
            },
        )
    }

    editing?.let { current ->
        ActionEditDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { label, instruction ->
                vm.saveActions(
                    vm.actions.map { if (it.id == current.id) it.copy(label = label, instruction = instruction) else it },
                )
                editing = null
            },
        )
    }
}

@Composable
private fun ActionEditDialog(initial: AiAction?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var instruction by remember { mutableStateOf(initial?.instruction.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni AI eylemi" else "Eylemi düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Düğme adı") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    minLines = 3,
                    label = { Text("Yönerge (ne yapsın?)") },
                    modifier = Modifier.fillMaxWidth(),
                )
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
