package com.keepsakeai.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Minimal markdown for AI answers: headings, bullets, **bold**, *italic* and `code`. */
fun simpleMarkdown(src: String): AnnotatedString = buildAnnotatedString {
    val lines = src.replace("\r", "").lines()
    lines.forEachIndexed { idx, raw ->
        var line = raw
        var heading = false
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> { line = trimmed.trimStart('#').trim(); heading = true }
            trimmed.startsWith("* ") || trimmed.startsWith("- ") ->
                line = " ".repeat(raw.length - trimmed.length) + "•  " + trimmed.drop(2)
        }
        if (heading) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(line) } else appendInline(line)
        if (idx < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append("**"); i += 2 }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append('`'); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
