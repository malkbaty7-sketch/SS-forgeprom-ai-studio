package com.forgeprom.studio.ui.screens

import androidx.activity.compose.BackHandler
import com.forgeprom.studio.core.editor.EditorDraft
import com.forgeprom.studio.ui.components.ScreenNotice
import com.forgeprom.studio.ui.components.ActionGroup
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
import com.forgeprom.studio.domain.TokenEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.backup.PromptFile
import com.forgeprom.studio.core.backup.PromptFileData
import com.forgeprom.studio.core.data.PromptVersionEntity
import com.forgeprom.studio.core.util.Exporter
import kotlinx.coroutines.launch

/**
 * R3 — المحرر: تحرير موجه محفوظ مع عدادات وحفظ ونسخ وتصدير.
 */
@Composable
fun EditorScreen(container: AppContainer, promptId: Long, onDone: () -> Unit) {
    var attempt by remember(promptId) { mutableStateOf(0) }
    val record by produceState<Result<com.forgeprom.studio.core.data.PromptEntity?>?>(null, container, promptId, attempt) {
        value = null
        try {
            value = Result.success(container.db.promptDao().byId(promptId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = Result.failure(e)
        }
    }
    val loaded = record
    if (loaded == null) {
        ScreenNotice("جارٍ التحميل…", loading = true)
        return
    }
    if (loaded.isFailure) {
        ScreenNotice("تعذّر تحميل الموجّه.", action = "إعادة المحاولة") { attempt++ }
        return
    }
    val prompt = loaded.getOrNull()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (prompt == null) {
        ScreenNotice("الموجّه غير موجود أو حُذف.", action = "رجوع", onAction = onDone)
        return
    }

    var title by remember { mutableStateOf(prompt.title) }
    var field by remember(promptId) { mutableStateOf(TextFieldValue(prompt.content)) }
    var undo by remember(promptId) { mutableStateOf<TextFieldValue?>(null) }
    var saving by remember(promptId) { mutableStateOf(false) }
    var saveError by remember(promptId) { mutableStateOf(false) }
    val content = field.text
    var edit by remember(promptId) { mutableStateOf<CinematicEdit?>(null) }

    // نسخة احتياطية محلية للتعديلات غير المحفوظة — تُستعاد بعد إغلاق التطبيق.
    var pendingDraft by remember(promptId) {
        val stored = container.drafts.load(promptId)
        if (stored != null && stored.content == prompt.content && stored.title.trim() == prompt.title) {
            container.drafts.clear(promptId)
            mutableStateOf<EditorDraft?>(null)
        } else {
            mutableStateOf(stored)
        }
    }
    var autosavedAt by remember(promptId) { mutableStateOf<Long?>(null) }
    var showExitConfirm by remember(promptId) { mutableStateOf(false) }
    var savedSuccessfully by remember(promptId) { mutableStateOf(false) }
    var showVersions by remember(promptId) { mutableStateOf(false) }
    val versionDao = container.db.promptVersionDao()
    val versions by remember(promptId) { versionDao.recent(promptId) }
        .collectAsState(initial = emptyList<PromptVersionEntity>())

    // حفظ تلقائي بعد توقف الكتابة ١٫٥ ثانية، ولا يبدأ قبل حسم المسودة القديمة.
    LaunchedEffect(content, title, pendingDraft) {
        if (pendingDraft != null) return@LaunchedEffect
        if (content == prompt.content && title.trim() == prompt.title) return@LaunchedEffect
        delay(1500)
        container.drafts.save(promptId, title.trim(), content)
        autosavedAt = System.currentTimeMillis()
    }

    // عند مغادرة الشاشة قبل انقضاء مهلة الحفظ التلقائي (رجوع سريع أو شريط علوي)
    // نكتب المسودة فوراً، إلا إذا كان الحفظ النهائي قد نجح.
    val latestTitle by rememberUpdatedState(title)
    val latestContent by rememberUpdatedState(content)
    val latestPrompt by rememberUpdatedState(prompt)
    DisposableEffect(promptId) {
        onDispose {
            if (!savedSuccessfully) {
                val lastTitle = latestTitle.trim()
                val lastContent = latestContent
                if (lastContent != latestPrompt.content || lastTitle != latestPrompt.title) {
                    container.drafts.save(promptId, lastTitle, lastContent)
                }
            }
        }
    }

    val hasUnsavedChanges = content != prompt.content || title.trim() != prompt.title
    BackHandler(enabled = hasUnsavedChanges) { showExitConfirm = true }

    var showLocalScript by remember(promptId) { mutableStateOf(false) }
    if (showLocalScript) LocalScriptDialog { showLocalScript = false }

    var showWebSearch by remember(promptId) { mutableStateOf(false) }
    if (showWebSearch) WebSearchDialog(container) { showWebSearch = false }

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

    if (showVersions) {
        AlertDialog(
            onDismissRequest = { showVersions = false },
            title = { Text("سجل إصدارات الموجّه") },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "أحدث 20 حفظاً. الاستعادة تضع النص في المحرر فقط؛ لا يتغيّر الموجّه المحفوظ حتى تضغط حفظ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    versions.forEachIndexed { index, version ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "نسخة " + (versions.size - index) + " · " + formatDateTime(version.createdAt),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    version.content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                                        .toString() + " كلمة · " + version.content.length + " حرفاً",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    version.content.replace('\n', ' ').take(140),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                                TextButton(onClick = {
                                    title = version.title
                                    field = TextFieldValue(version.content)
                                    undo = null
                                    showVersions = false
                                }) { Text("استعادة إلى المحرر") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersions = false }) { Text("إغلاق") }
            }
        )
    }

    if (showExitConfirm) {
        ExitWithoutSaveDialog(
            onStay = { showExitConfirm = false },
            onLeave = { showExitConfirm = false; onDone() }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pendingDraft?.let { draft ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("مسودة محلية غير محفوظة", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "وُجدت مسودة من الساعة " + formatClock(draft.savedAt) +
                            " تختلف عن النسخة المحفوظة من هذا الموجّه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ActionGroup {
                        Button(onClick = {
                            if (draft.title.isNotBlank()) title = draft.title
                            field = TextFieldValue(draft.content)
                            container.drafts.clear(promptId)
                            autosavedAt = draft.savedAt
                            pendingDraft = null
                        }) { Text("استعادة المسودة") }
                        OutlinedButton(onClick = {
                            container.drafts.clear(promptId)
                            pendingDraft = null
                            autosavedAt = null
                        }) { Text("تجاهل المسودة") }
                    }
                }
            }
        }
        OutlinedTextField(
            value = title,
            enabled = !saving,
            onValueChange = { title = it },
            label = { Text("عنوان الموجه") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = field,
            enabled = !saving,
            onValueChange = { if (it.text != field.text) undo = null; field = it },
            label = { Text("نص الموجه") },
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { showScene = true }) { Text("توليد مشهد وذاكرة القصة") }
        OutlinedButton(onClick = { showWebSearch = true }) { Text("البحث على الإنترنت") }
        OutlinedButton(onClick = { showLocalScript = true }) { Text("تشغيل سكربت حسابي محلي") }
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
        autosavedAt?.let { savedAt ->
            Text(
                "مسودة محلية محفوظة تلقائياً · الساعة " + formatClock(savedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (saving) ScreenNotice("جارٍ حفظ الموجّه…", loading = true)
        if (saveError) ScreenNotice("تعذّر حفظ الموجّه. لم تُفقد تعديلاتك؛ اضغط حفظ لإعادة المحاولة.")
        ActionGroup {
            Button(enabled = !saving && title.isNotBlank() && content.isNotBlank(), onClick = {
                saving = true
                saveError = false
                val updated = prompt.copy(title = title.trim(), content = content, updatedAt = System.currentTimeMillis())
                scope.launch {
                    try {
                        container.db.promptDao().update(updated)
                        // لقطة نصية لكل حفظ ناجح؛ فشلها لا يُفشل الحفظ نفسه.
                        runCatching {
                            val latest = versionDao.latest(promptId)
                            if (latest == null || latest.content != updated.content || latest.title != updated.title) {
                                versionDao.insert(
                                    PromptVersionEntity(
                                        promptId = promptId,
                                        title = updated.title,
                                        content = updated.content,
                                        createdAt = updated.updatedAt
                                    )
                                )
                                versionDao.trim(promptId, 20)
                            }
                        }
                        savedSuccessfully = true
                        container.drafts.clear(promptId)
                        container.logActivity("editor", "تعديل موجه: " + updated.title)
                        Toast.makeText(context, "تم الحفظ", Toast.LENGTH_SHORT).show()
                        onDone()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        saveError = true
                    } finally {
                        saving = false
                    }
                }
            }) { Text("حفظ") }
            OutlinedButton(onClick = {
                if (Exporter.copyToClipboard(context, content))
                    Toast.makeText(context, "تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show()
            }) { Text("نسخ") }
            if (versions.isNotEmpty()) {
                OutlinedButton(onClick = { showVersions = true }) {
                    Text("سجل الإصدارات (" + versions.size + ")")
                }
            }
            OutlinedButton(onClick = {
                val res = Exporter.export(context, title, content, "md")
                res.onSuccess { uri -> Exporter.share(context, uri, "text/markdown", "مشاركة الموجه") }
                    .onFailure { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
            }) { Text("تصدير MD") }
            OutlinedButton(onClick = {
                val payload = PromptFile.encode(
                    PromptFileData(
                        title = title.trim().ifBlank { "موجّه Forgeprom" },
                        content = content,
                        language = prompt.language,
                        goal = prompt.goal,
                        tech = prompt.tech,
                        exportedAt = java.time.OffsetDateTime.now().toString()
                    )
                )
                Exporter.export(context, title, payload, "forgeprom")
                    .onSuccess { uri ->
                        Exporter.share(context, uri, "application/json", "مشاركة ملف الموجّه")
                    }
                    .onFailure { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
            }) { Text("تصدير .forgeprom") }
        }
    }
}

@Composable
private fun ExitWithoutSaveDialog(onStay: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("خروج بدون حفظ؟") },
        text = {
            Text(
                "تعديلاتك محفوظة كمسودة محلية على هذا الجهاز ويمكن استعادتها عند فتح الموجّه مجدداً، " +
                    "لكن النسخة المحفوظة في المشروع لم تتغيّر بعد."
            )
        },
        confirmButton = { TextButton(onClick = onLeave) { Text("خروج") } },
        dismissButton = { TextButton(onClick = onStay) { Text("متابعة التحرير") } }
    )
}

private fun formatClock(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

private fun formatDateTime(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

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
                    val estimate = TokenEstimator.estimateRequest(
                        listOf(
                            edit.selection,
                            direction,
                            if (includeContext) edit.contextBefore + edit.contextAfter else ""
                        ),
                        800
                    )
                    Text(
                        "تقدير الطلب: ~" + estimate.inputTokens + " رمز إدخال + حتى " +
                            estimate.maxOutputTokens + " إخراج.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
