package com.codepad.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Defter kapağı renkleri: arka plan ve üstündeki yazı/desen rengi. */
data class CoverColors(val bg: Color, val fg: Color)

val CoverKeys = listOf("mint", "sky", "sand", "rose", "ink")

fun coverLabel(key: String): String = when (key) {
    "sky" -> "Gök"
    "sand" -> "Kum"
    "rose" -> "Gül"
    "ink" -> "Mürekkep"
    else -> "Nane"
}

fun coverColors(key: String): CoverColors = when (key) {
    "sky" -> CoverColors(Color(0xFFCFE6F5), Color(0xFF1F4E6B))
    "sand" -> CoverColors(Color(0xFFF2E6CC), Color(0xFF6B5420))
    "rose" -> CoverColors(Color(0xFFF5D6DC), Color(0xFF6B2A3A))
    "ink" -> CoverColors(Color(0xFF1F2A24), Color(0xFF8CCBA9))
    else -> CoverColors(Color(0xFFDDEEE3), Color(0xFF1E4D39))
}

val PageStyles = listOf("lined", "dotted", "grid", "blank")

fun pageLabel(key: String): String = when (key) {
    "dotted" -> "Noktalı"
    "grid" -> "Kareli"
    "blank" -> "Boş"
    else -> "Çizgili"
}

/** Sayfa arka planını çizer. Satır aralığı 28 dp. */
fun DrawScope.drawPage(style: String, color: Color) {
    val step = 28.dp.toPx()
    when (style) {
        "lined" -> {
            var y = step
            while (y < size.height) {
                drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        "grid" -> {
            var y = step
            while (y < size.height) {
                drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            var x = step
            while (x < size.width) {
                drawLine(color = color, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
        }
        "dotted" -> {
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) {
                    drawCircle(color = color, radius = 1.6.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
}

/** Kapağın üstündeki hafif nokta deseni. */
fun DrawScope.drawCoverDots(color: Color) {
    val step = 14.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color = color, radius = 1.4.dp.toPx(), center = Offset(x, y))
            x += step
        }
        y += step
    }
}
