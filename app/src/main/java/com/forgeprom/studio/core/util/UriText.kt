package com.forgeprom.studio.core.util

import android.content.Context
import android.net.Uri

/** قراءة/كتابة نص UTF-8 عبر منتقي ملفات النظام (SAF) بحدود صريحة. */
object UriText {

    fun readLimited(context: Context, uri: Uri, maxChars: Int): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val reader = input.reader(Charsets.UTF_8)
            val buffer = CharArray(8192)
            val collected = StringBuilder()
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                collected.append(buffer, 0, read)
                if (collected.length > maxChars) return null
            }
            collected.toString()
        }
    }.getOrNull()

    fun write(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        } != null
    }.getOrDefault(false)
}
