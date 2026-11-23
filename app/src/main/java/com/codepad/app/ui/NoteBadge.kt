package com.codepad.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kod bloğu dilinden dosya uzantısı ve kart rengi. */
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

/** Notun ilk kod bloğuna bakıp (etiket, renk anahtarı) döndürür. Kod yoksa null. */
fun noteBadgeFor(content: String): Pair<String, String>? {
    val lang = Regex("```\\s*([\\w+#-]+)").find(content)?.groupValues?.get(1)?.lowercase() ?: return null
    LangMap[lang]?.let { return it }
    return lang.take(4) to "mint"
}

@Composable
fun NoteBadge(content: String) {
    val badge = noteBadgeFor(content)
    val label = badge?.first ?: "md"
    val colors = coverColors(badge?.second ?: "mint")
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg),
    ) {
        if (badge == null) {
            Icon(
                Icons.Outlined.Notes,
                contentDescription = null,
                tint = colors.fg,
                modifier = Modifier.align(Alignment.Center).size(22.dp),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    label.uppercase(),
                    color = colors.fg,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (label.length > 3) 10.sp else 13.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(16.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.fg.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

private val TagRegex = Regex("""(?<![\w#])#([\p{L}][\p{L}\p{N}_-]{1,29})""")

/** Not metnindeki #etiket'leri (kod blokları hariç) küçük harfle listeler. */
fun extractTags(content: String): List<String> {
    val outside = parseBlocks(content).filterIsInstance<Block.Text>().joinToString("\n") { it.text }
    return TagRegex.findAll(outside).map { it.groupValues[1].lowercase() }.distinct().toList()
}

/** Yatay kaydırılan etiket süzgeci. */
@Composable
fun TagRow(tags: List<String>, selected: String?, onPick: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { t ->
            val on = t == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) primary else primary.copy(alpha = 0.10f))
                    .clickable { onPick(t) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("#$t", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else primary)
            }
        }
    }
}
