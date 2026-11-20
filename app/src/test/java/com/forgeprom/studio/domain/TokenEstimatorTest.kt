package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `blank text costs nothing`() {
        assertEquals(0, TokenEstimator.estimateText(""))
        assertEquals(0, TokenEstimator.estimateText("   \n\t "))
    }

    @Test
    fun `arabic is denser than latin for the same length`() {
        val arabic = TokenEstimator.estimateText("مرحبا".repeat(20))
        val latin = TokenEstimator.estimateText("hello".repeat(20))
        assertTrue(arabic > latin)
    }

    @Test
    fun `estimate grows monotonically with content`() {
        val short = TokenEstimator.estimateText("نص قصير")
        val long = TokenEstimator.estimateText("نص قصير ".repeat(50))
        assertTrue(long > short)
    }

    @Test
    fun `request estimate adds overhead and output budget`() {
        val estimate = TokenEstimator.estimateRequest(listOf("الهدف: متجر", "التقنية: Kotlin"), 500)
        assertTrue(estimate.inputTokens >= TokenEstimator.REQUEST_OVERHEAD)
        assertEquals(500, estimate.maxOutputTokens)
        assertEquals(estimate.inputTokens + 500, estimate.totalMax)
    }

    @Test
    fun `negative output budget is clamped`() {
        val estimate = TokenEstimator.estimateRequest(emptyList(), -10)
        assertEquals(TokenEstimator.REQUEST_OVERHEAD, estimate.inputTokens)
        assertEquals(0, estimate.maxOutputTokens)
    }
}
