package com.forgeprom.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryContextTest {
    @Test fun matchesArabicDespiteDiacriticsAndAlefVariants() {
        assertEquals("تنتظر أَمَل في المحطة", StoryContext.retrieve("امل والمحطة", "تنتظر أَمَل في المحطة\nيسافر سامر إلى القرية"))
    }

    @Test fun unrelatedNotesAreNotUsedAsFallback() {
        assertEquals("", StoryContext.retrieve("المطار", "يبحر سامر في الليل"))
    }
}
