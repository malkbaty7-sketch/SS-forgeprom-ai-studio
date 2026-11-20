package com.forgeprom.studio.core.editor

import java.util.Base64

/** مسودة تحرير غير محفوظة في قاعدة البيانات. */
data class EditorDraft(
    val title: String,
    val content: String,
    val savedAt: Long = 0L
)

/**
 * ترميز المسودة لتخزينها في SharedPreferences.
 *
 * الصيغة: إصدار + وقت الحفظ + العنوان base64 + النص base64.
 * الترميز يضمن بقاء الأسطر الجديدة والرموز العربية والرموز الخاصة سليمة.
 */
object DraftCodec {

    private const val VERSION = "1"
    private const val SEPARATOR = "\n"
    const val MAX_CONTENT_CHARS = 400_000

    fun encode(draft: EditorDraft): String = listOf(
        VERSION,
        draft.savedAt.toString(),
        encodeText(draft.title),
        encodeText(draft.content)
    ).joinToString(SEPARATOR)

    fun decode(raw: String?): EditorDraft? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != VERSION) return null
        val savedAt = parts[1].toLongOrNull() ?: return null
        val title = decodeText(parts[2]) ?: return null
        val content = decodeText(parts[3]) ?: return null
        if (content.length > MAX_CONTENT_CHARS) return null
        return EditorDraft(title = title, content = content, savedAt = savedAt)
    }

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()
}
