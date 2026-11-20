# R17 — قواعد R8 للإصدار.
# التطبيق يستخدم Compose + Room + WorkManager + MediaPipe + ML Kit + PDFBox.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# WorkManager يبني العمال بالانعكاس
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keepclassmembers class * extends androidx.work.ListenableWorker { <init>(...); }

# MediaPipe / ML Kit تعتمد على JNI وأسماء أصناف
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.mlkit.**

# PDFBox وتبعياته الاختيارية
-keep class com.tom_roush.** { *; }
-dontwarn org.apache.**
-dontwarn com.gemalto.**
-dontwarn com.gemalto.jp2.**
-dontwarn javax.**
-dontwarn java.awt.**

# Room يولّد التنفيذ مسبقاً، لكن نُبقي الأصناف المعلّمة
-keep @androidx.room.Entity class * { *; }

# OkHttp/JSON: تحذيرات اختيارية
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.json.**
