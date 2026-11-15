package com.codepad.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun AssistantScreen(vm: AppViewModel) {
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
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Asistan", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Forest)
            if (messages.isNotEmpty()) {
                IconButton(onClick = vm::clearChat) { Icon(Icons.Outlined.Delete, contentDescription = "Sohbeti temizle") }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Notlarına dayanarak soru sorabilirsin.", color = Muted)
                        Suggestions.forEach { s ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Mint), RoundedCornerShape(14.dp))
                                    .clickable { send(s) }
                                    .padding(14.dp),
                            ) { Text(s, color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
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
                                .background(Green)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) { Text(m.text, color = Color.White, fontSize = 14.sp) }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Line), RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                            .padding(14.dp),
                    ) { NoteBody(m.text) }
                }
            }
            if (vm.chatBusy) item { Text("Düşünüyor…", color = Muted, fontSize = 13.sp) }
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
                colors = IconButtonDefaults.iconButtonColors(containerColor = Green, contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gönder") }
        }
    }
}
