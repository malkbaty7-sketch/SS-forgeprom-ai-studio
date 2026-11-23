package com.forgeprom.studio.ui.screens

import com.forgeprom.studio.ui.components.ActionGroup
import com.forgeprom.studio.ui.components.PrimaryActionButton
import com.forgeprom.studio.ui.components.ScreenNotice
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.data.PromptEntity
import com.forgeprom.studio.core.data.ProjectEntity
import com.forgeprom.studio.core.util.Exporter
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.room.withTransaction

/**
 * R9/R10 — شاشة النتيجة: 6 تبويبات + نسخ/تصدير/حفظ في مشروع.
 */
@Composable
fun ResultScreen(container: AppContainer, onSaved: (Long) -> Unit) {
    val session = container.session
    val draft = session.draft
    if (session.promptText.isBlank() || draft == null) {
        ScreenNotice("لا توجد نتيجة لعرضها. ارجع إلى التوليد وأنشئ موجّهًا جديدًا.")
        return
    }
    val report = session.report
    val critique = session.critique
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var promptText by remember { mutableStateOf(session.promptText) }
    var projectName by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }

    val tabs = listOf("البرومبت", "ملخص الفهم", "النقد", "الافتراضات", "الجودة", "الحفظ والتصدير")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1) })
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("البرومبت النهائي — قابل للتحرير اليدوي") },
                        minLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ActionGroup {
                        OutlinedButton(onClick = {
                            if (Exporter.copyToClipboard(context, promptText))
                                Toast.makeText(context, "تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show()
                        }) { Text("نسخ") }
                    }
                }
                1 -> {
                    Section("النية", draft.intent)
                    Section("الملخص", draft.summary)
                    Section("الأهداف", draft.goals)
                    Section("الميزات", draft.features)
                    Section("المتطلبات", draft.requirements)
                    Section("القيود", draft.constraints)
                    Section("الأسئلة المفتوحة", draft.questions)
                }
                2 -> {
                    if (critique == null) { Text("لا نقد متاح") } else {
                        Section("نقاط القوة", critique.strengths)
                        Section("النواقص", critique.weaknesses)
                        Section("المخاطر", critique.risks)
                        Section("اقتراحات التحسين", critique.suggestions)
                    }
                }
                3 -> {
                    if (draft.assumptions.isEmpty()) Text("لا افتراضات — الوصف كان كافياً")
                    draft.assumptions.forEach { Text("• " + it) }
                }
                4 -> {
                    if (report == null) { Text("لا تقرير جودة") } else {
                        Text(
                            "درجة الجودة: " + report.score + " / 100",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (report.score >= 70)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        if (report.missing.isEmpty()) {
                            Text("البرومبت مكتمل الأقسام — لا نواقص بعد الإصلاح التلقائي إن طُبّق.")
                        } else {
                            Section("أقسام ناقصة (خُصم 7 لكل قسم)", report.missing)
                        }
                        Section("تفاصيل الفحص", report.findings)
                    }
                }
                5 -> {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("اسم مشروع لحفظ الموجه فيه (اتركه فارغاً لإنشاء تلقائي)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saving) ScreenNotice("جارٍ الحفظ في المشروع…", loading = true)
                    if (saveError) ScreenNotice("تعذّر الحفظ. أعد المحاولة من زر الحفظ؛ سيبقى النص كما هو.")
                    PrimaryActionButton(
                        enabled = !saving && promptText.isNotBlank(),
                        onClick = {
                            saving = true
                            saveError = false
                            val name = projectName.trim().ifBlank { "موجه — " + draft.summary.take(30) }
                            val content = promptText
                            scope.launch {
                                try {
                                    val projectId = container.db.withTransaction {
                                        val id = container.db.projectDao().insert(
                                            ProjectEntity(name = name, description = draft.summary, kind = "prompt")
                                        )
                                        container.db.promptDao().insert(PromptEntity(
                                            projectId = id, title = draft.summary.take(50), content = content,
                                            goal = session.goal, tech = session.tech,
                                            qualityScore = report?.score ?: 0, isImageInspired = session.isImageInspired
                                        ))
                                        id
                                    }
                                    container.logActivity("save", "حفظ موجه في مشروع: " + name)
                                    Toast.makeText(context, "تم الحفظ في المشروع", Toast.LENGTH_SHORT).show()
                                    onSaved(projectId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    saveError = true
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Text("حفظ في مشروع جديد", modifier = Modifier.padding(start = 8.dp))
                    }

                    ActionGroup {
                        OutlinedButton(onClick = { export(context, container, promptText, "md") }) { Text("Markdown") }
                        OutlinedButton(onClick = { export(context, container, promptText, "txt") }) { Text("TXT") }
                        OutlinedButton(onClick = { export(context, container, promptText, "json") }) { Text("JSON") }
                    }
                    Text(
                        "التصدير بترميز UTF-8 يحافظ على العربية تماماً. JSON يتضمن البرومبت والتحليل معاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, items: List<String>) {
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (items.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { Text("• " + it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Section(title: String, text: String) {
    Section(title, listOf(text))
}

private fun export(context: android.content.Context, container: AppContainer, promptText: String, ext: String) {
    val draft = container.session.draft ?: run {
        Toast.makeText(context, "لا توجد نتيجة للتصدير.", Toast.LENGTH_SHORT).show()
        return
    }
    val draftJson = JSONObject()
        .put("intent", draft.intent)
        .put("summary", draft.summary)
        .put("goals", JSONArray(draft.goals))
        .put("features", JSONArray(draft.features))
        .put("requirements", JSONArray(draft.requirements))
        .put("constraints", JSONArray(draft.constraints))
        .put("assumptions", JSONArray(draft.assumptions))
        .put("questions", JSONArray(draft.questions))
        .toString()

    val content = when (ext) {
        "json" -> Exporter.toJson(promptText, draftJson)
        else -> promptText
    }
    val mime = when (ext) {
        "json" -> "application/json"
        "md" -> "text/markdown"
        else -> "text/plain"
    }
    val result = Exporter.export(context, "Forgeprom-Prompt", content, ext)
    result.onSuccess { uri ->
        Exporter.share(context, uri, mime, "مشاركة البرومبت")
        container.logActivity("export", "تصدير برومبت بصيغة " + ext)
    }.onFailure {
        Toast.makeText(context, "فشل التصدير: " + it.message, Toast.LENGTH_SHORT).show()
    }
}
