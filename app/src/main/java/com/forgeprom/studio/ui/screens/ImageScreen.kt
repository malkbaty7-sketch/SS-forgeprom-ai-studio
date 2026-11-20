package com.forgeprom.studio.ui.screens

import com.forgeprom.studio.ui.components.ActionGroup
import com.forgeprom.studio.ui.components.ScreenHeading
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.ai.ImageAnalysisResult
import com.forgeprom.studio.core.ai.LocalImageAnalyzer
import com.forgeprom.studio.core.ai.generationContext
import com.forgeprom.studio.core.registry.FeatureRegistry
import com.forgeprom.studio.core.settings.AppSettings
import com.forgeprom.studio.core.util.Connectivity
import com.forgeprom.studio.ui.components.PhaseNote
import com.forgeprom.studio.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * R8 — تحليل بصري محلي أو تحليل Gemini اختياري بموافقة صريحة.
 */
@Composable
fun ImageScreen(container: AppContainer, onUseAnalysis: () -> Unit) {
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cloudMode by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var legalOnly by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ImageAnalysisResult?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        result = null
        error = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeading("تحليل صورة واجهة")
        Text(
            "استخرج وصفًا بصريًا أوليًا للاستلهام من صورة التصميم.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ActionGroup {
            FilterChip(selected = !cloudMode, enabled = !busy, onClick = {
                cloudMode = false; result = null; error = null
            }, label = { Text("محلي دون اتصال") })
            FilterChip(selected = cloudMode, enabled = !busy, onClick = {
                cloudMode = true; result = null; error = null
            }, label = { Text("Gemini سحابي") })
        }
        OutlinedButton(enabled = !busy, onClick = { picker.launch("image/*") }) {
            Text(if (imageUri == null) "اختيار صورة" else "تغيير الصورة")
        }
        if (imageUri != null) Text("تم اختيار صورة ✓", style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = legalOnly, onCheckedChange = { legalOnly = it })
            Text("ألتزم أن الاستخدام استلهام فقط ولا أنسخ تصميباً محمياً", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                error = null
                result = null
                val uri = imageUri
                if (uri == null) { error = "اختر صورة أولاً"; return@Button }
                if (!legalOnly) { error = "يجب الإقرار بأن الاستخدام استلهام فقط"; return@Button }
                if (cloudMode && !settings.cloudConsent) {
                    error = "موافقة السحابة غير ممنوحة — فعّلها من الإعدادات أولاً (لا يُرسل شيء دون موافقة)"
                    return@Button
                }
                if (cloudMode && !Connectivity.isOnline(context)) { error = "لا اتصال بالإنترنت — التحليل سحابي"; return@Button }
                if (cloudMode && container.apiKeys.geminiKey().isBlank()) { error = "أدخل مفتاح Gemini من الإعدادات"; return@Button }

                val useCloud = cloudMode
                busy = true
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { readBoundedImage(it) }
                                ?: throw IllegalArgumentException("تعذّر قراءة الصورة")
                        }
                        val analysis = if (useCloud) {
                            container.engines.cloudGate().getOrThrow()
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            container.engines.engineById("cloud.gemini").analyzeImage(bytes, mime, note).getOrThrow()
                        } else LocalImageAnalyzer.analyze(bytes)
                        result = analysis
                        container.logActivity("image", if (useCloud) "تحليل صورة عبر Gemini" else "تحليل صورة محلي")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = if (e is IllegalArgumentException || e is SecurityException) e.message ?: "تعذّر تحليل الصورة."
                            else "تعذّر تحليل الصورة. تحقّق من الملف وإعدادات المحرك."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "جارٍ التحليل…" else if (cloudMode) "تحليل الصورة عبر Gemini" else "تحليل الصورة محليًا") }

        if (busy) CircularProgressIndicator()
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نوع الشاشة: " + r.screenType, style = MaterialTheme.typography.titleSmall)
                    Text((if (cloudMode) "المكونات: " else "التسميات البصرية: ") + r.components.joinToString("، "))
                    Text("الألوان: " + r.colors.entries.joinToString("، ") { it.key + "=" + it.value })
                    Text("التخطيط: " + r.layout)
                    if (r.states.isNotEmpty()) Text("الحالات: " + r.states.joinToString("، "))
                    if (r.assumptions.isNotEmpty())
                        Text("افتراضات: " + r.assumptions.joinToString("، "))
                    Text(
                        (if (cloudMode) "الثقة: " else "ثقة أعلى تسمية بصرية: ") + (r.confidence * 100).toInt() + "%",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text("عند اعتماد التحليل، قد يُرسل الوصف إلى محرك التوليد السحابي إذا اخترته وفعّلت الموافقة. لا تُرفق الصورة الأصلية بطلب التوليد.")
            Button(
                onClick = {
                    val s = container.session
                    s.clear()
                    s.isImageInspired = true
                    s.sourceInstruction = note.ifBlank { "واجهة مستوحاة من صورة: " + r.screenType }
                    s.goal = "بناء واجهة مستوحاة من صورة (استلهام فقط)"
                    s.tech = ""
                    s.imageAnalysis = r
                    s.draft = com.forgeprom.studio.domain.IdeaAnalyzer.analyze(
                        s.sourceInstruction + "\n" + r.generationContext(), s.goal, s.tech
                    )
                    onUseAnalysis()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("اعتماد التحليل وفتح شاشة التحليل") }
        }

        val f = FeatureRegistry.byId("IMAGE.ANALYZE")
        SectionCard("حالة الميزة (مبدأ الصدق)") {
            Text(f?.name ?: "تحليل الصور")
            Text(
                f?.statusNote ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PhaseNote("التحليل السحابي يرسل الصورة إلى Google Gemini فقط بعد موافقتك الصريحة. التحليل المحلي لا يرسل الصورة، ويقدّم ألوانًا وتسميات عامة وتوزيع النص اللاتيني، لا تفسيرًا كاملًا للواجهة.")
    }
}

private fun readBoundedImage(input: InputStream): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(result.size() + count <= 5 * 1024 * 1024) { "حجم الصورة يتجاوز 5 ميبيبايت." }
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}
