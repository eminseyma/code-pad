package com.codepad.app.ui

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.codepad.app.data.ThemeChoice

// Ortak marka renkleri
val Mint = Color(0xFF8CCBA9)
val Green = Color(0xFF2F7D5B)
val Forest = Color(0xFF1E4D39)
val MintLight = Color(0xFFDDEEE3)

// Kod blokları her temada koyu kalır
val CodeBg = Color(0xFF15231C)
val CodeText = Color(0xFFCFE8D9)
val CodeComment = Color(0xFF6F8E7E)
val CodeString = Color(0xFFE8D8A0)

private val MintScheme = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = MintLight, onPrimaryContainer = Forest,
    secondary = Mint, onSecondary = Forest,
    background = Color(0xFFF3F8F4), onBackground = Color(0xFF16261E),
    surface = Color.White, onSurface = Color(0xFF16261E),
    surfaceVariant = Color(0xFFE2EFEC), onSurfaceVariant = Color(0xFF566A60),
    outline = Color(0xFFDCE8E0),
)

private val WhiteScheme = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = MintLight, onPrimaryContainer = Forest,
    secondary = Mint, onSecondary = Forest,
    background = Color.White, onBackground = Color(0xFF16261E),
    surface = Color(0xFFF5F8F6), onSurface = Color(0xFF16261E),
    surfaceVariant = Color(0xFFEEF4F0), onSurfaceVariant = Color(0xFF566A60),
    outline = Color(0xFFDCE8E0),
)

private val BlackScheme = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF0B1A12),
    primaryContainer = Forest, onPrimaryContainer = MintLight,
    secondary = Mint, onSecondary = Color(0xFF0B1A12),
    background = Color.Black, onBackground = Color(0xFFE6F2EA),
    surface = Color(0xFF111613), onSurface = Color(0xFFE6F2EA),
    surfaceVariant = Color(0xFF1A221D), onSurfaceVariant = Color(0xFF9FB5A7),
    outline = Color(0xFF26332B),
)

fun schemeFor(choice: ThemeChoice): ColorScheme = when (choice) {
    ThemeChoice.MINT -> MintScheme
    ThemeChoice.WHITE -> WhiteScheme
    ThemeChoice.BLACK -> BlackScheme
}

@Composable
fun CodePadTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val scheme = schemeFor(choice)
    val view = LocalView.current
    // Durum çubuğu ve gezinme çubuğu, seçilen temaya uysun.
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        val light = scheme.background.luminance() > 0.5f
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Koyu tema mi? */
@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Defter kutularının arka planları (temaya göre). */
@Composable
fun tileTints(): List<Color> =
    if (isDark()) listOf(Color(0xFF1B2B22), Color(0xFF1A2A30), Color(0xFF262C1B), Color(0xFF1F2A24))
    else listOf(Color(0xFFDDEEE3), Color(0xFFE2EFEC), Color(0xFFECF0DC), Color(0xFFE8EEE4))
