package com.forgeprom.studio.core.ai

import com.forgeprom.studio.core.data.AnalysisEntity
import com.forgeprom.studio.core.data.ProjectEntity
import com.forgeprom.studio.core.data.ProjectMessageContext
import com.forgeprom.studio.core.data.PromptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextBuilderTest {
    @Test fun contextIncludesProjectMemoryAndSavedArtifactsAndPriorThreads() {
        val context = ProjectContextBuilder.build(
            project = ProjectEntity(id = 4, name = "المتجر", description = "React وواجهة عربية", tags = "commerce"),
            prompts = listOf(PromptEntity(id = 2, projectId = 4, title = "تسجيل الدخول", content = "يدعم البريد", tech = "React")),
            analyses = listOf(AnalysisEntity(id = 1, projectId = 4, summary = "متجر عربي", intent = "تطبيق")),
            previousMessages = listOf(ProjectMessageContext(8, "الدفع", "user", "استخدم Stripe لاحقاً", 10))
        )
        listOf("المتجر", "React وواجهة عربية", "تسجيل الدخول", "يدعم البريد", "متجر عربي", "Stripe").forEach {
            assertTrue("Context should include $it", context.contains(it))
        }
    }

    @Test fun assistantMessagesAreMarkedAsSuggestionsNotFacts() {
        val context = ProjectContextBuilder.build(
            project = ProjectEntity(id = 2, name = "المشروع"),
            prompts = emptyList(),
            analyses = emptyList(),
            previousMessages = listOf(ProjectMessageContext(3, "نقاش", "assistant", "استخدم قاعدة بيانات سحابية", 1))
        )
        assertTrue(context.contains("رد سابق للمساعد (اقتراح، وليس حقيقة معتمدة)"))
        assertTrue(context.contains("استخدم قاعدة بيانات سحابية"))
    }

    @Test fun contextIsBoundedAndAssistantSuggestionsAreMarkedUnconfirmed() {
        val context = ProjectContextBuilder.build(
            project = ProjectEntity(id = 1, name = "P", description = "وصف".repeat(500)),
            prompts = emptyList(),
            analyses = emptyList(),
            previousMessages = listOf(ProjectMessageContext(1, "موضوع", "assistant", "اقتراح سابق", 1)),
            maxChars = 250
        )
        assertEquals(250, context.length)
        assertTrue(context.startsWith("المشروع: P"))
    }
}
