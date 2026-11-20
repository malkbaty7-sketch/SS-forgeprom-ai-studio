package com.forgeprom.studio.ui.screens

import android.content.Context
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
import com.forgeprom.studio.domain.SceneDraft
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
    var scene by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var direction by remember { mutableStateOf(SceneDirection()) }
    val historyKey = "history_$promptId"
    var history by remember(historyKey) {
        mutableStateOf(runCatching {
            val stored = JSONArray(preferences.getString(historyKey, "[]") ?: "[]")
            List(minOf(stored.length(), 5)) { stored.getString(it) }
        }.getOrDefault(emptyList()))
    }
    val draft = scene

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
                    Text("يُرسل وصف المشهد للمزوّد السحابي. لا يُرسل نص المحرّر. تُرسل الملاحظات فقط عند اختيار إرفاقها.")
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
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotEmpty()) Text(message)
                } else {
                    Text(draft)
                    TextButton(enabled = !busy && draft !in history, onClick = {
                        history = (listOf(draft) + history).take(5)
                        preferences.edit().putString(historyKey, JSONArray(history).toString()).apply()
                    }) { Text(if (draft in history) "محفوظة محلياً" else "حفظ نسخة في سجل المسودات") }
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
                val request = SceneDraft.request(brief, if (includeNotes) notes else "", direction)
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
                        scene = SceneDraft.validate(engine.generate(request).getOrThrow())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        message = "تعذّر توليد مسودة Fountain صالحة. يمكنك إعادة المحاولة دون فقدان الوصف."
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
