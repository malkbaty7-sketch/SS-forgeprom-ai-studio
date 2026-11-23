package com.forgeprom.studio.core.backup

/** حدود صريحة تمنع ملفات ضخمة أو مشوّهة من إغراق الذاكرة أو قاعدة البيانات. */
object BackupRules {
    const val FORMAT = "forgeprom.backup"
    const val VERSION = 2
    const val PREVIOUS_VERSION = 1
    const val MAX_FILE_CHARS = 8 * 1024 * 1024
    const val MAX_PROJECTS = 2_000
    const val MAX_PROMPTS = 20_000
    const val MAX_CHILDREN = 20_000
    const val MAX_CONVERSATIONS = 20_000
    const val MAX_MESSAGES = 100_000
    const val MAX_NAME_CHARS = 200
    const val MAX_TEXT_CHARS = 400_000
    const val MAX_JSON_CHARS = 400_000
}

data class BackupProject(
    val id: Long,
    val name: String,
    val description: String = "",
    val kind: String = "prompt",
    val tags: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class BackupPrompt(
    val id: Long,
    val projectId: Long,
    val title: String,
    val content: String,
    val language: String = "ar",
    val qualityScore: Int = 0,
    val goal: String = "",
    val tech: String = "",
    val isImageInspired: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class BackupAnalysis(
    val id: Long,
    val projectId: Long,
    val summary: String,
    val intent: String = "",
    val goalsJson: String = "[]",
    val featuresJson: String = "[]",
    val requirementsJson: String = "[]",
    val constraintsJson: String = "[]",
    val assumptionsJson: String = "[]",
    val questionsJson: String = "[]",
    val createdAt: Long = 0L
)

data class BackupEditorFile(
    val id: Long,
    val projectId: Long,
    val fileName: String,
    val language: String = "plaintext",
    val content: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class BackupImageAnalysis(
    val id: Long,
    val projectId: Long,
    val screenType: String,
    val componentsJson: String = "[]",
    val colorsJson: String = "{}",
    val layout: String = "",
    val statesJson: String = "[]",
    val assumptionsJson: String = "[]",
    val confidence: Float = 0f,
    val createdAt: Long = 0L
)

data class BackupConversation(
    val id: Long,
    val projectId: Long,
    val title: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class BackupMessage(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = 0L
)

/**
 * مستند النسخ الاحتياطي — يعرض مشاريع المستخدم وموجّاته وملحقاتها.
 *
 * لا يحتوي مفاتيح API ولا إعدادات الجهاز ولا مسودات المحرر غير المحفوظة.
 */
data class BackupDocument(
    val exportedAt: String,
    val projects: List<BackupProject> = emptyList(),
    val prompts: List<BackupPrompt> = emptyList(),
    val analyses: List<BackupAnalysis> = emptyList(),
    val editorFiles: List<BackupEditorFile> = emptyList(),
    val imageAnalyses: List<BackupImageAnalysis> = emptyList(),
    val conversations: List<BackupConversation> = emptyList(),
    val messages: List<BackupMessage> = emptyList()
) {

    val isEmpty: Boolean
        get() = projects.isEmpty() && prompts.isEmpty() && analyses.isEmpty() &&
            editorFiles.isEmpty() && imageAnalyses.isEmpty() && conversations.isEmpty() && messages.isEmpty()

    fun toJson(): String = SimpleJson.stringify(
        linkedMapOf<String, Any?>(
            "format" to BackupRules.FORMAT,
            "version" to BackupRules.VERSION,
            "exportedAt" to exportedAt,
            "projects" to projects.map { it.toMap() },
            "prompts" to prompts.map { it.toMap() },
            "analyses" to analyses.map { it.toMap() },
            "editorFiles" to editorFiles.map { it.toMap() },
            "imageAnalyses" to imageAnalyses.map { it.toMap() },
            "conversations" to conversations.map { it.toMap() },
            "messages" to messages.map { it.toMap() }
        )
    )

    companion object {

        fun fromJson(text: String): BackupDocument {
            require(text.length <= BackupRules.MAX_FILE_CHARS) { "Backup file is too large" }
            val root = SimpleJson.parse(text) as? Map<*, *>
                ?: throw IllegalArgumentException("Backup root must be a JSON object")
            val format = root["format"] as? String
            require(format == BackupRules.FORMAT) { "Not a Forgeprom backup file" }
            val version = root.longField("version", 0L)
            require(version in BackupRules.PREVIOUS_VERSION.toLong()..BackupRules.VERSION.toLong()) {
                "Unsupported backup version: $version"
            }

            val projectsRaw = root.listField("projects")
            val promptsRaw = root.listField("prompts")
            val analysesRaw = root.listField("analyses")
            val editorFilesRaw = root.listField("editorFiles")
            val imageAnalysesRaw = root.listField("imageAnalyses")
            val conversationsRaw = root.listField("conversations")
            val messagesRaw = root.listField("messages")
            require(projectsRaw.size <= BackupRules.MAX_PROJECTS) { "Too many projects" }
            require(promptsRaw.size <= BackupRules.MAX_PROMPTS) { "Too many prompts" }
            require(analysesRaw.size <= BackupRules.MAX_CHILDREN) { "Too many analyses" }
            require(editorFilesRaw.size <= BackupRules.MAX_CHILDREN) { "Too many editor files" }
            require(imageAnalysesRaw.size <= BackupRules.MAX_CHILDREN) { "Too many image analyses" }
            require(conversationsRaw.size <= BackupRules.MAX_CONVERSATIONS) { "Too many conversations" }
            require(messagesRaw.size <= BackupRules.MAX_MESSAGES) { "Too many messages" }

            return BackupDocument(
                exportedAt = root["exportedAt"] as? String ?: "",
                projects = projectsRaw.mapIndexed { i, item -> project(item.objectField("projects", i)) },
                prompts = promptsRaw.mapIndexed { i, item -> prompt(item.objectField("prompts", i)) },
                analyses = analysesRaw.mapIndexed { i, item -> analysis(item.objectField("analyses", i)) },
                editorFiles = editorFilesRaw.mapIndexed { i, item -> editorFile(item.objectField("editorFiles", i)) },
                imageAnalyses = imageAnalysesRaw.mapIndexed { i, item ->
                    imageAnalysis(item.objectField("imageAnalyses", i))
                },
                conversations = conversationsRaw.mapIndexed { i, item ->
                    conversation(item.objectField("conversations", i))
                },
                messages = messagesRaw.mapIndexed { i, item ->
                    message(item.objectField("messages", i))
                }
            )
        }

        private fun project(m: Map<String, Any?>): BackupProject {
            val id = m.longField("id", 0L)
            return BackupProject(
                id = id,
                name = m.textField("name", BackupRules.MAX_NAME_CHARS),
                description = m.textField("description", BackupRules.MAX_TEXT_CHARS),
                kind = m.textField("kind", 32, "prompt"),
                tags = m.textField("tags", 500),
                isPinned = m.boolField("isPinned"),
                isArchived = m.boolField("isArchived"),
                createdAt = m.longField("createdAt", 0L),
                updatedAt = m.longField("updatedAt", 0L)
            )
        }

        private fun prompt(m: Map<String, Any?>): BackupPrompt = BackupPrompt(
            id = m.longField("id", 0L),
            projectId = m.longField("projectId", 0L),
            title = m.textField("title", BackupRules.MAX_NAME_CHARS),
            content = m.textField("content", BackupRules.MAX_TEXT_CHARS),
            language = m.textField("language", 16, "ar"),
            qualityScore = m.longField("qualityScore", 0L).coerceIn(0L, 100L).toInt(),
            goal = m.textField("goal", BackupRules.MAX_TEXT_CHARS),
            tech = m.textField("tech", BackupRules.MAX_TEXT_CHARS),
            isImageInspired = m.boolField("isImageInspired"),
            createdAt = m.longField("createdAt", 0L),
            updatedAt = m.longField("updatedAt", 0L)
        )

        private fun analysis(m: Map<String, Any?>): BackupAnalysis = BackupAnalysis(
            id = m.longField("id", 0L),
            projectId = m.longField("projectId", 0L),
            summary = m.textField("summary", BackupRules.MAX_TEXT_CHARS),
            intent = m.textField("intent", BackupRules.MAX_NAME_CHARS),
            goalsJson = m.textField("goalsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            featuresJson = m.textField("featuresJson", BackupRules.MAX_JSON_CHARS, "[]"),
            requirementsJson = m.textField("requirementsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            constraintsJson = m.textField("constraintsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            assumptionsJson = m.textField("assumptionsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            questionsJson = m.textField("questionsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            createdAt = m.longField("createdAt", 0L)
        )

        private fun editorFile(m: Map<String, Any?>): BackupEditorFile = BackupEditorFile(
            id = m.longField("id", 0L),
            projectId = m.longField("projectId", 0L),
            fileName = m.textField("fileName", BackupRules.MAX_NAME_CHARS),
            language = m.textField("language", 32, "plaintext"),
            content = m.textField("content", BackupRules.MAX_TEXT_CHARS),
            createdAt = m.longField("createdAt", 0L),
            updatedAt = m.longField("updatedAt", 0L)
        )

        private fun imageAnalysis(m: Map<String, Any?>): BackupImageAnalysis = BackupImageAnalysis(
            id = m.longField("id", 0L),
            projectId = m.longField("projectId", 0L),
            screenType = m.textField("screenType", BackupRules.MAX_NAME_CHARS),
            componentsJson = m.textField("componentsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            colorsJson = m.textField("colorsJson", BackupRules.MAX_JSON_CHARS, "{}"),
            layout = m.textField("layout", BackupRules.MAX_TEXT_CHARS),
            statesJson = m.textField("statesJson", BackupRules.MAX_JSON_CHARS, "[]"),
            assumptionsJson = m.textField("assumptionsJson", BackupRules.MAX_JSON_CHARS, "[]"),
            confidence = m.doubleField("confidence", 0.0).coerceIn(0.0, 1.0).toFloat(),
            createdAt = m.longField("createdAt", 0L)
        )

        private fun conversation(m: Map<String, Any?>): BackupConversation = BackupConversation(
            id = m.longField("id", 0L),
            projectId = m.longField("projectId", 0L),
            title = m.textField("title", BackupRules.MAX_NAME_CHARS),
            createdAt = m.longField("createdAt", 0L),
            updatedAt = m.longField("updatedAt", 0L)
        )

        private fun message(m: Map<String, Any?>): BackupMessage = BackupMessage(
            id = m.longField("id", 0L),
            conversationId = m.longField("conversationId", 0L),
            role = m.textField("role", 16),
            content = m.textField("content", BackupRules.MAX_TEXT_CHARS),
            createdAt = m.longField("createdAt", 0L)
        )
    }
}

private fun BackupProject.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "name" to name, "description" to description, "kind" to kind, "tags" to tags,
    "isPinned" to isPinned, "isArchived" to isArchived, "createdAt" to createdAt, "updatedAt" to updatedAt
)

private fun BackupPrompt.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "projectId" to projectId, "title" to title, "content" to content,
    "language" to language, "qualityScore" to qualityScore, "goal" to goal, "tech" to tech,
    "isImageInspired" to isImageInspired, "createdAt" to createdAt, "updatedAt" to updatedAt
)

private fun BackupAnalysis.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "projectId" to projectId, "summary" to summary, "intent" to intent,
    "goalsJson" to goalsJson, "featuresJson" to featuresJson, "requirementsJson" to requirementsJson,
    "constraintsJson" to constraintsJson, "assumptionsJson" to assumptionsJson,
    "questionsJson" to questionsJson, "createdAt" to createdAt
)

private fun BackupEditorFile.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "projectId" to projectId, "fileName" to fileName, "language" to language,
    "content" to content, "createdAt" to createdAt, "updatedAt" to updatedAt
)

private fun BackupImageAnalysis.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "projectId" to projectId, "screenType" to screenType,
    "componentsJson" to componentsJson, "colorsJson" to colorsJson, "layout" to layout,
    "statesJson" to statesJson, "assumptionsJson" to assumptionsJson,
    "confidence" to confidence, "createdAt" to createdAt
)

private fun BackupConversation.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "projectId" to projectId, "title" to title,
    "createdAt" to createdAt, "updatedAt" to updatedAt
)

private fun BackupMessage.toMap(): Map<String, Any?> = linkedMapOf(
    "id" to id, "conversationId" to conversationId, "role" to role,
    "content" to content, "createdAt" to createdAt
)

private fun Any?.objectField(field: String, index: Int): Map<String, Any?> {
    val map = this as? Map<*, *> ?: throw IllegalArgumentException("$field[$index] must be an object")
    @Suppress("UNCHECKED_CAST")
    return map as Map<String, Any?>
}

private fun Map<*, *>.listField(key: String): List<Any?> {
    val value = this[key] ?: return emptyList()
    return value as? List<*> ?: throw IllegalArgumentException("'$key' must be an array")
}

private fun Map<*, *>.textField(key: String, max: Int, default: String = ""): String {
    val value = this[key] ?: return default
    val text = value as? String ?: throw IllegalArgumentException("'$key' must be a string")
    require(text.length <= max) { "'$key' is too long" }
    return text
}

private fun Map<*, *>.longField(key: String, default: Long): Long {
    val value = this[key] ?: return default
    val number = value as? Number ?: throw IllegalArgumentException("'$key' must be a number")
    return number.toLong()
}

private fun Map<*, *>.doubleField(key: String, default: Double): Double {
    val value = this[key] ?: return default
    val number = value as? Number ?: throw IllegalArgumentException("'$key' must be a number")
    return number.toDouble()
}

private fun Map<*, *>.boolField(key: String, default: Boolean = false): Boolean {
    val value = this[key] ?: return default
    return value as? Boolean ?: throw IllegalArgumentException("'$key' must be a boolean")
}
