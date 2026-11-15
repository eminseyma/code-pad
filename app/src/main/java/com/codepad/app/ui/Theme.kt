package com.codepad.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Code Pad paleti: yumuşak nane yeşili, cırtlak olmayan tonlar.
val Mint = Color(0xFF8CCBA9)
val Green = Color(0xFF2F7D5B)
val Forest = Color(0xFF1E4D39)
val MintLight = Color(0xFFDDEEE3)
val Surface = Color(0xFFF3F8F4)
val Line = Color(0xFFDCE8E0)
val Ink = Color(0xFF16261E)
val Muted = Color(0xFF566A60)

val CodeBg = Color(0xFF15231C)
val CodeText = Color(0xFFCFE8D9)
val CodeComment = Color(0xFF6F8E7E)
val CodeString = Color(0xFFE8D8A0)

val NotebookTints = listOf(
    Color(0xFFDDEEE3),
    Color(0xFFE2EFEC),
    Color(0xFFECF0DC),
    Color(0xFFE8EEE4),
)

private val Scheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = MintLight,
    onPrimaryContainer = Forest,
    secondary = Mint,
    onSecondary = Forest,
    background = Surface,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE2EFEC),
    onSurfaceVariant = Muted,
    outline = Line,
)

@Composable
fun CodePadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
