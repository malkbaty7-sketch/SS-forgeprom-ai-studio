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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankDirection() {
        edit.request(" ")
    }
}
