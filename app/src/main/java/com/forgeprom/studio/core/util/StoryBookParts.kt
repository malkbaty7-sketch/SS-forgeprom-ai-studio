package com.forgeprom.studio.core.util

internal object StoryBookParts {
    // Even with six JSON bytes per UTF-16 unit, the request fits the gateway's 64 KiB limit.
    private const val PART_LIMIT = 10_000
    private const val MAX_PARTS = 1000

    fun split(text: String): List<String> = buildList {
        var start = 0
        while (start < text.length) {
            var end = start + minOf(PART_LIMIT, text.length - start)
            if (end < text.length) {
                if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                val boundary = (end - 1 downTo start + PART_LIMIT / 2).firstOrNull { text[it].isWhitespace() }
                if (boundary != null) end = boundary + 1
            }
            val part = text.substring(start, end).replace("\u0000", "").trim()
            if (part.isNotEmpty()) {
                require(size < MAX_PARTS)
                add(part)
            }
            start = end
        }
    }
}
