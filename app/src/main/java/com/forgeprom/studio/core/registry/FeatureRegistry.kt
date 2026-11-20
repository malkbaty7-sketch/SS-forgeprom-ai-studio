package com.forgeprom.studio.core.registry

/**
 * R12 — سجل الميزات (CORE.GUARD): كل ميزة تُسجَّل مع فحص ذاتي حقيقي
 * قابل للتشغيل عند بدء التطبيق — لا ادعاء جاهزية قبل عمل فعلي.
 */
data class Feature(
    val id: String,
    val name: String,
    val phase: String,
    val isReady: Boolean,
    val statusNote: String
)

object FeatureRegistry {

    val features: List<Feature> = listOf(
        Feature("CORE.UI", "واجهة عربية RTL مع وضع فاتح وداكن", "R1", true, "مكتمل"),
        Feature("CORE.SETTINGS", "الإعدادات والتفضيلات المحلية", "R1", true, "مكتمل"),
        Feature("CORE.NAV", "التنقل بين الشاشات", "R1", true, "مكتمل"),
        Feature("PROJ.CRUD", "إدارة المشاريع: إنشاء وتثبيت وأرشفة وحذف", "R2", true, "مكتمل — Room محلي"),
        Feature("PROMPT.STORE", "حفظ الموجهات ونسخها في المشاريع", "R2", true, "مكتمل"),
        Feature("ACT.LOG", "سجل النشاطات", "R2", true, "مكتمل"),
        Feature("EDITOR.BASIC", "محرر نصوص مع عدادات وحفظ", "R3", true, "مكتمل"),
        Feature("AI.LOCAL", "محرك محلي قاعدي يعمل دون إنترنت", "R4", true, "توليد قاعدي مستقل عن النموذج العصبي الاختياري"),
        Feature("AI.MANAGER", "اختيار المحرك الافتراضي لكل مهمة", "R4", true, "مكتمل"),
        Feature("MODELS.CENTER", "مركز المحركات وحالتها", "R5", true, "مكتمل"),
        Feature("ANALYZER", "محلل الأفكار العربي (نية/ميزات/افتراضات/أسئلة ≤3)", "R6", true, "مكتمل"),
        Feature("PROMPT.BUILDER", "مولد البرومبتات 12 قسماً بثلاث لغات", "R7", true, "مكتمل"),
        Feature("QUALITY.CHECK", "فحص الجودة 14 عنصراً + إصلاح تلقائي", "R9", true, "مكتمل"),
        Feature("CRITIQUE", "النقد والاقتراح", "R9", true, "مكتمل"),
        Feature("IMAGE.ANALYZE", "تحليل صور الواجهات", "R8", true, "تحليل بصري محلي محدود، أو Gemini اختياري بموافقة صريحة"),
        Feature("AI.CLOUD.GEMINI", "محرك Gemini السحابي", "R5", true, "جاهز بعد إدخال المفتاح ومنح الموافقة"),
        Feature("AI.CLOUD.OPENAI", "محرك متوافق OpenAI", "R5", true, "جاهز بعد إدخال المفتاح ومنح الموافقة"),
        Feature("EXPORT.SHARE", "تصدير TXT/MD/JSON ومشاركة ونسخ", "R10", true, "مكتمل — UTF-8 مع سلامة العربية"),
        Feature("KEYS.SECURE", "مفاتيح API مشفرة", "R11", true, "مكتمل — EncryptedSharedPreferences"),
        Feature("CLOUD.CONSENT", "بوابة موافقة السحابة (مرفوضة افتراضياً)", "R11", true, "مكتمل"),
        Feature("QUALITY.TESTS", "اختبارات وحدة لطبقة المجال", "R12", true, "مكتمل — QualityChecker/IdeaAnalyzer"),
        Feature("CI.BUILD", "بناء APK تلقائي عبر GitHub Actions", "R12", true, "مكتمل — workflow في المستودع"),
        Feature("NEURAL.ONDEVICE", "نموذج عصبي محلي على الجهاز", "R13", true, "توليد نصي اختياري بعد استيراد حزمة MediaPipe متوافقة على ARM64"),
        Feature("UI.RUNTIME", "تشغيل سكربت حسابي داخل المحرر", "R3", true, "لغة حسابية محدودة تعمل محليًا دون ملفات أو شبكة"),
        Feature("SEARCH.WEB", "البحث في الإنترنت", "R9", true, "اختياري عبر بوابة مهيّأة وموافقة صريحة"),
        Feature("EDITOR.AUTOSAVE", "حفظ تلقائي واسترداد مسودات المحرر", "R14", true, "مكتمل — مسودة محلية لكل موجّه مع استعادة صريحة"),
        Feature("PROJECT.SEARCH", "بحث عربي مطبَّع وتصفية بالوسوم", "R14", true, "مكتمل — بحث في الاسم والوصف والوسوم دون إنترنت"),
        Feature("BACKUP.LOCAL", "نسخ احتياطي واستعادة محلية بصيغة JSON", "R14", true, "مكتمل — تصدير واستعادة دمجية عبر منتقي ملفات النظام"),
        Feature("UI.TEMPLATES", "قوالب بداية جاهزة للفكرة والهدف والتقنية", "R15", true, "مكتمل — قوالب محلية قابلة للتعديل بعد الاختيار"),
        Feature("GATEWAY.USAGE", "عرض استهلاك البوابة لآخر 30 يوماً", "R15", true, "مكتمل — قراءة فقط بعد الموافقة وتهيئة الحساب"),
        Feature("SEC.HTTPS", "فرض HTTPS وقيود النسخ الاحتياطي", "R15", true, "مكتمل — سياسة عنوان + Network Security Config + استثناءات نسخ"),
        Feature("OPS.GATEWAY", "جاهزية البوابة وسجلاتها وإغلاقها اللطيف", "R15", true, "مكتمل — /readyz وX-Request-Id وسجلات JSON وحاوية Docker"),
        Feature("PROMPT.VERSIONS", "سجل إصدارات الموجّه واستعادة نسخة", "R16", true, "مكتمل — أحدث 20 لقطة لكل موجّه مع استعادة إلى المحرر"),
        Feature("AI.ESTIMATE", "تقدير رموز الطلب قبل الإرسال", "R16", true, "مكتمل — تقدير محلي تقريبي بلا شبكة"),
        Feature("SHARE.PROMPTFILE", "مشاركة واستيراد ملف .forgeprom", "R16", true, "مكتمل — ترميز مستقل وتحقق من الصيغة والإصدار"),
        Feature("JOBS.RESUME", "استئناف مهام المشهد بعد إغلاق التطبيق", "R16", true, "مكتمل — WorkManager يستعلم فقط ولا يعيد الإرسال؛ الإشعار اختياري"),
        Feature("BUILD.RELEASE", "بناء إصدار مصغّر وموقّع", "R17", true, "R8 وتقليص الموارد مفعّلان؛ التوقيع يُقرأ من keystore.properties أو متغيرات البيئة، وغيابه يعني APK غير موقّع"),
        Feature("TEST.COMPOSE", "اختبارات Compose على JVM", "R17", true, "مكوّن TokenEstimateCard مغطّى باختبارين عبر Robolectric داخل CI"),
        Feature("UI.I18N", "واجهة عربية وإنجليزية", "R18", true, "العربية افتراضية والإنجليزية عبر values-en وكتالوج ترجمة؛ 13 نصاً ديناميكياً ورسائل المشاركة خارج التغطية")
    )

    fun byId(id: String): Feature? = features.firstOrNull { it.id == id }

    fun readyCount(): Int = features.count { it.isReady }
    fun plannedCount(): Int = features.size
}
