package com.codepad.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BadgeGreen = Color(0xFF1E4D39)

/** Kod bloğu dilinden dosya uzantısı ve kart rengi (telefondaki NoteBadge ile aynı). */
private val LangMap = mapOf(
    "kotlin" to ("kt" to "sky"), "kt" to ("kt" to "sky"),
    "java" to ("java" to "rose"),
    "python" to ("py" to "sand"), "py" to ("py" to "sand"),
    "javascript" to ("js" to "sand"), "js" to ("js" to "sand"),
    "typescript" to ("ts" to "sky"), "ts" to ("ts" to "sky"),
    "sql" to ("sql" to "ink"),
    "bash" to ("sh" to "ink"), "sh" to ("sh" to "ink"), "shell" to ("sh" to "ink"),
    "json" to ("json" to "mint"),
    "xml" to ("xml" to "rose"), "html" to ("html" to "rose"), "css" to ("css" to "sky"),
    "dart" to ("dart" to "sky"), "go" to ("go" to "sky"),
    "rust" to ("rs" to "rose"), "rs" to ("rs" to "rose"),
    "c" to ("c" to "ink"), "cpp" to ("cpp" to "ink"), "c++" to ("cpp" to "ink"),
    "swift" to ("swift" to "rose"),
    "yaml" to ("yml" to "mint"), "yml" to ("yml" to "mint"),
    "gradle" to ("gradle" to "mint"),
)

private fun langBadge(content: String): Pair<String, String>? {
    val lang = Regex("```\\s*([\\w+#-]+)").find(content)?.groupValues?.get(1)?.lowercase() ?: return null
    LangMap[lang]?.let { return it }
    return lang.take(4) to "mint"
}

/** Notun solundaki dil rozeti: kod varsa dosya uzantısı, yoksa "md". */
@Composable
fun NoteBadgeDesk(content: String) {
    val badge = langBadge(content)
    val label = badge?.first ?: "md"
    val key = badge?.second ?: "mint"
    val fg = if (key == "ink") Color.White else BadgeGreen
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(coverBg(key)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (label.length > 3) 10.sp else 13.sp,
            maxLines = 1,
        )
    }
}

/** Not listesindeki yuvarlak kart (telefondaki NoteRow ile aynı düzen). */
@Composable
fun NoteCardDesk(n: Nt, active: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color(0x1A000000),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoteBadgeDesk(n.content)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                n.title.ifBlank { "Başlıksız not" },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                n.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                fontSize = 12.sp,
                color = muted,
                maxLines = 1,
            )
        }
    }
}
