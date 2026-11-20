package com.forgeprom.studio.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImageSummaryTest {
    @Test fun paletteRanksCommonOpaqueColorsAndSkipsTransparentPixels() {
        assertEquals(listOf("#FF0000", "#0000FF"), dominantImageColors(sequenceOf(
            0x00FF00FF, 0xFFFF0000.toInt(), 0xFFF90000.toInt(), 0xFF0000FF.toInt()
        )))
    }

    @Test fun layoutSeparatesVerticalTextRegions() {
        assertEquals("عمودي؛ كتل نص لاتيني: 1 أعلى، 1 وسط، 2 أسفل.",
            describeTextLayout(500, 900, listOf(0.1f, 0.45f, 0.7f, 0.9f)))
    }

    @Test fun promptContextDoesNotIncludeRawProviderOutput() {
        val result = ImageAnalysisResult("screen", listOf("label"), emptyMap(), "vertical", emptyList(),
            listOf("not a component detector"), 0.7f, "raw provider text")
        assertTrue(result.generationContext().contains("vertical"))
        assertFalse(result.generationContext().contains("raw provider text"))
    }
}
