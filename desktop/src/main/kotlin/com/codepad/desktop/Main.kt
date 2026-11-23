package com.codepad.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val icon = BitmapPainter(loadImageBitmap(object {}.javaClass.getResourceAsStream("/codepad.png")!!))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Code Pad",
        icon = icon,
        state = rememberWindowState(width = 1120.dp, height = 740.dp),
    ) {
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(960, 640) }
        App()
    }
}
