package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplatesTest {

    @Test
    fun `template ids are unique and every field is usable`() {
        val ids = PromptTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(PromptTemplates.all.size >= 6)
        PromptTemplates.all.forEach { template ->
            assertTrue(template.title.isNotBlank())
            assertTrue(template.goal.isNotBlank())
            assertTrue(template.tech.isNotBlank())
            assertTrue(template.idea.length > 40)
        }
    }

    @Test
    fun `lookup returns the template or null`() {
        assertEquals("متجر إلكتروني", PromptTemplates.byId("store")?.title)
        assertNull(PromptTemplates.byId("missing"))
    }

    @Test
    fun `template ideas are analyzable locally`() {
        PromptTemplates.all.forEach { template ->
            val draft = IdeaAnalyzer.analyze(template.idea, template.goal, template.tech)
            assertTrue(draft.summary.isNotBlank())
            assertTrue(draft.questions.size <= 3)
        }
    }
}
