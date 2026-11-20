package com.forgeprom.studio.domain

import org.junit.Assert.*
import org.junit.Test

class CinematicEditTest {
    private val edit = CinematicEdit("قبل\nصمت\nبعد", 4, 7)

    @Test fun requestContainsOnlySelectedPassageAndDirection() {
        val request = edit.request("أضف صوت المطر")
        assertTrue(request.cinematicEdit)
        assertTrue(request.instruction.contains("صمت"))
        assertTrue(request.instruction.contains("أضف صوت المطر"))
        assertFalse(request.instruction.contains("قبل"))
        assertFalse(request.instruction.contains("بعد"))
    }

    @Test fun replacesOnlySelectedRange() {
        assertEquals("قبل\nدقّ المطر\nبعد", edit.apply(edit.source, "دقّ المطر"))
    }

    @Test fun rejectsStaleSourceAndBlankReplacement() {
        assertNull(edit.apply(edit.source + "!", "اقتراح"))
        assertNull(edit.apply(edit.source, "  "))
    }

    @Test fun allPresetsProduceValidRewriteRequests() {
        CinematicAction.entries.forEach { action ->
            assertTrue(edit.request(action.direction).instruction.contains(action.direction))
        }
    }

    @Test fun contextIsBoundedAndOnlySentWhenRequested() {
        val source = "x".repeat(500) + "مقطع" + "y".repeat(500)
        val selected = CinematicEdit(source, 500, 504)
        assertFalse(selected.request("اختصر").instruction.contains("xxxxxxxx"))
        val request = selected.request("اختصر", includeContext = true)
        assertTrue(request.instruction.contains("x".repeat(400)))
        assertTrue(request.instruction.contains("y".repeat(400)))
        assertFalse(request.instruction.contains("x".repeat(401)))
        assertFalse(request.instruction.contains("y".repeat(401)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankDirection() {
        edit.request(" ")
    }
}
