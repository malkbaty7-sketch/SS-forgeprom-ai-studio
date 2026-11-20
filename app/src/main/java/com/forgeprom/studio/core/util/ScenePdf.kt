package com.forgeprom.studio.core.util

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/** Text PDF with Android shaping and pagination at line boundaries. */
object ScenePdf {
    fun render(scene: String): ByteArray {
        val text = scene.lineSequence().joinToString("\n") { line ->
            if (line.startsWith('@') || (line.startsWith('.') && !line.startsWith(".."))) line.drop(1) else line
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length,
            TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }, 499)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(3f, 1f)
            .build()
        val document = PdfDocument()
        try {
            var firstLine = 0
            var number = 1
            while (firstLine < layout.lineCount) {
                val top = layout.getLineTop(firstLine)
                var end = firstLine + 1
                while (end < layout.lineCount && layout.getLineTop(end + 1) - top <= 746) end++
                val height = layout.getLineTop(end) - top
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number++).create())
                page.canvas.clipRect(48, 48, 547, 48 + height)
                page.canvas.translate(48f, 48f - top)
                layout.draw(page.canvas)
                document.finishPage(page)
                firstLine = end
            }
            return ByteArrayOutputStream().use { stream -> document.writeTo(stream); stream.toByteArray() }
        } finally {
            document.close()
        }
    }
}
