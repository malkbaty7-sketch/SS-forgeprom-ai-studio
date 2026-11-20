package com.forgeprom.studio.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptFileTest {

    private fun sample() = PromptFileData(
        title = "متجر «عربي»",
        content = "الهدف:\nمتجر إلكتروني\n\"قيود\" و\\رموز\\",
        language = "ar",
        tags = "تصميم, تجارة",
        goal = "تطبيق كامل",
        tech = "Kotlin Compose",
        exportedAt = "2026-11-20T10:15:30Z"
    )

    @Test
    fun `round trip preserves arabic text and metadata`() {
        val decoded = PromptFile.decode(PromptFile.encode(sample()))
        assertEquals(sample(), decoded)
    }

    @Test
    fun `decode fills safe defaults for optional metadata`() {
        val minimal = SimpleJson.stringify(
            mapOf("format" to PromptFile.FORMAT, "version" to 1, "content" to "نص")
        )
        val decoded = PromptFile.decode(minimal)
        assertEquals("موجّه مستورد", decoded.title)
        assertEquals("ar", decoded.language)
        assertEquals("", decoded.tags)
    }

    @Test
    fun `blank fields receive defaults without failing`() {
        val decoded = PromptFile.decode(PromptFile.encode(PromptFileData(title = "", content = "")))
        assertEquals("موجّه مستورد", decoded.title)
        assertEquals("ar", decoded.language)
        assertEquals("", decoded.content)
        assertEquals("", decoded.exportedAt)
    }

    @Test
    fun `rejects other formats and versions`() {
        val wrongFormat = SimpleJson.stringify(mapOf("format" to "other", "version" to 1))
        assertTrue(runCatching { PromptFile.decode(wrongFormat) }.isFailure)
        val wrongVersion = SimpleJson.stringify(mapOf("format" to PromptFile.FORMAT, "version" to 7))
        assertTrue(runCatching { PromptFile.decode(wrongVersion) }.isFailure)
    }

    @Test
    fun `rejects oversized content`() {
        val huge = sample().copy(content = "ا".repeat(PromptFile.MAX_CONTENT_CHARS + 1))
        assertTrue(runCatching { PromptFile.decode(PromptFile.encode(huge)) }.isFailure)
    }
}
