package com.forgeprom.studio.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.forgeprom.studio.core.util.EpubBook
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
import com.forgeprom.studio.core.ai.LocalNeuralEngine
import com.forgeprom.studio.core.ai.OpenAiCompatEngine
import kotlinx.coroutines.flow.first
import com.forgeprom.studio.core.util.Exporter
import com.forgeprom.studio.domain.StoryAnalysis
import com.forgeprom.studio.domain.StoryContext
import com.forgeprom.studio.domain.SemanticContext
import com.forgeprom.studio.domain.SceneDraft
import com.forgeprom.studio.domain.SceneFormat
import com.forgeprom.studio.domain.SceneDirection
import com.forgeprom.studio.domain.SceneTone
import com.forgeprom.studio.domain.SceneStyle
import com.forgeprom.studio.domain.TokenEstimator
import com.forgeprom.studio.domain.SceneLighting
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
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
    var showIndex by remember { mutableStateOf(false) }
    var chapterNumber by remember { mutableStateOf("1") }
    var confirmDelete by remember { mutableStateOf(false) }
    var bookParts by remember(key) { mutableStateOf(emptyList<String>()) }
    var uploadedParts by remember(key) { mutableIntStateOf(0) }
    var deletedIndex by remember(key) { mutableStateOf(false) }
    var uploadSession by remember(key) { mutableStateOf<(suspend (String, JSONObject?) -> JSONObject)?>(null) }
    fun clearBookImport() {
        bookParts = emptyList()
        uploadedParts = 0
        deletedIndex = false
        uploadSession = null
    }
    fun storyBookId(): String {
        val bookKey = "story_book_$promptId"
        return preferences.getString(bookKey, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(bookKey, it).apply()
        }
    }
    fun prepareBook(uri: android.net.Uri?, pdf: Boolean) {
        if (uri != null) {
            busy = true
            confirmDelete = false
            clearBookImport()
            message = "جارٍ استخراج نص الكتاب محليًا…"
            scope.launch {
                try {
                    bookParts = withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use {
                            if (pdf) PdfExcerpt.readBook(context, it) else EpubBook.read(it)
                        }
                    }
                    message = if (bookParts.isEmpty()) "لم يُعثر على نص قابل للاستخراج؛ لا يدعم التطبيق OCR."
                        else "جُهّز ${bookParts.size} جزءًا محليًا. لم يُرسل أي نص."
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message = if (!pdf) "تعذّر تجهيز EPUB. يلزم كتاب نصي غير محمي بترتيب قراءة صالح وضمن حدود الفهرس."
                        else "تعذّر تجهيز النص الكامل. يلزم PDF غير مشفّر حتى 8 ميبيبايت و300 صفحة، وأن يتّسع نصّه في حدود الفهرس."
                } finally {
                    busy = false
                }
            }
        }
    }
    val bookPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { prepareBook(it, true) }
    val epubBookPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { prepareBook(it, false) }
    fun uploadBook() {
        busy = true
        message = ""
        scope.launch {
            try {
                val settings = container.engines.cloudGate().getOrNull()
                if (settings == null) {
                    message = "فعّل الموافقة السحابية من الإعدادات أولاً."
                    return@launch
                }
                val engine = container.engines.engineById(settings.engineGeneral)
                if (engine !is OpenAiCompatEngine || !engine.status(settings).isReady) {
                    message = "اضبط بوابة Forgeprom كمزوّد OpenAI-compatible للتوليد العام."
                    return@launch
                }
                val send = uploadSession ?: engine.storyIndexSession(storyBookId()).also { uploadSession = it }
                if (!deletedIndex) {
                    semantic = null
                    send("delete", null)
                    deletedIndex = true
                }
                while (uploadedParts < bookParts.size) {
                    val part = uploadedParts
                    send("chapters", JSONObject().put("chapter", part + 1).put("text", bookParts[part]))
                    uploadedParts++
                }
                message = "اكتملت فهرسة الكتاب (${bookParts.size} جزءًا)."
                clearBookImport()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = if (deletedIndex) "توقف الرفع وقد يكون الفهرس جزئيًا. يمكنك متابعة الأجزاء المتبقية."
                    else "لم يتأكد حذف الفهرس السابق. لم يبدأ رفع النص؛ يمكنك إعادة المحاولة."
            } finally {
                busy = false
            }
        }
    }
    fun indexAction(action: String, payload: JSONObject? = null) {
        busy = true
        message = ""
        scope.launch {
            try {
                val settings = container.engines.cloudGate().getOrNull()
                if (settings == null) {
                    message = "فعّل الموافقة السحابية من الإعدادات أولاً."
                    return@launch
                }
                val engine = container.engines.engineById(settings.engineGeneral)
                if (engine !is OpenAiCompatEngine || !engine.status(settings).isReady) {
                    message = "اضبط بوابة Forgeprom كمزوّد OpenAI-compatible للتوليد العام."
                    return@launch
                }
                val result = engine.storyIndex(storyBookId(), action, payload)
                when (action) {
                    "search" -> {
                        val passages = result.getJSONArray("passages")
                        semantic = (0 until minOf(passages.length(), 5)).joinToString("\n\n") {
                            passages.getJSONObject(it).getString("content")
                        }.take(900)
                        includeNotes = true
                        message = "تم البحث في الفهرس؛ راجع المقتطفات المعروضة قبل التوليد."
                    }
                    "delete" -> {
                        semantic = null
                        message = "حُذف الفهرس من البوابة المهيّأة."
                    }
                    else -> message = "حُفظت ملاحظات الفصل في الفهرس."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "تعذّر إجراء الفهرسة. تحقّق من إعدادات البوابة وتفعيل فهرس القصة."
            } finally {
                busy = false
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
    var queue by remember { mutableStateOf(false) }
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
                    Text("مع النموذج المحلي يبقى التوليد على الجهاز. إذا اخترت مزوّدًا سحابيًا، يُرسل وصف المشهد والملاحظات المرفقة فقط؛ لا يُرسل نص المحرّر.")
                    val estimate = TokenEstimator.estimateRequest(listOf(brief, outgoingNotes), 900)
                    Text(
                        "تقدير الطلب: ~" + estimate.inputTokens + " رمز إدخال + حتى " +
                            estimate.maxOutputTokens + " إخراج.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Row {
                        Checkbox(queue, { queue = it }, enabled = !busy)
                        Text("استخدام طابور Forgeprom عبر مزوّد متوافق مهيّأ للبوابة. قد يحتفظ الخادم بالمهمة بعد إغلاق النافذة.", Modifier.padding(top = 12.dp))
                    }
                    TextButton(enabled = !busy, onClick = { showIndex = !showIndex }) { Text("فهرس القصة على البوابة") }
                    if (showIndex) {
                        Text("الفهرسة ترسل الملاحظات وتحفظها في البوابة. البحث يرسل وصف المشهد. ترسل البوابة النص إلى مزوّد التضمينات.")
                        OutlinedButton(enabled = !busy && bookParts.isEmpty(), onClick = {
                            bookPicker.launch(arrayOf("application/pdf"))
                        }) { Text("تجهيز نص كتاب PDF للفهرسة") }
                        Text("ملف PDF نصي غير مشفّر حتى 8 ميبيبايت و300 صفحة؛ لا يدعم OCR.")
                        OutlinedButton(enabled = !busy && bookParts.isEmpty(), onClick = {
                            epubBookPicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                        }) { Text("تجهيز نص كتاب EPUB للفهرسة") }
                        if (bookParts.isNotEmpty()) {
                            Text("الاستبدال ليس ذريًا: يُحذف الفهرس السابق أولًا، ثم يُرسل النص الكامل القابل للاستخراج إلى البوابة. قد يبقى الفهرس الجديد جزئيًا إذا توقف الرفع.")
                            Text("الأجزاء المؤكدة: $uploadedParts من ${bookParts.size}. المتابعة متاحة ما دامت النافذة مفتوحة.")
                            OutlinedButton(enabled = !busy, onClick = { uploadBook() }) {
                                Text(if (deletedIndex) "متابعة رفع الأجزاء المتبقية" else "تأكيد استبدال الفهرس ورفع النص")
                            }
                            TextButton(enabled = !busy, onClick = {
                                val attempted = uploadSession != null
                                clearBookImport()
                                message = if (attempted) "أُلغي الاستيراد المحلي. لا يعيد الإلغاء الفهرس السابق."
                                    else "أُلغي تجهيز الكتاب دون إرسال النص."
                            }) { Text("التخلي عن الاستيراد") }
                        }
                        OutlinedTextField(chapterNumber, { chapterNumber = it.filter { ch -> ch in '0'..'9' }.take(4) },
                            enabled = !busy, label = { Text("رقم الفصل (يستبدل الفصل إن كان موجوداً)") })
                        val chapter = chapterNumber.toIntOrNull()
                        OutlinedButton(enabled = !busy && bookParts.isEmpty() && notes.isNotBlank() && chapter != null && chapter in 1..1000,
                            onClick = { indexAction("chapters", JSONObject().put("chapter", chapter).put("text", notes)) }) {
                            Text("إرسال الملاحظات لفهرسة الفصل")
                        }
                        OutlinedButton(enabled = !busy && brief.isNotBlank(),
                            onClick = { indexAction("search", JSONObject().put("query", brief)) }) { Text("إرسال الوصف للبحث في الفهرس") }
                        TextButton(enabled = !busy && bookParts.isEmpty(), onClick = { confirmDelete = !confirmDelete }) { Text("حذف فهرس القصة") }
                        if (confirmDelete) {
                            Text("سيُحذف كامل الفهرس لهذا الموجّه من البوابة الحالية.")
                            TextButton(enabled = !busy, onClick = { confirmDelete = false; indexAction("delete") }) { Text("تأكيد الحذف") }
                        }
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
                val request = SceneDraft.request(brief, outgoingNotes, direction, format).copy(useGatewayQueue = queue)
                scope.launch {
                    try {
                        val settings = container.settings.settings.first()
                        val engine = container.engines.engineById(settings.engineGeneral)
                        if (!engine.isLocal && container.engines.cloudGate().isFailure) {
                            message = "فعّل الموافقة السحابية من الإعدادات أولاً."
                            return@launch
                        }
                        if ((engine.isLocal && engine !is LocalNeuralEngine) || !engine.status(settings).isReady) {
                            message = "اختر نموذجًا عصبيًا محليًا مثبّتًا أو مزوّدًا سحابيًا جاهزًا."
                            return@launch
                        }
                        if (request.useGatewayQueue && engine !is OpenAiCompatEngine) {
                            message = "يتطلب الطابور تهيئة مزوّد OpenAI-compatible على عنوان بوابة Forgeprom."
                            return@launch
                        }
                        val generated = SceneDraft.validate(engine.generate(request).getOrThrow(), request.sceneFormat)
                        if (autoSave) saveVersion(generated)
                        scene = generated
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        message = if (request.useGatewayQueue) "لم تصل نتيجة صالحة من الطابور. قد تستمر المهمة على الخادم؛ تحقّق قبل إعادة الإرسال."
                        else "تعذّر توليد مسودة بالصيغة المختارة. يمكنك إعادة المحاولة دون فقدان الوصف."
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
