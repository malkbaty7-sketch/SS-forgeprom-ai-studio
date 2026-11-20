package com.forgeprom.studio.core.backup

/**
 * ملف موجّه واحد قابل للمشاركة بامتداد `.forgeprom`.
 *
 * يستخدم نفس ترميز JSON المكتفي بذاته في النسخ الاحتياطي، ولا يحتوي
 * مفاتيح ولا إعدادات جهاز. الاستيراد ينشئ مشروعاً جديداً وموجّهاً واحداً.
 */
data class PromptFileData(
    val title: String,
    val content: String,
    val language: String = "ar",
    val tags: String = "",
    val goal: String = "",
    val tech: String = "",
    val exportedAt: String = ""
)

object PromptFile {

    const val FORMAT = "forgeprom.prompt"
    const val VERSION = 1
    const val MAX_FILE_CHARS = 1_000_000
    const val MAX_TITLE_CHARS = 200
    const val MAX_CONTENT_CHARS = 400_000
    const val MAX_TAGS_CHARS = 500
    const val MAX_META_CHARS = 2_000

    fun encode(data: PromptFileData): String = SimpleJson.stringify(
        linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to data.exportedAt,
            "title" to data.title,
            "content" to data.content,
            "language" to data.language,
            "tags" to data.tags,
            "goal" to data.goal,
            "tech" to data.tech
        )
    )

    fun decode(text: String): PromptFileData {
        require(text.length <= MAX_FILE_CHARS) { "Prompt file is too large" }
        val root = SimpleJson.parse(text) as? Map<*, *>
            ?: throw IllegalArgumentException("Prompt file root must be an object")
        require(root["format"] as? String == FORMAT) { "Not a Forgeprom prompt file" }
        val version = (root["version"] as? Number)?.toLong() ?: 0L
        require(version == VERSION.toLong()) { "Unsupported prompt file version: $version" }
        return PromptFileData(
            title = root.text("title", MAX_TITLE_CHARS).ifBlank { "موجّه مستورد" },
            content = root.text("content", MAX_CONTENT_CHARS),
            language = root.text("language", 16, "ar").ifBlank { "ar" },
            tags = root.text("tags", MAX_TAGS_CHARS),
            goal = root.text("goal", MAX_META_CHARS),
            tech = root.text("tech", MAX_META_CHARS),
            exportedAt = root.text("exportedAt", 64)
        )
    }

    private fun Map<*, *>.text(key: String, max: Int, default: String = ""): String {
        val value = this[key] ?: return default
        val text = value as? String ?: throw IllegalArgumentException("'$key' must be a string")
        require(text.length <= max) { "'$key' is too long" }
        return text
    }
}
