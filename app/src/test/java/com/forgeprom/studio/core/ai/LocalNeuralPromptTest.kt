package com.forgeprom.studio.core.ai

import com.forgeprom.studio.core.settings.PromptLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNeuralPromptTest {
    @Test fun generalPromptIncludesLanguageAndContext() {
        val prompt = localNeuralPrompt(GenerationRequest(
            instruction = "Build an editor", goal = "Draft stories", tech = "Kotlin",
            projectContext = "Works offline", promptLanguage = PromptLanguage.DUAL
        ))
        listOf("Arabic and English", "Build an editor", "Draft stories", "Kotlin", "Works offline").forEach {
            assertTrue(prompt.contains(it))
        }
    }

    @Test fun scenePromptUsesSceneInstructionsNotGeneralTemplate() {
        val request = GenerationRequest("Two characters meet", "", "", cinematicScene = true)
        val prompt = localNeuralPrompt(request)
        assertTrue(prompt.startsWith(requireNotNull(request.cinematicSystem)))
        assertFalse(prompt.contains("coding assistant"))
    }
}
