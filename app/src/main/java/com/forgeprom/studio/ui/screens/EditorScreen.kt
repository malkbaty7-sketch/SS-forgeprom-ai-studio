package com.forgeprom.studio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.forgeprom.studio.domain.CinematicAction
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.forgeprom.studio.domain.CinematicEdit
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.util.Exporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * R3 — المحرر: تحرير موجه محفوظ مع عدادات وحفظ ونسخ وتصدير.
 */
@Composable
fun EditorScreen(container: AppContainer, promptId: Long, onDone: () -> Unit) {
    val prompt = remember(promptId) {
        runBlocking { container.db.promptDao().byId(promptId) }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (prompt == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("الموجه غير موجود", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onDone) { Text("رجوع") }
        }
        return
    }

    var title by remember { mutableStateOf(prompt.title) }
    var field by remember(promptId) { mutableStateOf(TextFieldValue(prompt.content)) }
    var undo by remember(promptId) { mutableStateOf<TextFieldValue?>(null) }
    val content = field.text
    var edit by remember(promptId) { mutableStateOf<CinematicEdit?>(null) }

    var showScene by remember(promptId) { mutableStateOf(false) }
    if (showScene) {
        SceneDraftDialog(container, promptId, title, onDismiss = { showScene = false }) { scene ->
            undo = field
            val updated = if (field.text.isBlank()) scene else field.text + "\n\n" + scene
            field = TextFieldValue(updated, TextRange(updated.length))
            showScene = false
        }
    }

    edit?.let { snapshot ->
        SmartEditDialog(container, snapshot, onDismiss = { edit = null }) { replacement ->
            snapshot.apply(field.text, replacement)?.let { updated ->
                undo = TextFieldValue(snapshot.source, TextRange(snapshot.start, snapshot.end))
                field = TextFieldValue(updated, TextRange(snapshot.start + replacement.length))
            }
            edit = null
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("عنوان الموجه") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = field,
            onValueChange = { if (it.text != field.text) undo = null; field = it },
            label = { Text("نص الموجه") },
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { showScene = true }) { Text("توليد مشهد وذاكرة القصة") }
        undo?.let { previous ->
            OutlinedButton(onClick = { field = previous; undo = null }) { Text("تراجع عن آخر تعديل سينمائي") }
        }
        val selection = field.selection
        if (!selection.collapsed && content.substring(selection.min, selection.max).isNotBlank()) {
            OutlinedButton(onClick = { edit = CinematicEdit(content, selection.min, selection.max) }) {
                Text("تعديل ذكي")
            }
        }
        Text(
            "الكلمات: " + content.trim().split(Regex("\\s+")).count { it.isNotBlank() } +
                " • الأحرف: " + content.length,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    container.db.promptDao().update(
                        prompt.copy(title = title, content = content, updatedAt = System.currentTimeMillis())
                    )
                    container.logActivity("editor", "تعديل موجه: " + title)
                    Toast.makeText(context, "تم الحفظ", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }) { Text("حفظ") }
            OutlinedButton(onClick = {
                if (Exporter.copyToClipboard(context, content))
                    Toast.makeText(context, "تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show()
            }) { Text("نسخ") }
            OutlinedButton(onClick = {
                val res = Exporter.export(context, title, content, "md")
                res.onSuccess { uri -> Exporter.share(context, uri, "text/markdown", "مشاركة الموجه") }
                    .onFailure { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
            }) { Text("تصدير MD") }
        }
    }
}

@Composable
private fun SmartEditDialog(
    container: AppContainer,
    edit: CinematicEdit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var direction by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var includeContext by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preview == null) "المخرج التفاعلي" else "معاينة التعديل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val replacement = preview
                if (replacement != null) {
                    ComparisonPassage("النص الأصلي", edit.selection)
                    HorizontalDivider()
                    ComparisonPassage("النص المعدّل", replacement)
                } else {
                    Text("يُرسل التحديد وتوجيهك إلى المزوّد السحابي المختار. لا يُرسل النص المحيط إلا إذا اخترت إرفاقه.")
                    Text(edit.selection.take(240) + if (edit.selection.length > 240) "…" else "")
                    Text("إجراءات سريعة", modifier = Modifier.semantics { heading() })
                    CinematicAction.entries.forEach { action ->
                        FilterChip(
                            selected = direction == action.direction,
                            enabled = !busy && edit.selection.length <= 8_000,
                            onClick = { direction = action.direction; error = null },
                            label = { Text(action.label) }
                        )
                    }
                    OutlinedTextField(
                        value = direction,
                        onValueChange = { direction = it.take(500); error = null },
                        enabled = !busy,
                        label = { Text("توجيه التعديل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (edit.contextBefore.isNotBlank() || edit.contextAfter.isNotBlank()) {
                        Row {
                            Checkbox(
                                checked = includeContext,
                                onCheckedChange = { includeContext = it; error = null },
                                enabled = !busy
                            )
                            Text("إرفاق سياق المقطع (اختياري)", Modifier.padding(top = 12.dp))
                        }
                        if (includeContext) {
                            if (edit.contextBefore.isNotEmpty()) ComparisonPassage("السياق المرسل قبل التحديد", edit.contextBefore)
                            if (edit.contextAfter.isNotEmpty()) ComparisonPassage("السياق المرسل بعد التحديد", edit.contextAfter)
                        }
                    }
                    if (edit.selection.length > 8_000) Text("حدّد مقطعاً لا يتجاوز ٨٠٠٠ حرف.")
                    if (busy) Text("جارٍ إعداد الاقتراح…")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            val replacement = preview
            if (replacement != null) Button(onClick = { onApply(replacement) }) { Text("تطبيق") }
            else Button(
                enabled = !busy && direction.isNotBlank() && edit.selection.length <= 8_000,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val settings = container.engines.cloudGate().getOrNull()
                            if (settings == null) {
                                error = "فعّل الموافقة السحابية من الإعدادات أولاً."
                                return@launch
                            }
                            val engine = container.engines.engineById(settings.engineGeneral)
                            if (engine.isLocal || !engine.status(settings).isReady) {
                                error = "اختر مزوّداً سحابياً جاهزاً للتوليد العام من شاشة النماذج."
                                return@launch
                            }
                            preview = engine.generate(edit.request(direction, includeContext)).getOrThrow().trim()
                                .takeIf { it.isNotBlank() } ?: kotlin.error("Empty rewrite")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            error = "تعذّر إعداد الاقتراح. تحقّق من اتصالك وإعدادات المزوّد ثم أعد المحاولة."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (error == null) "اقتراح تعديل" else "إعادة المحاولة") }
        },
        dismissButton = {
            if (preview != null) TextButton(onClick = { preview = null }) { Text("تعديل التوجيه") }
            else TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ComparisonPassage(label: String, passage: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Text(passage, style = MaterialTheme.typography.bodyMedium)
}
