package com.forgeprom.studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBookPartsTest {
    @Test fun keepsAllTextAndDoesNotSplitSurrogatePairs() {
        val text = "أ".repeat(9_999) + "🎬" + "ب".repeat(20_000)
        val parts = StoryBookParts.split(text)
        assertEquals(text, parts.joinToString(""))
        assertEquals(9_999, parts.first().length)
        assertTrue(parts[1].startsWith("🎬"))
        assertTrue(parts.all { it.length <= 10_000 })
    }

    @Test fun prefersWhitespaceBoundariesAndSkipsBlankInput() {
        val first = "أ".repeat(8_000)
        val second = "ب".repeat(5_000)
        assertEquals(listOf(first, second), StoryBookParts.split("$first\n$second"))
        assertTrue(StoryBookParts.split("\n \t\u0000").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTextThatCannotFitInsteadOfIndexingATruncatedBook() {
        StoryBookParts.split("a".repeat(10_000_001))
    }
}
