package com.forgeprom.studio.domain

/** سطر استهلاك يومي كما تعيده البوابة. */
data class UsageDay(
    val day: String,
    val model: String,
    val requests: Long,
    val promptTokens: Long,
    val completionTokens: Long
)

data class UsageTotals(
    val requests: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0
) {
    val tokens: Long get() = promptTokens + completionTokens
    operator fun plus(other: UsageTotals) = UsageTotals(
        requests = requests + other.requests,
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens
    )
}

/**
 * تجميع سجل استهلاك البوابة. البوابة تعيد الأعداد الكبيرة كسلاسل رقمية،
 * لذلك نقبل الرقم أو السلسلة، ونتجاهل أي قيمة سالبة أو غير رقمية.
 */
object UsageSummary {

    fun count(value: Any?): Long = when (value) {
        is Number -> value.toLong().coerceAtLeast(0)
        is String -> value.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0
        else -> 0
    }

    fun totals(days: List<UsageDay>): UsageTotals =
        days.fold(UsageTotals()) { sum, day ->
            sum + UsageTotals(day.requests, day.promptTokens, day.completionTokens)
        }

    /** إجمالي كل نموذج، مرتباً تنازلياً بعدد الطلبات ثم بالاسم. */
    fun byModel(days: List<UsageDay>): List<Pair<String, UsageTotals>> =
        days.groupBy { it.model }
            .map { (model, rows) -> model to totals(rows) }
            .sortedWith(compareByDescending<Pair<String, UsageTotals>> { it.second.requests }.thenBy { it.first })

    /** آخر N يوماً فقط (البوابة تعيد حتى 30). */
    fun lastDays(days: List<UsageDay>, count: Int): List<UsageDay> {
        if (count <= 0) return emptyList()
        val limit = days.map { it.day }.distinct().sortedDescending().take(count).toSet()
        return days.filter { it.day in limit }
    }
}
