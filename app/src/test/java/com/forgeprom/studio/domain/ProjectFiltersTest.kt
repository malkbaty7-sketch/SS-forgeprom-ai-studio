package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFiltersTest {

    private fun fields(
        name: String,
        description: String = "",
        tags: String = "",
        pinned: Boolean = false,
        archived: Boolean = false
    ) = ProjectSearchFields(
        name = name,
        description = description,
        tags = tags,
        isPinned = pinned,
        isArchived = archived
    )

    @Test
    fun `normalizes alef hamza and diacritics`() {
        assertEquals("انشاء مشروع", ArabicSearch.normalize("إنشاء مشروع"))
        assertEquals(ArabicSearch.normalize("مشروعي"), ArabicSearch.normalize("مشروعى"))
        assertEquals(ArabicSearch.normalize("مكتبة"), ArabicSearch.normalize("مكتبه"))
        assertEquals("تصميم", ArabicSearch.normalize("تَصْمِيم"))
    }

    @Test
    fun `search ignores hamza forms and tashkeel`() {
        assertTrue(ArabicSearch.matches("إنشاء تطبيق متجر", "انشاء"))
        assertTrue(ArabicSearch.matches("مكتبةٌ عربية", "مكتبه"))
        assertFalse(ArabicSearch.matches("تطبيق متجر", "لعبة"))
    }

    @Test
    fun `tags accept arabic comma and hash and remove duplicates`() {
        val tags = ProjectTags.parse("تصميم، عربي, #UI , تصميم")
        assertEquals(listOf("تصميم", "عربي", "UI"), tags)
        assertEquals("تصميم, عربي, UI", ProjectTags.normalize(" تصميم ،عربي,#UI"))
    }

    @Test
    fun `tag filter compares normalized values`() {
        val filter = ProjectFilter(tag = "تصميم")
        assertTrue(ProjectFilters.matches(fields("متجر", tags = "عربي, تصميم"), filter))
        assertFalse(ProjectFilters.matches(fields("متجر", tags = "عربي"), filter))
    }

    @Test
    fun `pinned mode excludes archived and unpinned projects`() {
        val filter = ProjectFilter(mode = ProjectFilterMode.PINNED)
        assertTrue(ProjectFilters.matches(fields("مثبّت", pinned = true), filter))
        assertFalse(ProjectFilters.matches(fields("عادي"), filter))
        assertFalse(ProjectFilters.matches(fields("مثبّت مؤرشف", pinned = true, archived = true), filter))
    }

    @Test
    fun `archived mode returns only archived projects`() {
        val filter = ProjectFilter(mode = ProjectFilterMode.ARCHIVED)
        assertTrue(ProjectFilters.matches(fields("قديم", archived = true), filter))
        assertFalse(ProjectFilters.matches(fields("جديد"), filter))
    }

    @Test
    fun `query searches name description and tags`() {
        assertTrue(ProjectFilters.matches(fields("متجر"), ProjectFilter(query = "متجر")))
        assertTrue(ProjectFilters.matches(fields("مشروع", description = "سلة شراء"), ProjectFilter(query = "سلة")))
        assertTrue(ProjectFilters.matches(fields("مشروع", tags = "تجارة"), ProjectFilter(query = "تجارة")))
        assertFalse(ProjectFilters.matches(fields("مشروع", tags = "تجارة"), ProjectFilter(query = "لعبة")))
    }

    @Test
    fun `empty filter accepts archived and active projects`() {
        val filter = ProjectFilter()
        assertFalse(filter.isActive)
        assertTrue(ProjectFilters.matches(fields("نشط"), filter))
        assertTrue(ProjectFilters.matches(fields("مؤرشف", archived = true), filter))
    }
}
