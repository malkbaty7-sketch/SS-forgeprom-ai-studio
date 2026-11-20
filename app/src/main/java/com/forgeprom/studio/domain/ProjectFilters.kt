package com.forgeprom.studio.domain

/** نطاق عرض المشاريع في شاشة المشاريع. */
enum class ProjectFilterMode { ALL, PINNED, ARCHIVED }

/**
 * وصفة تصفية المشاريع: نص بحث حر + نطاق + وسم اختياري.
 * كائن نقي بلا اعتماد على Android ليسهل اختباره.
 */
data class ProjectFilter(
    val query: String = "",
    val mode: ProjectFilterMode = ProjectFilterMode.ALL,
    val tag: String? = null
) {
    val isActive: Boolean
        get() = query.isNotBlank() || mode != ProjectFilterMode.ALL || !tag.isNullOrBlank()
}

/** أدوات الوسوم: تخزينها كنص مفصول بفواصل، مع تطبيع ومنع التكرار. */
object ProjectTags {
    const val MAX_TAGS = 12
    const val MAX_TAG_LENGTH = 24

    fun parse(raw: String): List<String> =
        raw.split(',', '،')
            .map { it.trim().trimStart('#').trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(MAX_TAG_LENGTH) }
            .distinctBy { ArabicSearch.normalize(it) }
            .take(MAX_TAGS)

    /** شكل التخزين الموحّد: «تصميم, عربي» أو نص فارغ. */
    fun normalize(raw: String): String = parse(raw).joinToString(", ")

    /** كل الوسوم الفريدة من مجموعة مشاريع، مرتبة عربياً. */
    fun all(rawValues: Iterable<String>): List<String> =
        rawValues.asSequence()
            .flatMap { parse(it).asSequence() }
            .distinctBy { ArabicSearch.normalize(it) }
            .sortedBy { ArabicSearch.normalize(it) }
            .toList()

    fun sameTag(a: String, b: String): Boolean =
        ArabicSearch.normalize(a.trim()) == ArabicSearch.normalize(b.trim())
}

/** الحقول التي تشارك في تصفية مشروع — بلا اعتماد على كيان Room. */
data class ProjectSearchFields(
    val name: String,
    val description: String = "",
    val tags: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

object ProjectFilters {

    fun matches(fields: ProjectSearchFields, filter: ProjectFilter): Boolean {
        when (filter.mode) {
            ProjectFilterMode.ARCHIVED -> if (!fields.isArchived) return false
            ProjectFilterMode.PINNED -> if (!fields.isPinned || fields.isArchived) return false
            ProjectFilterMode.ALL -> Unit
        }
        if (!filter.tag.isNullOrBlank() && ProjectTags.parse(fields.tags).none { ProjectTags.sameTag(it, filter.tag) }) {
            return false
        }
        if (filter.query.isNotBlank()) {
            val haystack = fields.name + "\n" + fields.description + "\n" + ProjectTags.parse(fields.tags).joinToString(" ")
            if (!ArabicSearch.matches(haystack, filter.query)) return false
        }
        return true
    }
}
