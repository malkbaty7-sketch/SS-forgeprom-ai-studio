package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticContextTest {
    @Test fun usesOnlyOriginalIndexedPassages() {
        val passages = SemanticContext.passages("الأول\nالثاني")
        assertEquals("الثاني", SemanticContext.select("2", passages))
        assertEquals("", SemanticContext.select("NONE", passages))
        assertTrue(runCatching { SemanticContext.select("3", passages) }.isFailure)
        assertTrue(runCatching { SemanticContext.select("نص مخترع", passages) }.isFailure)
    }
}
