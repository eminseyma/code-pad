package com.codepad.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.app.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ayarlar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Forest)
        Text("Gemini API", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            "AI özellikleri için Google AI Studio'dan ücretsiz bir API anahtarı al (aistudio.google.com/apikey) ve buraya yapıştır. Anahtar yalnızca bu cihazda, şifreli olarak saklanır.",
            color = Muted,
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
        Button(
            onClick = {
                vm.saveSettings(key, model)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green),
        ) { Text(if (saved) "Kaydedildi" else "Kaydet", fontWeight = FontWeight.Bold) }
        Text(
            "Not: AI kullandığında not içeriği Google'a gönderilir. Hassas bilgileri notlara yazmamaya dikkat et.",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}
