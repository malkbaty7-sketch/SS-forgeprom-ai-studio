package com.forgeprom.studio.core.util

import android.text.Html
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounded preview import; never extracts archive entries to disk. */
object EpubExcerpt {
    fun read(input: InputStream, limit: Int): String {
        val result = StringBuilder()
        ZipInputStream(input).use { archive ->
            repeat(128) {
                val entry = archive.nextEntry ?: return@use
                // Skip unrelated entries with a bounded read too: advancing a ZIP stream inflates skipped data.
                val bytes = ByteArray(256 * 1024 + 1)
                var size = 0
                while (size < bytes.size) {
                    val count = archive.read(bytes, size, bytes.size - size)
                    if (count < 0) break
                    size += count
                }
                val name = entry.name.substringAfterLast('/').lowercase()
                if (name.endsWith(".xhtml") || name.endsWith(".html")) {
                    val text = Html.fromHtml(String(bytes, 0, size, Charsets.UTF_8), Html.FROM_HTML_MODE_LEGACY)
                        .toString().trim()
                    if (text.isNotBlank()) {
                        if (result.isNotEmpty()) result.append("\n\n")
                        result.append(text.take(limit - result.length.coerceAtMost(limit)))
                    }
                }
                if (size == bytes.size || result.length >= limit) return@use
            }
        }
        return result.toString().take(limit).trim()
    }
}
