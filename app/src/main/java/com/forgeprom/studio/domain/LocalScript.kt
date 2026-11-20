package com.forgeprom.studio.domain

/** A bounded arithmetic interpreter, not a general-purpose language or a system shell. */
internal object LocalScript {
    const val MAX_SOURCE = 4096
    private const val MAX_LINES = 64
    private val binding = Regex("let[ \\t]+([A-Za-z_][A-Za-z0-9_]{0,31})[ \\t]*=[ \\t]*(.+)")
    private val print = Regex("print[ \\t]+(.+)")

    fun run(source: String): List<String> {
        require(source.length <= MAX_SOURCE) { "الحد الأقصى $MAX_SOURCE حرفًا." }
        val lines = source.lines()
        require(lines.size <= MAX_LINES) { "الحد الأقصى $MAX_LINES سطرًا." }
        val variables = mutableMapOf<String, Double>()
        return buildList {
            lines.forEachIndexed { index, raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachIndexed
                try {
                    val assignment = binding.matchEntire(line)
                    val output = print.matchEntire(line)
                    when {
                        assignment != null -> {
                            val (name, expression) = assignment.destructured
                            variables[name] = Expression(expression, variables).parse()
                        }
                        output != null -> add(Expression(output.groupValues[1], variables).parse().toString().removeSuffix(".0"))
                        else -> throw IllegalArgumentException("استخدم let لتعريف متغيّر أو print لعرض قيمة.")
                    }
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("السطر ${index + 1}: ${e.message}", e)
                }
            }
        }
    }

    private class Expression(private val source: String, private val variables: Map<String, Double>) {
        private var at = 0
        fun parse(): Double {
            val value = sum(0)
            require(peek() == null) { "رمز غير متوقع بعد التعبير." }
            return value
        }

        private fun sum(depth: Int): Double {
            var left = product(depth)
            while (true) left = when {
                take('+') -> finite(left + product(depth))
                take('-') -> finite(left - product(depth))
                else -> return left
            }
        }

        private fun product(depth: Int): Double {
            var left = atom(depth)
            while (true) left = when {
                take('*') -> finite(left * atom(depth))
                take('/') -> {
                    val right = atom(depth)
                    require(right != 0.0) { "القسمة على صفر غير مسموحة." }
                    finite(left / right)
                }
                else -> return left
            }
        }

        private fun atom(depth: Int): Double {
            require(depth < 32) { "تداخل التعبير أعمق من الحد المسموح." }
            return when {
                take('+') -> atom(depth + 1)
                take('-') -> -atom(depth + 1)
                take('(') -> sum(depth + 1).also { require(take(')')) { "القوس الختامي مفقود." } }
                else -> literal()
            }
        }

        private fun literal(): Double {
            val head = peek()
            val start = at
            if (head != null && (head in 'a'..'z' || head in 'A'..'Z' || head == '_')) {
                while (source.getOrNull(at)?.let { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' } == true) at++
                return requireNotNull(variables[source.substring(start, at)]) { "المتغيّر غير معرّف." }
            }
            while (source.getOrNull(at)?.let { it in '0'..'9' || it == '.' } == true) at++
            return finite(requireNotNull(source.substring(start, at).toDoubleOrNull()) { "توقّعت عددًا أو متغيّرًا." })
        }

        private fun finite(value: Double): Double = value.also { require(it.isFinite()) { "القيمة خارج النطاق العددي." } }
        private fun peek(): Char? {
            while (source.getOrNull(at)?.isWhitespace() == true) at++
            return source.getOrNull(at)
        }
        private fun take(token: Char): Boolean = (peek() == token).also { if (it) at++ }
    }
}
