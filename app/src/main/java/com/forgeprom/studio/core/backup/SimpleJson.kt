package com.forgeprom.studio.core.backup

/**
 * JSON صغير مكتفٍ بذاته (كتابة وقراءة) لملفات النسخ الاحتياطي.
 *
 * لماذا لا نستخدم org.json؟ لأن طبقة النسخ الاحتياطي يجب أن تبقى قابلة
 * للاختبار على JVM دون Android. يدعم هذا التطبيق الكائنات والمصفوفات
 * والنصوص والأرقام والمنطق وnull، مع حد للعمق ورسائل خطأ صريحة.
 */
object SimpleJson {

    private const val MAX_DEPTH = 64

    fun stringify(value: Any?): String =
        StringBuilder(512).also { out -> writeValue(out, value, 0) }.toString()

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue(0)
        parser.skipWhitespace()
        require(parser.finished) { "Unexpected trailing content in JSON" }
        return value
    }

    private fun writeValue(out: StringBuilder, value: Any?, depth: Int) {
        require(depth <= MAX_DEPTH) { "JSON nested too deeply" }
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Float -> writeNumber(out, value.toDouble())
            is Double -> writeNumber(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    require(k is String) { "JSON object keys must be strings" }
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k)
                    out.append(':')
                    writeValue(out, v, depth + 1)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    writeValue(out, item, depth + 1)
                }
                out.append(']')
            }
            is Array<*> -> writeValue(out, value.asList(), depth)
            else -> throw IllegalArgumentException("Unsupported JSON value: " + value::class.java.name)
        }
    }

    private fun writeNumber(out: StringBuilder, value: Double) {
        require(!value.isNaN() && !value.isInfinite()) { "Invalid JSON number" }
        if (value == Math.floor(value) && Math.abs(value) < 9.2e18) {
            out.append(value.toLong())
        } else {
            out.append(value.toString())
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        private var index = 0

        val finished: Boolean get() = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "JSON nested too deeply" }
            skipWhitespace()
            require(index < text.length) { "Unexpected end of JSON" }
            return when (val c = text[index]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("Unexpected character '$c' in JSON")
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            index++ // {
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (peek() == '}') { index++; return map }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "Expected a string key in JSON object" }
                val key = readString()
                skipWhitespace()
                require(peek() == ':') { "Expected ':' after JSON key" }
                index++
                map[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> { index++; return map }
                    else -> throw IllegalArgumentException("Expected ',' or '}' in JSON object")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            index++ // [
            skipWhitespace()
            val list = ArrayList<Any?>()
            if (peek() == ']') { index++; return list }
            while (true) {
                list.add(readValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> { index++; return list }
                    else -> throw IllegalArgumentException("Expected ',' or ']' in JSON array")
                }
            }
        }

        private fun readString(): String {
            require(peek() == '"') { "Expected a string in JSON" }
            index++
            val out = StringBuilder()
            while (true) {
                require(index < text.length) { "Unterminated JSON string" }
                val ch = text[index++]
                when {
                    ch == '"' -> return out.toString()
                    ch == '\\' -> {
                        require(index < text.length) { "Unterminated escape in JSON string" }
                        when (val escape = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= text.length) { "Invalid unicode escape in JSON" }
                                val hex = text.substring(index, index + 4)
                                index += 4
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid unicode escape in JSON")
                                out.append(code.toChar())
                            }
                            else -> throw IllegalArgumentException("Invalid escape '\\$escape' in JSON")
                        }
                    }
                    ch < ' ' -> throw IllegalArgumentException("Control character in JSON string")
                    else -> out.append(ch)
                }
            }
        }

        private fun readNumber(): Number {
            val start = index
            if (peek() == '-') index++
            var digits = 0
            while (index < text.length && text[index].isDigit()) { index++; digits++ }
            require(digits > 0) { "Invalid number in JSON" }
            var fractional = false
            if (index < text.length && text[index] == '.') {
                index++
                fractional = true
                var fractionDigits = 0
                while (index < text.length && text[index].isDigit()) { index++; fractionDigits++ }
                require(fractionDigits > 0) { "Invalid number in JSON" }
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                var exponentDigits = 0
                while (index < text.length && text[index].isDigit()) { index++; exponentDigits++ }
                require(exponentDigits > 0) { "Invalid number in JSON" }
            }
            val raw = text.substring(start, index)
            return if (!fractional && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                raw.toLongOrNull() ?: raw.toDouble()
            } else {
                raw.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number in JSON")
            }
        }

        private fun readLiteral(token: String, value: Any?): Any? {
            require(text.regionMatches(index, token, 0, token.length)) { "Invalid literal in JSON" }
            index += token.length
            return value
        }

        private fun peek(): Char {
            require(index < text.length) { "Unexpected end of JSON" }
            return text[index]
        }
    }
}
