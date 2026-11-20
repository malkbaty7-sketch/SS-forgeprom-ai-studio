package com.forgeprom.studio.domain

import org.junit.Assert.*
import org.junit.Test

class SceneDraftTest {
    @Test fun buildsSceneRequestWithOptionalNotes() {
        val brief = "تنتظر سلمى القطار"
        val notes = "سلمى لا تعرف المدينة"
        assertFalse(SceneDraft.request(brief).instruction.contains(notes))
        val request = SceneDraft.request(brief, notes)
        assertTrue(request.instruction.contains(notes))
        assertEquals(SceneDraft.SYSTEM, request.cinematicSystem)
    }

    @Test fun appliesDirectorSettingsToTheRequest() {
        val request = SceneDraft.request("رصيف خالٍ", settings = SceneDirection(9, SceneTone.TENSE, camera = true))
        assertTrue(request.instruction.contains("pace 9/10"))
        assertTrue(request.instruction.contains(SceneTone.TENSE.guidance))
        assertTrue(request.instruction.contains("Include concise shot directions"))
    }

    @Test fun includesStyleLightingAndActorCues() {
        val options = SceneDirection(style = SceneStyle.VISUAL, lighting = SceneLighting.LOW_KEY, actorCues = true)
        val instruction = SceneDraft.request("ممر", settings = options).instruction
        assertTrue(instruction.contains(SceneStyle.VISUAL.guidance))
        assertTrue(instruction.contains(SceneLighting.LOW_KEY.guidance))
        assertTrue(instruction.contains("Add brief performance cues"))
    }

    @Test fun selectsFormatSpecificInstructionsAndValidation() {
        val request = SceneDraft.request("مشهد", format = SceneFormat.SHOTS)
        assertTrue(request.cinematicSystem.orEmpty().contains(SceneFormat.SHOTS.rules))
        val shots = "1. لقطة واسعة\n2. لقطة قريبة"
        assertEquals(shots, SceneDraft.validate(shots, SceneFormat.SHOTS))
        assertTrue(runCatching { SceneDraft.validate(shots) }.isFailure)
        val prose = "تتسلل أشعة الشمس إلى الممر."
        assertEquals(prose, SceneDraft.validate(prose, SceneFormat.PROSE))
    }

    @Test fun acceptsArabicForcedHeading() {
        val draft = ".المحطة - ليلاً\n\nتتوقف سلمى عند الرصيف.\n\n@سلمى\nهل تأخر القطار؟"
        assertEquals(draft, SceneDraft.validate(draft))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMarkdownFences() {
        SceneDraft.validate("```fountain\n.المحطة - ليلاً\nتتوقف سلمى.\n```")
    }
}
