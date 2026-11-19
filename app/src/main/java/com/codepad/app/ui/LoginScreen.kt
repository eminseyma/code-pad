package com.codepad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.app.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel, onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Mint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Code, contentDescription = null, tint = Forest, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Code Pad", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Forest)
        Text(
            if (create) "Yeni hesap oluştur" else "Notlarını telefonda ve bilgisayarda aynı anda gör",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-posta") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Şifre (en az 6 karakter)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (vm.authError.isNotBlank()) {
            Text(vm.authError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { vm.authenticate(email, password, create, onDone) },
            enabled = !vm.authBusy && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text(if (create) "Hesap oluştur" else "Giriş yap", fontWeight = FontWeight.Bold) }

        TextButton(onClick = { create = !create }) {
            Text(if (create) "Zaten hesabım var" else "Hesabım yok, oluştur")
        }
        if (!create) {
            TextButton(onClick = { showReset = true }) { Text("Şifremi unuttum") }
        }
        TextButton(onClick = onDone) { Text("Şimdilik sadece bu telefonda kullan") }
    }

    if (showReset) {
        ResetPasswordDialog(
            vm = vm,
            initialEmail = email,
            onDismiss = { showReset = false },
            onDone = {
                showReset = false
                onDone()
            },
        )
    }
}

@Composable
private fun ResetPasswordDialog(vm: AppViewModel, initialEmail: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var email by remember { mutableStateOf(initialEmail) }
    var sent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!sent) "Şifreni sıfırla" else "Kodu ve yeni şifreni gir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!sent) {
                    Text("E-posta adresine 6 haneli bir kod gönderelim.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-posta") },
                        singleLine = true,
                    )
                } else {
                    Text("$email adresine gelen kodu yaz ve yeni şifreni belirle.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Kod") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Yeni şifre (en az 6 karakter)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (vm.authError.isNotBlank()) {
                    Text(vm.authError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !vm.authBusy && (!sent && email.isNotBlank() || sent && code.isNotBlank() && newPass.length >= 6),
                onClick = {
                    if (!sent) vm.sendRecoveryCode(email) { sent = true }
                    else vm.completeRecovery(email, code, newPass, onDone)
                },
            ) { Text(if (!sent) "Kod gönder" else "Şifreyi değiştir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
