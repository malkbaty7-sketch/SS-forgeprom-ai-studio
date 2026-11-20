package com.forgeprom.studio.core.editor

import android.content.Context

/**
 * مخزن المسودات المحلية لكل موجّه (SharedPreferences).
 *
 * الحفظ هنا لا يعدّل قاعدة البيانات ولا يُعدّ حفظاً نهائياً؛
 * إنه شبكة أمان عند إغلاق المحرر أو انقطاع التطبيق.
 */
class DraftStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(promptId: Long): EditorDraft? =
        DraftCodec.decode(prefs.getString(key(promptId), null))

    fun save(promptId: Long, title: String, content: String, savedAt: Long = System.currentTimeMillis()) {
        if (content.length > DraftCodec.MAX_CONTENT_CHARS) return
        val encoded = DraftCodec.encode(EditorDraft(title = title, content = content, savedAt = savedAt))
        prefs.edit().putString(key(promptId), encoded).apply()
    }

    fun clear(promptId: Long) {
        prefs.edit().remove(key(promptId)).apply()
    }

    private fun key(promptId: Long): String = "prompt_$promptId"

    private companion object {
        const val PREFS_NAME = "forgeprom_editor_drafts"
    }
}
