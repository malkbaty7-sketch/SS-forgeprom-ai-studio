package com.forgeprom.studio.core.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.io.ByteArrayOutputStream

object PdfExcerpt {
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun read(context: Context, input: InputStream, limit: Int): String {
        val bytes = ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(bytes).use { document ->
            require(!document.isEncrypted)
            PDFTextStripper().apply {
                startPage = 1
                endPage = 5
                sortByPosition = true
            }.getText(document).trim().take(limit)
        }
    }
}
