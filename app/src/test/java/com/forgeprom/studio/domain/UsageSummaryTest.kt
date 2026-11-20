package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSummaryTest {

    @Test
    fun `counts accept numbers and large numeric strings`() {
        assertEquals(12L, UsageSummary.count(12))
        assertEquals(12L, UsageSummary.count(12.9))
        assertEquals(9_007_199_254_740_991L, UsageSummary.count("9007199254740991"))
        assertEquals(0L, UsageSummary.count("not-a-number"))
        assertEquals(0L, UsageSummary.count(-5))
        assertEquals(0L, UsageSummary.count(null))
    }

    @Test
    fun `totals sum requests and tokens`() {
        val days = listOf(
            UsageDay("2026-11-20", "gpt-4o-mini", 3, 100, 50),
            UsageDay("2026-11-19", "gpt-4o-mini", 2, 40, 10)
        )
        val totals = UsageSummary.totals(days)
        assertEquals(5L, totals.requests)
        assertEquals(140L, totals.promptTokens)
        assertEquals(60L, totals.completionTokens)
        assertEquals(200L, totals.tokens)
    }

    @Test
    fun `totals of empty history are zero`() {
        val totals = UsageSummary.totals(emptyList())
        assertEquals(0L, totals.requests)
        assertEquals(0L, totals.tokens)
    }

    @Test
    fun `models are aggregated and sorted by requests`() {
        val days = listOf(
            UsageDay("2026-11-20", "a", 1, 10, 5),
            UsageDay("2026-11-20", "b", 4, 20, 10),
            UsageDay("2026-11-19", "a", 2, 30, 15)
        )
        val byModel = UsageSummary.byModel(days)
        assertEquals(listOf("b", "a"), byModel.map { it.first })
        assertEquals(3L, byModel[1].second.requests)
        assertEquals(60L, byModel[1].second.tokens)
    }

    @Test
    fun `last days keeps the newest window`() {
        val days = (1..5).map { day -> UsageDay("2026-11-0$day", "m", 1, 1, 1) }
        val recent = UsageSummary.lastDays(days, 2)
        assertEquals(setOf("2026-11-04", "2026-11-05"), recent.map { it.day }.toSet())
        assertTrue(UsageSummary.lastDays(days, 0).isEmpty())
    }
}
