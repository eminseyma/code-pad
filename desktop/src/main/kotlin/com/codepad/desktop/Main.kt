package com.codepad.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Code Pad",
        state = rememberWindowState(width = 1120.dp, height = 740.dp),
    ) { App() }
}
