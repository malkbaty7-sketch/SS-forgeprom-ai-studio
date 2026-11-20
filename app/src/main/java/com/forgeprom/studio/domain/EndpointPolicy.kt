package com.forgeprom.studio.domain

import java.net.URI

/**
 * سياسة عنوان المزوّد: HTTPS إلزامي، والاستثناء الوحيد هو تطوير محلي
 * على localhost أو محاكي Android (10.0.2.2). تمنع أيضاً تمرير بيانات
 * دخول أو استعلام داخل العنوان.
 */
object EndpointPolicy {

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** null يعني أن العنوان مقبول؛ وإلا رسالة عربية جاهزة للعرض. */
    fun validateBaseUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "أدخل عنوان المزوّد الأساسي."
        if (value.any { it.isWhitespace() }) return "العنوان لا يحتوي مسافات."
        val uri = runCatching { URI(value) }.getOrNull() ?: return "العنوان غير صالح."
        if (uri.userInfo != null) return "لا تُدرج اسم مستخدم أو كلمة مرور في العنوان."
        if (uri.query != null || uri.fragment != null) return "أزل الاستعلام أو الجزء (#) من العنوان."
        val scheme = uri.scheme?.lowercase() ?: return "أضف https:// إلى بداية العنوان."
        val host = uri.host ?: return "العنوان بلا اسم مضيف صالح."
        return when {
            scheme == "https" -> null
            scheme == "http" && host.lowercase() in LOCAL_HOSTS -> null
            scheme == "http" -> "يلزم HTTPS. HTTP مسموح فقط للتطوير المحلي (localhost أو 10.0.2.2)."
            else -> "استخدم https:// فقط."
        }
    }

    fun isAllowed(raw: String): Boolean = validateBaseUrl(raw) == null
}
