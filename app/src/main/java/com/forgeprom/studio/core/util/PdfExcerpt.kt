package com.forgeprom.studio.core.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.io.ByteArrayOutputStream

object PdfExcerpt {
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun read(context: Context, input: InputStream, limit: Int): String =
        extract(context, input, fullBook = false).trim().take(limit)

    fun readBook(context: Context, input: InputStream): List<String> =
        StoryBookParts.split(extract(context, input, fullBook = true))

    private fun extract(context: Context, input: InputStream, fullBook: Boolean): String {
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
            require(!fullBook || document.numberOfPages <= 300)
            PDFTextStripper().apply {
                startPage = 1
                endPage = if (fullBook) document.numberOfPages else 5
                sortByPosition = true
            }.getText(document)
        }
    }
}
