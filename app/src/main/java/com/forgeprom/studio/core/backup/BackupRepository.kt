package com.forgeprom.studio.core.backup

import androidx.room.withTransaction
import com.forgeprom.studio.core.data.AnalysisEntity
import com.forgeprom.studio.core.data.EditorFileEntity
import com.forgeprom.studio.core.data.ForgepromDatabase
import com.forgeprom.studio.core.data.ImageAnalysisEntity
import com.forgeprom.studio.core.data.ProjectEntity
import com.forgeprom.studio.core.data.PromptEntity
import com.forgeprom.studio.core.data.ProjectConversationEntity
import com.forgeprom.studio.core.data.ProjectMessageEntity
import com.forgeprom.studio.domain.ArabicSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

/** نتيجة الاستعادة: ما أُضيف فعلاً وما تُجوهل لأنه موجود مسبقاً. */
data class RestoreReport(
    val projectsRestored: Int = 0,
    val promptsRestored: Int = 0,
    val attachmentsRestored: Int = 0,
    val projectsSkipped: Int = 0
) {
    val totalRestored: Int get() = projectsRestored + promptsRestored + attachmentsRestored
}

/**
 * نسخ احتياطي محلي كامل (تصدير/استعادة) بصيغة JSON.
 *
 * الاستعادة **دمج فقط**: لا تحذف شيئاً. تُتجاهل المشاريع الموجودة مسبقاً
 * (نفس الاسم وتاريخ الإنشاء) مع ملحقاتها، وتُعاد تسمية المعرّفات الداخلية
 * كي لا تتصادم مع بيانات المشاريع الحالية.
 */
class BackupRepository(private val db: ForgepromDatabase) {

    suspend fun exportJson(exportedAt: String = OffsetDateTime.now().toString()): String =
        withContext(Dispatchers.IO) {
            val document = BackupDocument(
                exportedAt = exportedAt,
                projects = db.projectDao().allOnce().map { it.toBackup() },
                prompts = db.promptDao().allOnce().map { it.toBackup() },
                analyses = db.analysisDao().allOnce().map { it.toBackup() },
                editorFiles = db.editorFileDao().allOnce().map { it.toBackup() },
                imageAnalyses = db.imageAnalysisDao().allOnce().map { it.toBackup() },
                conversations = db.projectConversationDao().allOnce().map { it.toBackup() },
                messages = db.projectMessageDao().allOnce().map { it.toBackup() }
            )
            document.toJson()
        }

    suspend fun restoreJson(text: String): RestoreReport = withContext(Dispatchers.IO) {
        val document = BackupDocument.fromJson(text)
        var projectsRestored = 0
        var promptsRestored = 0
        var attachmentsRestored = 0
        var projectsSkipped = 0

        db.withTransaction {
            val existingByKey = db.projectDao().allOnce()
                .associateBy { projectKey(it.name, it.createdAt) }
            val idMap = HashMap<Long, Long>()

            for (project in document.projects) {
                val match = existingByKey[projectKey(project.name, project.createdAt)]
                if (match != null) {
                    idMap[project.id] = match.id
                    projectsSkipped++
                    continue
                }
                val now = System.currentTimeMillis()
                val newId = db.projectDao().insert(
                    ProjectEntity(
                        name = project.name,
                        description = project.description,
                        kind = project.kind,
                        tags = project.tags,
                        isPinned = project.isPinned,
                        isArchived = project.isArchived,
                        createdAt = project.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = project.updatedAt.takeIf { it > 0 } ?: now
                    )
                )
                idMap[project.id] = newId
                projectsRestored++
            }

            for (prompt in document.prompts) {
                val projectId = idMap[prompt.projectId] ?: continue
                val now = System.currentTimeMillis()
                db.promptDao().insert(
                    PromptEntity(
                        projectId = projectId,
                        title = prompt.title,
                        content = prompt.content,
                        language = prompt.language,
                        qualityScore = prompt.qualityScore,
                        goal = prompt.goal,
                        tech = prompt.tech,
                        isImageInspired = prompt.isImageInspired,
                        createdAt = prompt.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = prompt.updatedAt.takeIf { it > 0 } ?: now
                    )
                )
                promptsRestored++
            }

            for (analysis in document.analyses) {
                val projectId = idMap[analysis.projectId] ?: continue
                db.analysisDao().insert(
                    AnalysisEntity(
                        projectId = projectId,
                        summary = analysis.summary,
                        intent = analysis.intent,
                        goalsJson = analysis.goalsJson,
                        featuresJson = analysis.featuresJson,
                        requirementsJson = analysis.requirementsJson,
                        constraintsJson = analysis.constraintsJson,
                        assumptionsJson = analysis.assumptionsJson,
                        questionsJson = analysis.questionsJson,
                        createdAt = analysis.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
                attachmentsRestored++
            }

            for (file in document.editorFiles) {
                val projectId = idMap[file.projectId] ?: continue
                val now = System.currentTimeMillis()
                db.editorFileDao().insert(
                    EditorFileEntity(
                        projectId = projectId,
                        fileName = file.fileName,
                        language = file.language,
                        content = file.content,
                        createdAt = file.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = file.updatedAt.takeIf { it > 0 } ?: now
                    )
                )
                attachmentsRestored++
            }

            for (image in document.imageAnalyses) {
                val projectId = idMap[image.projectId] ?: continue
                db.imageAnalysisDao().insert(
                    ImageAnalysisEntity(
                        projectId = projectId,
                        screenType = image.screenType,
                        componentsJson = image.componentsJson,
                        colorsJson = image.colorsJson,
                        layout = image.layout,
                        statesJson = image.statesJson,
                        assumptionsJson = image.assumptionsJson,
                        confidence = image.confidence,
                        createdAt = image.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
                attachmentsRestored++
            }

            val conversationIds = HashMap<Long, Long>()
            for (conversation in document.conversations) {
                val projectId = idMap[conversation.projectId] ?: continue
                val now = System.currentTimeMillis()
                val newId = db.projectConversationDao().insert(
                    ProjectConversationEntity(
                        projectId = projectId,
                        title = conversation.title,
                        createdAt = conversation.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = conversation.updatedAt.takeIf { it > 0 } ?: now
                    )
                )
                conversationIds[conversation.id] = newId
                attachmentsRestored++
            }
            for (message in document.messages) {
                val conversationId = conversationIds[message.conversationId] ?: continue
                if (message.role !in setOf("user", "assistant")) continue
                db.projectMessageDao().insert(
                    ProjectMessageEntity(
                        conversationId = conversationId,
                        role = message.role,
                        content = message.content,
                        createdAt = message.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
                attachmentsRestored++
            }
        }

        RestoreReport(
            projectsRestored = projectsRestored,
            promptsRestored = promptsRestored,
            attachmentsRestored = attachmentsRestored,
            projectsSkipped = projectsSkipped
        )
    }

    private fun projectKey(name: String, createdAt: Long): String =
        ArabicSearch.normalize(name).trim() + "|" + createdAt
}

private fun ProjectEntity.toBackup(): BackupProject = BackupProject(
    id = id, name = name, description = description, kind = kind, tags = tags,
    isPinned = isPinned, isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt
)

private fun PromptEntity.toBackup(): BackupPrompt = BackupPrompt(
    id = id, projectId = projectId, title = title, content = content, language = language,
    qualityScore = qualityScore, goal = goal, tech = tech, isImageInspired = isImageInspired,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun AnalysisEntity.toBackup(): BackupAnalysis = BackupAnalysis(
    id = id, projectId = projectId, summary = summary, intent = intent, goalsJson = goalsJson,
    featuresJson = featuresJson, requirementsJson = requirementsJson, constraintsJson = constraintsJson,
    assumptionsJson = assumptionsJson, questionsJson = questionsJson, createdAt = createdAt
)

private fun EditorFileEntity.toBackup(): BackupEditorFile = BackupEditorFile(
    id = id, projectId = projectId, fileName = fileName, language = language,
    content = content, createdAt = createdAt, updatedAt = updatedAt
)

private fun ImageAnalysisEntity.toBackup(): BackupImageAnalysis = BackupImageAnalysis(
    id = id, projectId = projectId, screenType = screenType, componentsJson = componentsJson,
    colorsJson = colorsJson, layout = layout, statesJson = statesJson,
    assumptionsJson = assumptionsJson, confidence = confidence, createdAt = createdAt
)

private fun ProjectConversationEntity.toBackup(): BackupConversation = BackupConversation(
    id = id, projectId = projectId, title = title, createdAt = createdAt, updatedAt = updatedAt
)

private fun ProjectMessageEntity.toBackup(): BackupMessage = BackupMessage(
    id = id, conversationId = conversationId, role = role, content = content, createdAt = createdAt
)
