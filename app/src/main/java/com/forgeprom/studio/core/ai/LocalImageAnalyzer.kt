package com.forgeprom.studio.core.ai

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/** Uses bundled ML Kit models; neither the image nor recognized text is sent to a provider. */
internal object LocalImageAnalyzer {
    suspend fun analyze(bytes: ByteArray): ImageAnalysisResult = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "صيغة الصورة غير مدعومة." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }))
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.65f).build())
            val labels = try { Tasks.await(labeler.process(image)).sortedByDescending { it.confidence }.take(5) }
                finally { labeler.close() }
            ensureActive()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val blocks = try { Tasks.await(recognizer.process(image)).textBlocks }
                finally { recognizer.close() }
            ensureActive()
            val positions = blocks.mapNotNull { block -> block.boundingBox?.centerY()?.toFloat()?.div(bitmap.height) }
            val layout = describeTextLayout(bitmap.width, bitmap.height, positions)
            val pixels = sequence {
                val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / 64)
                for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
                    yield(bitmap.getPixel(x, y))
                }
            }
            ImageAnalysisResult(
                screenType = "غير محدد — وصف بصري محلي",
                components = labels.map { it.text }.ifEmpty { listOf("لا توجد تسميات بصرية موثوقة") },
                colors = dominantImageColors(pixels).mapIndexed { index, color -> "لون ${index + 1}" to color }.toMap(),
                layout = layout,
                states = emptyList(),
                assumptions = listOf("التسميات تصف الصورة إجمالًا وليست كشفًا للأزرار أو عناصر الواجهة.",
                    "توزيع النص يستند إلى OCR للخط اللاتيني فقط؛ لا يُدرج النص المقروء في النتيجة."),
                confidence = labels.firstOrNull()?.confidence ?: 0f,
                rawText = ""
            )
        } finally {
            bitmap.recycle()
        }
    }
}

internal fun describeTextLayout(width: Int, height: Int, positions: List<Float>): String {
    val shape = when {
        width > height -> "أفقي"
        width < height -> "عمودي"
        else -> "مربع"
    }
    if (positions.isEmpty()) return "$shape؛ لم تُكتشف كتل نص لاتيني."
    val upper = positions.count { it < 1f / 3 }
    val lower = positions.count { it >= 2f / 3 }
    return "$shape؛ كتل نص لاتيني: $upper أعلى، ${positions.size - upper - lower} وسط، $lower أسفل."
}

internal fun dominantImageColors(pixels: Sequence<Int>): List<String> = pixels
    .filter { it ushr 24 >= 128 }
    .groupingBy { (it and 0x00F0F0F0) or ((it and 0x00F0F0F0) shr 4) }
    .eachCount().entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
    .take(4).map { String.format(Locale.ROOT, "#%06X", it.key) }
