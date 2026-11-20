package com.forgeprom.studio.domain

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class FinalDraftExportTest {
    @Test fun exportsTypedParagraphsAndEscapesText() {
        val xml = FinalDraftExport.render(".المحطة - ليلاً\n\n@سلمى\n(بهدوء)\nأ & ب < ج\n\nتغلق الباب.")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val paragraphs = document.getElementsByTagName("Paragraph")
        assertEquals(5, paragraphs.length)
        val types = (0 until paragraphs.length).map { paragraphs.item(it).attributes.getNamedItem("Type").nodeValue }
        assertEquals(listOf("Scene Heading", "Character", "Parenthetical", "Dialogue", "Action"), types)
        assertEquals("أ & ب < ج", paragraphs.item(3).textContent)
    }
}
