package com.forgeprom.studio.domain

/** FDX renderer for the single-scene Fountain subset produced by SceneDraft. */
object FinalDraftExport {
    fun render(scene: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<FinalDraft DocumentType=\"Script\" Template=\"No\" Version=\"1\"><Content>\n")
        var dialogue = false
        SceneDraft.validate(scene).lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) {
                dialogue = false
                return@forEach
            }
            val (type, text) = when {
                line.startsWith(".") && !line.startsWith("..") -> {
                    dialogue = false
                    "Scene Heading" to line.drop(1)
                }
                line.startsWith("@") -> {
                    dialogue = true
                    "Character" to line.drop(1)
                }
                dialogue && line.startsWith("(") && line.endsWith(")") -> "Parenthetical" to line
                dialogue -> "Dialogue" to line
                else -> "Action" to line
            }
            append("<Paragraph Type=\"").append(type).append("\"><Text>")
            text.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> if (ch >= ' ' && ch != '\uFFFE' && ch != '\uFFFF') append(ch)
                }
            }
            append("</Text></Paragraph>\n")
        }
        append("</Content></FinalDraft>\n")
    }
}
