package com.forgeprom.studio.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sample(): BackupDocument = BackupDocument(
        exportedAt = "2026-11-20T10:15:30Z",
        projects = listOf(
            BackupProject(
                id = 7,
                name = "متجر «عربي»",
                description = "وصف بسطر\nجديد و\"اقتباس\"",
                tags = "تصميم, تجارة",
                isPinned = true,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_100_000L
            )
        ),
        prompts = listOf(
            BackupPrompt(
                id = 11,
                projectId = 7,
                title = "موجّه المتجر",
                content = "الهدف:\nمتجر إلكتروني\n\n\"قيود\" و\\رموز\\",
                qualityScore = 100,
                createdAt = 1_700_000_050_000L,
                updatedAt = 1_700_000_150_000L
            )
        ),
        analyses = listOf(
            BackupAnalysis(
                id = 3,
                projectId = 7,
                summary = "تحليل مختصر",
                questionsJson = "[\"سؤال واحد\"]",
                createdAt = 1_700_000_060_000L
            )
        ),
        editorFiles = listOf(
            BackupEditorFile(
                id = 2,
                projectId = 7,
                fileName = "notes.md",
                content = "# ملاحظات",
                createdAt = 1_700_000_070_000L,
                updatedAt = 1_700_000_080_000L
            )
        ),
        imageAnalyses = listOf(
            BackupImageAnalysis(
                id = 1,
                projectId = 7,
                screenType = "شاشة رئيسية",
                confidence = 0.75f,
                createdAt = 1_700_000_090_000L
            )
        )
    )

    @Test
    fun `round trip preserves every field and arabic text`() {
        val decoded = BackupDocument.fromJson(sample().toJson())
        assertEquals(sample(), decoded)
    }

    @Test
    fun `empty document round trips`() {
        val empty = BackupDocument(exportedAt = "2026-11-20T10:15:30Z")
        assertTrue(empty.isEmpty)
        assertEquals(empty, BackupDocument.fromJson(empty.toJson()))
    }

    @Test
    fun `simple json handles nested values and escapes`() {
        val value = mapOf(
            "text" to "سطر\n\"مقتبس\"\\",
            "list" to listOf(1L, 2.5, true, null, "نص"),
            "nested" to mapOf("ok" to false)
        )
        val decoded = SimpleJson.parse(SimpleJson.stringify(value)) as Map<*, *>
        assertEquals("سطر\n\"مقتبس\"\\", decoded["text"])
        assertEquals(listOf(1L, 2.5, true, null, "نص"), decoded["list"])
        assertEquals(mapOf("ok" to false), decoded["nested"])
    }

    @Test
    fun `rejects files from another app`() {
        val notBackup = SimpleJson.stringify(mapOf("format" to "other", "version" to 1))
        assertTrue(runCatching { BackupDocument.fromJson(notBackup) }.isFailure)
    }

    @Test
    fun `rejects unsupported versions`() {
        val future = SimpleJson.stringify(
            mapOf("format" to BackupRules.FORMAT, "version" to 99, "projects" to emptyList<Any>())
        )
        assertTrue(runCatching { BackupDocument.fromJson(future) }.isFailure)
    }

    @Test
    fun `rejects oversized text fields`() {
        val huge = sample().copy(
            projects = listOf(sample().projects.first().copy(name = "ا".repeat(BackupRules.MAX_NAME_CHARS + 1)))
        )
        assertTrue(runCatching { BackupDocument.fromJson(huge.toJson()) }.isFailure)
    }

    @Test
    fun `missing arrays decode as empty`() {
        val minimal = SimpleJson.stringify(
            mapOf("format" to BackupRules.FORMAT, "version" to 1, "exportedAt" to "now")
        )
        val decoded = BackupDocument.fromJson(minimal)
        assertFalse(decoded.projects.isNotEmpty())
        assertTrue(decoded.isEmpty)
    }
}
