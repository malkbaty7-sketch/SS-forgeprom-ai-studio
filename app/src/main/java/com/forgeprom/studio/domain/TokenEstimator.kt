package com.forgeprom.studio.domain

/** تقدير تقريبي لحجم الطلب بالرموز قبل إرساله إلى مزوّد سحابي. */
data class TokenEstimate(
    val inputTokens: Int,
    val maxOutputTokens: Int
) {
    val totalMax: Int get() = inputTokens + maxOutputTokens
}

/**
 * تقدير محلي خشن (لا يستهلك شبكة): العربية تُستهلك برمز لكل حرفين تقريباً،
 * واللاتينية برمز لكل أربعة أحرف. القيمة للشفافية فقط وليست فاتورة؛
 * العدّاد الحقيقي يعود من المزوّد في سجل الاستهلاك.
 */
object TokenEstimator {

    /** هامش ثابت لتعليمات النظام والبنية. */
    const val REQUEST_OVERHEAD = 140

    fun estimateText(text: String): Int {
        if (text.isBlank()) return 0
        var arabic = 0
        var other = 0
        for (ch in text) {
            when {
                ch.isWhitespace() -> Unit
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\uFB50'..'\uFDFF' -> arabic++
                else -> other++
            }
        }
        return Math.ceil(arabic / 2.0).toInt() + Math.ceil(other / 4.0).toInt()
    }

    fun estimateRequest(parts: List<String>, maxOutputTokens: Int = 800): TokenEstimate {
        val body = parts.sumOf { estimateText(it) }
        return TokenEstimate(
            inputTokens = REQUEST_OVERHEAD + body,
            maxOutputTokens = maxOutputTokens.coerceAtLeast(0)
        )
    }
}
