package com.codepad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Block {
    data class Text(val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
}

/** ``` ile çevrili bölümleri kod bloğu, kalanını düz metin olarak ayırır. */
fun parseBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val buf = StringBuilder()
    var inCode = false
    var lang = ""
    for (line in src.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out += Block.Code(lang, buf.toString().trimEnd())
                buf.clear()
                inCode = false
            } else {
                if (buf.isNotBlank()) out += Block.Text(buf.toString().trimEnd())
                buf.clear()
                lang = line.trim().removePrefix("```").trim()
                inCode = true
            }
        } else {
            buf.appendLine(line)
        }
    }
    if (buf.isNotBlank()) {
        out += if (inCode) Block.Code(lang, buf.toString().trimEnd()) else Block.Text(buf.toString().trimEnd())
    }
    return out
}

private val Keywords = setOf(
    "val", "var", "fun", "class", "object", "interface", "if", "else", "when", "for", "while", "return",
    "import", "package", "def", "function", "const", "let", "true", "false", "null", "None", "True", "False",
    "in", "is", "override", "suspend", "private", "public", "static", "void", "int", "String", "async",
    "await", "try", "catch", "throw", "new", "this", "self", "from", "as", "lambda", "yield", "launch",
)

private val TokenRegex = Regex("""//.*|#.*|"(?:\\.|[^"\\])*"|\b[A-Za-z_]\w*\b""")

/** Basit, bağımlılıksız sözdizimi renklendirme (dilden bağımsız). */
fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in TokenRegex.findAll(code)) {
        append(code.substring(last, m.range.first))
        val t = m.value
        val color = when {
            t.startsWith("//") || t.startsWith("#") -> CodeComment
            t.startsWith("\"") -> CodeString
            t in Keywords -> Mint
            else -> null
        }
        if (color != null) withStyle(SpanStyle(color = color)) { append(t) } else append(t)
        last = m.range.last + 1
    }
    append(code.substring(last))
}

@Composable
fun CodeBlock(lang: String, code: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CodeBg)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lang.ifBlank { "kod" },
                color = Mint,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Kodu kopyala", tint = CodeText, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            highlight(code),
            color = CodeText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            lineHeight = 21.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        )
    }
}

/** Not içeriğini (metin + kod blokları) okunur biçimde gösterir. */
@Composable
fun NoteBody(content: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parseBlocks(content).forEach { block ->
            when (block) {
                is Block.Text -> Text(block.text, style = MaterialTheme.typography.bodyLarge, color = Ink)
                is Block.Code -> CodeBlock(block.lang, block.code)
            }
        }
    }
}
