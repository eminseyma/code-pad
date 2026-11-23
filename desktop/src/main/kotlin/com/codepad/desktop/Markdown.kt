package com.codepad.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private val CodeBg = Color(0xFF15231C)
private val CodeText = Color(0xFFD9EFE2)
private val CodeMint = Color(0xFF8CCBA9)
private val CodeComment = Color(0xFF7FA08F)
private val CodeString = Color(0xFFE6C98F)

private sealed interface MdBlock {
    data class Text(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
}

private fun splitBlocks(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val buf = StringBuilder()
    var inCode = false
    var lang = ""
    for (line in src.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out += MdBlock.Code(lang, buf.toString().trimEnd())
                buf.clear()
                inCode = false
            } else {
                if (buf.isNotBlank()) out += MdBlock.Text(buf.toString().trimEnd())
                buf.clear()
                lang = line.trim().removePrefix("```").trim()
                inCode = true
            }
        } else {
            buf.appendLine(line)
        }
    }
    if (buf.isNotBlank()) {
        out += if (inCode) MdBlock.Code(lang, buf.toString().trimEnd()) else MdBlock.Text(buf.toString().trimEnd())
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

/** Basit, bağımlılıksız sözdizimi renklendirme (telefondakiyle aynı). */
private fun highlight(code: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in TokenRegex.findAll(code)) {
        append(code.substring(last, m.range.first))
        val t = m.value
        val color = when {
            t.startsWith("//") || t.startsWith("#") -> CodeComment
            t.startsWith("\"") -> CodeString
            t in Keywords -> CodeMint
            else -> null
        }
        if (color != null) withStyle(SpanStyle(color = color)) { append(t) } else append(t)
        last = m.range.last + 1
    }
    append(code.substring(last))
}

private val InlineRegex = Regex("""\*\*(.+?)\*\*|`([^`]+)`|(?<![\w#])#[\p{L}][\p{L}\p{N}_-]{1,29}""")

private fun inline(s: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in InlineRegex.findAll(s)) {
        append(s.substring(last, m.range.first))
        val bold = m.groups[1]?.value
        val code = m.groups[2]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = accent.copy(alpha = 0.14f))) { append(code) }
            else -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(m.value) }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

@Composable
private fun CodeBlockDesk(lang: String, code: String) {
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CodeBg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lang.ifBlank { "kod" }, color = CodeMint, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
            GButton(onClick = {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                copied = true
            }) { Text(if (copied) "Kopyalandı ✓" else "Kopyala", fontSize = 12.sp) }
        }
        Text(
            highlight(code),
            color = CodeText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun MdText(text: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val t = line.trimStart()
            when {
                t.isBlank() -> Spacer(Modifier.height(6.dp))
                t.startsWith("### ") -> Text(inline(t.drop(4), cs.primary), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                t.startsWith("## ") -> Text(inline(t.drop(3), cs.primary), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary, modifier = Modifier.padding(top = 8.dp))
                t.startsWith("# ") -> Text(inline(t.drop(2), cs.primary), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
                t.startsWith("- [ ] ") -> Row { Text("☐  ", color = cs.primary); Text(inline(t.drop(6), cs.primary), fontSize = 14.sp, lineHeight = 21.sp) }
                t.startsWith("- [x] ") || t.startsWith("- [X] ") ->
                    Row { Text("☑  ", color = cs.primary); Text(inline(t.drop(6), cs.primary), fontSize = 14.sp, lineHeight = 21.sp) }
                t.startsWith("- ") || t.startsWith("* ") ->
                    Row { Text("•  ", color = cs.primary, fontWeight = FontWeight.Bold); Text(inline(t.drop(2), cs.primary), fontSize = 14.sp, lineHeight = 21.sp) }
                else -> Text(inline(line, cs.primary), fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    }
}

/** Notu okunur biçimde gösterir: başlıklar, listeler, #etiketler ve kopyalanabilir kod blokları. */
@Composable
fun MarkdownView(src: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Box(modifier) {
        SelectionContainer {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (src.isBlank()) {
                    Text("Önizlenecek bir şey yok. Düzenleme moduna dönüp yazmaya başla.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                splitBlocks(src).forEach { b ->
                    when (b) {
                        is MdBlock.Code -> CodeBlockDesk(b.lang, b.code)
                        is MdBlock.Text -> MdText(b.text)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
