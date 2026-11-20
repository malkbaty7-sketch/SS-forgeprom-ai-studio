package com.forgeprom.studio.domain

/**
 * تطبيع النص العربي للبحث المحلي.
 *
 * يزيل التشكيل والتطويل، ويوحّد الألف والياء والتاء المربوطة والهمزتين،
 * ثم يحوّل الحروف إلى صغيرة — حتى يجد المستخدم «مشروعى» عند كتابة «مشروعي»
 * أو «إنشاء» عند كتابة «انشاء». لا يغيّر النص المخزّن نفسه.
 */
object ArabicSearch {

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val out = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u0610'..'\u061A', in '\u064B'..'\u065F', '\u0670',
                in '\u06D6'..'\u06ED', '\u0640' -> Unit

                'أ', 'إ', 'آ', 'ٱ' -> out.append('ا')
                'ى' -> out.append('ي')
                'ة' -> out.append('ه')
                'ؤ' -> out.append('و')
                'ئ' -> out.append('ي')
                else -> out.append(ch)
            }
        }
        return out.toString().lowercase()
    }

    /** هل يحتوي النص على كل كلمات الاستعلام بعد التطبيع؟ */
    fun matches(haystack: String, query: String): Boolean {
        val cleanQuery = normalize(query).trim()
        if (cleanQuery.isEmpty()) return true
        val target = normalize(haystack)
        return cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }.all { target.contains(it) }
    }
}
