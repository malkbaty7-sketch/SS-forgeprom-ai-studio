package com.forgeprom.studio.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftCodecTest {

    @Test
    fun `round trip keeps arabic newlines and quotes`() {
        val draft = EditorDraft(
            title = "مسودة «متجر»",
            content = "السطر الأول\nالسطر الثاني \"باقتباس\" و\\شرطة مائلة\\\nثم رمز 🎬",
            savedAt = 1_700_000_000_000L
        )
        val decoded = DraftCodec.decode(DraftCodec.encode(draft))
        assertEquals(draft, decoded)
    }

    @Test
    fun `empty content round trips`() {
        val draft = EditorDraft(title = "", content = "", savedAt = 42L)
        assertEquals(draft, DraftCodec.decode(DraftCodec.encode(draft)))
    }

    @Test
    fun `malformed values return null instead of throwing`() {
        assertNull(DraftCodec.decode(null))
        assertNull(DraftCodec.decode(""))
        assertNull(DraftCodec.decode("1\n42\n***\n***"))
        assertNull(DraftCodec.decode("2\n42\nQQ==\nQQ=="))
        assertNull(DraftCodec.decode("1\nليس-رقماً\nQQ==\nQQ=="))
    }
}
