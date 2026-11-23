package com.forgeprom.studio.core.ai

import com.forgeprom.studio.core.data.AnalysisEntity
import com.forgeprom.studio.core.data.EditorFileEntity
import com.forgeprom.studio.core.data.ImageAnalysisEntity
import com.forgeprom.studio.core.data.ProjectEntity
import com.forgeprom.studio.core.data.ProjectMessageContext
import com.forgeprom.studio.core.data.PromptEntity

/** يبني سياقاً مشتركاً محدود الحجم من ذاكرة المشروع وآثاره ومحادثاته السابقة. */
internal object ProjectContextBuilder {
    const val MAX_CONTEXT_CHARS = 7_000

    fun build(
        project: ProjectEntity,
        prompts: List<PromptEntity>,
        analyses: List<AnalysisEntity>,
        previousMessages: List<ProjectMessageContext>,
        editorFiles: List<EditorFileEntity> = emptyList(),
        imageAnalyses: List<ImageAnalysisEntity> = emptyList(),
        maxChars: Int = MAX_CONTEXT_CHARS
    ): String {
        val sections = buildList {
            add("المشروع: ${project.name}")
            if (project.description.isNotBlank()) add("وصف/ذاكرة المشروع المعتمدة:\n${project.description.trim()}")
            if (project.tags.isNotBlank()) add("وسوم المشروع: ${project.tags}")
            prompts.sortedByDescending { it.updatedAt }.take(4).forEach { prompt ->
                val text = buildString {
                    append("موجّه محفوظ: ${prompt.title}")
                    if (prompt.goal.isNotBlank()) append("\nالهدف: ${prompt.goal}")
                    if (prompt.tech.isNotBlank()) append("\nالتقنية: ${prompt.tech}")
                    append("\nالمحتوى:\n${prompt.content.take(1_200)}")
                }
                add(text)
            }
            analyses.sortedByDescending { it.createdAt }.take(3).forEach { analysis ->
                add(buildString {
                    append("تحليل محفوظ: ${analysis.summary.take(500)}")
                    if (analysis.intent.isNotBlank()) append("\nالنية: ${analysis.intent}")
                    val requirements = listOf(analysis.goalsJson, analysis.featuresJson, analysis.requirementsJson,
                        analysis.constraintsJson, analysis.assumptionsJson)
                        .filter { it != "[]" && it.isNotBlank() }
                    if (requirements.isNotEmpty()) append("\nمتطلبات/قرارات مسجلة: ${requirements.joinToString(" | ").take(900)}")
                })
            }
            editorFiles.sortedByDescending { it.updatedAt }.take(3).forEach { file ->
                add("ملف محفوظ: ${file.fileName} (${file.language})\n${file.content.take(1_000)}")
            }
            imageAnalyses.sortedByDescending { it.createdAt }.take(3).forEach { image ->
                add("تحليل صورة محفوظ: ${image.screenType}\nالتخطيط: ${image.layout.take(350)}\nالعناصر: ${image.componentsJson.take(450)}\nالحالات: ${image.statesJson.take(300)}")
            }
            previousMessages.takeLast(6).forEach { message ->
                add("من محادثة «${message.conversationTitle.take(80)}» — ${roleLabel(message.role)}:\n${message.content.take(500)}")
            }
        }

        val result = StringBuilder()
        for (section in sections) {
            val separator = if (result.isEmpty()) "" else "\n\n"
            val remaining = maxChars - result.length - separator.length
            if (remaining <= 0) break
            result.append(separator).append(section.take(remaining))
        }
        return result.toString()
    }

    private fun roleLabel(role: String): String = when (role) {
        "assistant" -> "رد سابق للمساعد (اقتراح، وليس حقيقة معتمدة)"
        else -> "رسالة سابقة للمستخدم (ليست بالضرورة حقيقة معتمدة)"
    }
}
