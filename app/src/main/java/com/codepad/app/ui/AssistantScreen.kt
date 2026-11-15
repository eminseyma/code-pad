package com.codepad.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codepad.app.AppViewModel

private val Suggestions = listOf(
    "Son notlarımdan kısa bir özet çıkar",
    "Hangi konularda eksiğim var?",
    "Notlarımdaki kodlardan bir quiz hazırla",
)

@Composable
fun AssistantScreen(vm: AppViewModel, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = vm.chat

    LaunchedEffect(messages.size, vm.chatBusy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun send(text: String) {
        vm.ask(text)
        input = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri") }
            Text(
                "Asistan",
                Modifier.weight(1f),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (messages.isNotEmpty()) {
                IconButton(onClick = { vm.clearChat() }) { Icon(Icons.Outlined.Delete, contentDescription = "Sohbeti temizle") }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Notlarına dayanarak soru sorabilirsin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Suggestions.forEach { s ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.secondary), RoundedCornerShape(14.dp))
                                    .clickable { send(s) }
                                    .padding(14.dp),
                            ) {
                                Text(s, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, m ->
                if (m.role == "user") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) { Text(m.text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp) }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                            .padding(14.dp),
                    ) { NoteBody(m.text) }
                }
            }
            if (vm.chatBusy) item { Text("Düşünüyor…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Notlarına bir şey sor…") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 4,
            )
            IconButton(
                onClick = { send(input) },
                enabled = input.isNotBlank() && !vm.chatBusy,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gönder") }
        }
    }
}
