package com.forgeprom.studio.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.forgeprom.studio.core.util.EpubExcerpt
import com.forgeprom.studio.core.util.PdfExcerpt
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.util.Exporter
import com.forgeprom.studio.domain.StoryAnalysis
import com.forgeprom.studio.domain.StoryContext
import com.forgeprom.studio.domain.SemanticContext
import com.forgeprom.studio.domain.SceneDraft
import com.forgeprom.studio.domain.SceneFormat
import com.forgeprom.studio.domain.SceneDirection
import com.forgeprom.studio.domain.SceneTone
import com.forgeprom.studio.domain.SceneStyle
import com.forgeprom.studio.domain.SceneLighting
import org.json.JSONArray
import kotlin.math.roundToInt
import com.forgeprom.studio.domain.FinalDraftExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.forgeprom.studio.core.util.ScenePdf

@Composable
internal fun SceneDraftDialog(
    container: AppContainer,
    promptId: Long,
    title: String,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("story_notes", Context.MODE_PRIVATE) }
    val key = "prompt_$promptId"
    var notes by remember(key) { mutableStateOf(preferences.getString(key, "").orEmpty()) }
    var brief by remember { mutableStateOf("") }
    var includeNotes by remember { mutableStateOf(false) }
    var relevantOnly by remember { mutableStateOf(false) }
    val relevantNotes = remember(brief, notes) { StoryContext.retrieve(brief, notes) }
    var semantic by remember(brief, notes) { mutableStateOf<String?>(null) }
    val outgoingNotes = if (!includeNotes) "" else semantic ?: if (relevantOnly) relevantNotes else notes
    var analysis by remember { mutableStateOf<String?>(null) }
    var scene by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun importExcerpt(uri: android.net.Uri?, pdf: Boolean) {
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val excerpt = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            if (pdf) PdfExcerpt.read(context, input, SceneDraft.NOTES_LIMIT)
                            else EpubExcerpt.read(input, SceneDraft.NOTES_LIMIT)
                        }.orEmpty()
                    }
                    if (excerpt.isBlank()) message = "لم يُعثر على نص قابل للقراءة في الملف؛ لا يدعم الاستيراد الصور الممسوحة ضوئياً."
                    else {
                        notes = excerpt
                        includeNotes = false
                        message = "استُبدل حقل الملاحظات بمقتطف من الملف. راجع النص قبل حفظه أو إرساله."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message = "تعذّرت قراءة الملف. يدعم PDF النصي غير المشفّر حتى 8 ميبيبايت."
                } finally {
                    busy = false
                }
            }
        }
    }
    val epubPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importExcerpt(it, false) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importExcerpt(it, true) }
    var direction by remember { mutableStateOf(SceneDirection()) }
    val historyKey = "history_$promptId"
    var history by remember(historyKey) {
        mutableStateOf(runCatching {
            val stored = JSONArray(preferences.getString(historyKey, "[]") ?: "[]")
            List(minOf(stored.length(), 5)) { stored.getString(it) }
        }.getOrDefault(emptyList()))
    }
    var autoSave by remember { mutableStateOf(preferences.getBoolean("autosave_$promptId", true)) }
    var comparison by remember(scene) { mutableStateOf<String?>(null) }
    fun saveVersion(text: String) {
        history = (listOf(text) + history.filterNot { it == text }).take(5)
        preferences.edit().putString(historyKey, JSONArray(history).toString()).apply()
    }
    var format by remember { mutableStateOf(SceneFormat.SCREENPLAY) }
    val draft = scene
    val screenplay = draft != null && runCatching { SceneDraft.validate(draft) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft == null) "توليد مشهد" else "معاينة المشهد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft == null) {
                    OutlinedTextField(brief, { brief = it.take(SceneDraft.BRIEF_LIMIT) }, enabled = !busy,
                        label = { Text("وصف المشهد") }, modifier = Modifier.fillMaxWidth())
                    Text("لوحة المخرج", style = MaterialTheme.typography.titleSmall)
                    Text("الإيقاع: ${direction.pace} من 10")
                    Slider(value = direction.pace.toFloat(), onValueChange = { direction = direction.copy(pace = it.roundToInt()) },
                        enabled = !busy, valueRange = 1f..10f, steps = 8)
                    SceneTone.entries.forEach { tone ->
                        FilterChip(selected = direction.tone == tone, enabled = !busy,
                            onClick = { direction = direction.copy(tone = tone) }, label = { Text(tone.label) })
                    }
                    SceneChoices("صيغة المشهد", SceneFormat.entries, format, !busy, { it.label }) { format = it }
                    SceneChoices("أسلوب الإخراج", SceneStyle.entries, direction.style, !busy, { it.label }) {
                        direction = direction.copy(style = it)
                    }
                    SceneChoices("الإضاءة", SceneLighting.entries, direction.lighting, !busy, { it.label }) {
                        direction = direction.copy(lighting = it)
                    }
                    FilterChip(selected = direction.actorCues, enabled = !busy,
                        onClick = { direction = direction.copy(actorCues = !direction.actorCues) }, label = { Text("إشارات الأداء") })
                    FilterChip(selected = direction.camera, enabled = !busy,
                        onClick = { direction = direction.copy(camera = !direction.camera) }, label = { Text("توجيهات الكاميرا") })
                    FilterChip(selected = direction.sound, enabled = !busy,
                        onClick = { direction = direction.copy(sound = !direction.sound) }, label = { Text("تصميم الصوت") })
                    OutlinedTextField(notes, { notes = it.take(SceneDraft.NOTES_LIMIT) }, enabled = !busy,
                        label = { Text("ذاكرة القصة لهذا الموجّه") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy, onClick = { epubPicker.launch(arrayOf("application/epub+zip")) }) {
                        Text("استيراد مقتطف EPUB إلى الملاحظات")
                    }
                    OutlinedButton(enabled = !busy, onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                        Text("استيراد مقتطف PDF إلى الملاحظات")
                    }
                    TextButton(enabled = !busy, onClick = {
                        val editor = preferences.edit()
                        if (notes.isBlank()) editor.remove(key) else editor.putString(key, notes)
                        editor.apply()
                        message = "تم تحديث الملاحظات المحلية."
                    }) { Text("حفظ الملاحظات محلياً") }
                    Row {
                        Checkbox(includeNotes, { includeNotes = it }, enabled = !busy)
                        Text("إرسال الملاحظات المعروضة مع الطلب", Modifier.padding(top = 12.dp))
                    }
                    if (includeNotes) {
                        Row {
                            Checkbox(relevantOnly, { relevantOnly = it; semantic = null }, enabled = !busy)
                            Text("إرسال المقتطفات المطابقة فقط", Modifier.padding(top = 12.dp))
                        }
                        if (semantic == null && relevantOnly) Text(relevantNotes.ifBlank { "لا توجد مطابقة؛ لن تُرسل ملاحظات مع هذا المشهد." })
                        OutlinedButton(enabled = !busy && brief.isNotBlank() && notes.isNotBlank(), onClick = {
                            busy = true
                            message = ""
                            val candidates = SemanticContext.passages(notes)
                            val request = SemanticContext.request(brief, candidates)
                            scope.launch {
                                try {
                                    val settings = container.engines.cloudGate().getOrNull()
                                    if (settings == null) {
                                        message = "فعّل الموافقة السحابية من الإعدادات أولاً."
                                        return@launch
                                    }
                                    val engine = container.engines.engineById(settings.engineGeneral)
                                    if (engine.isLocal || !engine.status(settings).isReady) {
                                        message = "اختر مزوّداً سحابياً جاهزاً من شاشة النماذج."
                                        return@launch
                                    }
                                    semantic = SemanticContext.select(engine.generate(request).getOrThrow(), candidates)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    message = "تعذّر انتقاء السياق الدلالي. لم يُغيّر خيار الإرفاق الحالي."
                                } finally {
                                    busy = false
                                }
                            }
                        }) { Text("إرسال الوصف والملاحظات للانتقاء الدلالي") }
                        semantic?.let { selected ->
                            Text(selected.ifBlank { "لم يجد النموذج مقتطفات مناسبة؛ لن تُرفق ملاحظات عند التوليد." })
                            TextButton(enabled = !busy, onClick = { semantic = null }) { Text("إلغاء الانتقاء الدلالي") }
                        }
                    }
                    Text("يُرسل وصف المشهد للمزوّد السحابي. لا يُرسل نص المحرّر. تُرسل الملاحظات فقط عند اختيار إرفاقها.")
                    Row {
                        Checkbox(autoSave, { checked ->
                            autoSave = checked
                            preferences.edit().putBoolean("autosave_$promptId", checked).apply()
                        }, enabled = !busy)
                        Text("حفظ المسودات المولّدة تلقائياً على الجهاز", Modifier.padding(top = 12.dp))
                    }
                    if (history.isNotEmpty()) {
                        Text("آخر المسودات المحفوظة", style = MaterialTheme.typography.titleSmall)
                        history.forEachIndexed { index, saved ->
                            TextButton(enabled = !busy, onClick = { scene = saved }) {
                                Text("${index + 1}. ${saved.lineSequence().first().removePrefix(".").take(60)}")
                            }
                        }
                        TextButton(enabled = !busy, onClick = {
                            history = emptyList()
                            preferences.edit().remove(historyKey).apply()
                        }) { Text("مسح سجل المسودات") }
                    }
                    OutlinedButton(enabled = !busy && notes.isNotBlank(), onClick = {
                        busy = true
                        message = ""
                        analysis = null
                        val request = StoryAnalysis.request(notes)
                        scope.launch {
                            try {
                                val settings = container.engines.cloudGate().getOrNull()
                                if (settings == null) {
                                    message = "فعّل الموافقة السحابية من الإعدادات أولاً."
                                    return@launch
                                }
                                val engine = container.engines.engineById(settings.engineGeneral)
                                if (engine.isLocal || !engine.status(settings).isReady) {
                                    message = "اختر مزوّداً سحابياً جاهزاً من شاشة النماذج."
                                    return@launch
                                }
                                analysis = StoryAnalysis.review(engine.generate(request).getOrThrow())
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                message = "تعذّر استخراج الشخصيات والأحداث. راجع إعدادات المزوّد وأعد المحاولة."
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("إرسال الملاحظات لاستخراج الشخصيات والأحداث") }
                    analysis?.let { proposed ->
                        Text("اقتراح قابل للمراجعة؛ لم تُعدّل ملاحظاتك بعد.")
                        OutlinedTextField(proposed, { analysis = it.take(SceneDraft.NOTES_LIMIT) },
                            label = { Text("الشخصيات والأحداث المقترحة") }, modifier = Modifier.fillMaxWidth())
                        TextButton(enabled = proposed.isNotBlank(), onClick = {
                            notes = proposed
                            includeNotes = false
                            analysis = null
                        }) { Text("استبدال الملاحظات بالاقتراح") }
                        TextButton(onClick = { analysis = null }) { Text("تجاهل الاقتراح") }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotEmpty()) Text(message)
                } else {
                    comparison?.let { older ->
                        Text("النسخة المرجعية", style = MaterialTheme.typography.titleSmall)
                        Text(older)
                        HorizontalDivider()
                        Text("المسودة المعروضة", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(draft)
                    val alternatives = history.filterNot { it == draft }
                    if (alternatives.isNotEmpty()) {
                        SceneChoices("المقارنة مع", listOf<String?>(null) + alternatives, comparison, !busy,
                            { entry -> entry?.lineSequence()?.first()?.take(55) ?: "بدون مقارنة" }) { comparison = it }
                    }
                    TextButton(enabled = !busy && draft !in history, onClick = { saveVersion(draft) }) {
                        Text(if (draft in history) "محفوظة محلياً" else "حفظ نسخة في سجل المسودات")
                    }
                    if (screenplay) {
                        OutlinedButton(onClick = {
                            Exporter.export(context, title, draft, "fountain")
                                .onSuccess { Exporter.share(context, it, "text/plain", "مشاركة المشهد") }
                                .onFailure { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
                        }) { Text("تصدير Fountain") }
                        OutlinedButton(onClick = {
                            Exporter.export(context, title, FinalDraftExport.render(draft), "fdx")
                                .onSuccess { Exporter.share(context, it, "application/xml", "مشاركة مشهد Final Draft") }
                                .onFailure { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
                        }) { Text("تصدير Final Draft (FDX)") }
                    } else {
                        OutlinedButton(onClick = {
                            Exporter.export(context, title, draft, "txt")
                                .onSuccess { Exporter.share(context, it, "text/plain", "مشاركة المشهد") }
                                .onFailure { Toast.makeText(context, "فشل التصدير", Toast.LENGTH_SHORT).show() }
                        }) { Text("تصدير TXT") }
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) {
                                    Exporter.exportBytes(context, title, ScenePdf.render(draft), "pdf").getOrThrow()
                                }
                                Exporter.share(context, uri, "application/pdf", "مشاركة المشهد PDF")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "فشل تصدير PDF", Toast.LENGTH_SHORT).show()
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("تصدير PDF") }
                }
            }
        },
        confirmButton = {
            if (draft != null) Button(onClick = { onInsert(draft) }) { Text("إلحاق بالمحرّر") }
            else Button(enabled = !busy && brief.isNotBlank(), onClick = {
                busy = true
                message = ""
                val request = SceneDraft.request(brief, outgoingNotes, direction, format)
                scope.launch {
                    try {
                        val settings = container.engines.cloudGate().getOrNull()
                        if (settings == null) {
                            message = "فعّل الموافقة السحابية من الإعدادات أولاً."
                            return@launch
                        }
                        val engine = container.engines.engineById(settings.engineGeneral)
                        if (engine.isLocal || !engine.status(settings).isReady) {
                            message = "اختر مزوّداً سحابياً جاهزاً من شاشة النماذج."
                            return@launch
                        }
                        val generated = SceneDraft.validate(engine.generate(request).getOrThrow(), request.sceneFormat)
                        if (autoSave) saveVersion(generated)
                        scene = generated
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        message = "تعذّر توليد مسودة بالصيغة المختارة. يمكنك إعادة المحاولة دون فقدان الوصف."
                    } finally {
                        busy = false
                    }
                }
            }) { Text("توليد") }
        },
        dismissButton = {
            TextButton(onClick = { if (draft == null) onDismiss() else scene = null }) {
                Text(if (draft == null) "إلغاء" else "رجوع")
            }
        }
    )
}

@Composable
private fun <T> SceneChoices(title: String, choices: List<T>, selected: T, enabled: Boolean,
                             label: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text("$title: ${label(selected)}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}
