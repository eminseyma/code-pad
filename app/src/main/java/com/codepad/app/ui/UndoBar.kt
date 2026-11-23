package com.codepad.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codepad.app.AppViewModel
import kotlinx.coroutines.delay

/** Not silinince altta çıkan "Geri al" çubuğu. */
@Composable
fun UndoBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val pending = vm.pendingUndo
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(6_000)
            vm.clearUndo()
        }
    }
    AnimatedVisibility(visible = pending != null, modifier = modifier.padding(16.dp).navigationBarsPadding()) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Not silindi", color = MaterialTheme.colorScheme.inverseOnSurface)
            TextButton(onClick = { vm.undoDelete() }) {
                Text("GERİ AL", color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
