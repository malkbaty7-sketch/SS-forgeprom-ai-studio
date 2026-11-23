package com.forgeprom.studio.ui.screens

import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.forgeprom.studio.ui.components.ScreenNotice
import com.forgeprom.studio.ui.components.observeScreenData
import kotlinx.coroutines.flow.combine
import com.forgeprom.studio.ui.components.ScreenHeading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.util.Exporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * R2 — تفاصيل المشروع: الموجهات (فتح في المحرر) والتحليلات والتصدير.
 */
@Composable
fun ProjectDetailScreen(
    container: AppContainer,
    projectId: Long,
    onOpenEditor: (Long) -> Unit,
    onOpenConversations: (Long) -> Unit
) {
    var attempt by remember(projectId) { mutableStateOf(0) }
    val record by produceState<Result<com.forgeprom.studio.core.data.ProjectEntity?>?>(null, container, projectId, attempt) {
        value = null
        try {
            value = Result.success(container.db.projectDao().byId(projectId))
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
        ScreenNotice("تعذّر تحميل المشروع.", action = "إعادة المحاولة") { attempt++ }
        return
    }
    val project = loaded.getOrNull()
    val source = remember(container, projectId) {
        container.db.promptDao().forProject(projectId).combine(container.db.analysisDao().forProject(projectId)) { prompts, analyses ->
            prompts to analyses
        }
    }
    var retryContents by remember(projectId) { mutableStateOf(0) }
    val contents by observeScreenData(source, retryContents)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = SimpleDateFormat("d MMM yyyy، HH:mm", Locale.getDefault())

    if (project == null) {
        ScreenNotice("المشروع غير موجود أو حُذف.")
        return
    }

    var editMemory by remember(projectId) { mutableStateOf(false) }
    var memoryText by remember(projectId) { mutableStateOf(project.description) }
    var memoryError by remember(projectId) { mutableStateOf<String?>(null) }
    var savingMemory by remember(projectId) { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ScreenHeading(project.name)
                if (project.description.isNotBlank())
                    Text(project.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "آخر تحديث: " + fmt.format(Date(project.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "ذاكرة المشروع هذه تُستخدم تلقائياً في محادثاته لتقليل تكرار شرح السياق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { onOpenConversations(projectId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("محادثات المشروع")
                }
                OutlinedButton(onClick = { memoryText = project.description; memoryError = null; editMemory = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("تعديل ذاكرة المشروع")
                }
            }
        }

        val current = contents
        val lists = current?.getOrNull()
        if (lists == null) {
            item {
                if (current == null) ScreenNotice("جارٍ تحميل محتويات المشروع…", loading = true)
                else ScreenNotice("تعذّر تحميل محتويات المشروع.", action = "إعادة المحاولة") { retryContents++ }
            }
            return@LazyColumn
        }
        val (prompts, analyses) = lists

        item { Text("الموجهات (" + prompts.size + ")", style = MaterialTheme.typography.titleMedium) }
        if (prompts.isEmpty()) {
            item {
                Text("لا موجّهات في هذا المشروع بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(prompts, key = { "prompt-${it.id}" }) { p ->
            Card(onClick = { onOpenEditor(p.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val res = Exporter.export(context, p.title, p.content, "md")
                            res.onSuccess { uri ->
                                Exporter.share(context, uri, "text/markdown", "مشاركة الموجه")
                            }
                        }) { Icon(Icons.Filled.Share, contentDescription = "تصدير ومشاركة") }
                    }
                    Text(
                        "جودة: " + p.qualityScore + "/100 • " + p.goal +
                            (if (p.tech.isNotBlank()) " • " + p.tech else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        p.content.take(120) + if (p.content.length > 120) "…" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { onOpenEditor(p.id) }) { Text("فتح في المحرر") }
                }
            }
        }

        if (analyses.isNotEmpty()) {
            item { Text("التحليلات (" + analyses.size + ")", style = MaterialTheme.typography.titleMedium) }
            items(analyses, key = { "analysis-${it.id}" }) { a ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(a.summary.take(100), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "النية: " + a.intent + " • " + fmt.format(Date(a.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (editMemory) {
        AlertDialog(
            onDismissRequest = { if (!savingMemory) editMemory = false },
            title = { Text("ذاكرة المشروع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اكتب الحقائق والقرارات والتقنيات التي يجب أن تبقى مرجعاً مشتركاً لكل محادثات المشروع. لا تحفظ كلمة مرور أو مفتاحاً سرياً هنا.")
                    OutlinedTextField(
                        value = memoryText,
                        onValueChange = { memoryText = it.take(2_000); memoryError = null },
                        enabled = !savingMemory,
                        label = { Text("معلومات المشروع الثابتة") },
                        supportingText = { Text("${memoryText.length}/2000") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    memoryError?.let { ScreenNotice(it) }
                }
            },
            confirmButton = {
                TextButton(enabled = !savingMemory, onClick = {
                    savingMemory = true
                    scope.launch {
                        try {
                            val clean = memoryText.trim()
                            check(container.db.projectDao().updateDetails(
                                project.id, project.name, clean, project.tags, System.currentTimeMillis()
                            ) == 1)
                            editMemory = false
                            attempt++
                        } catch (_: Exception) {
                            memoryError = "تعذّر حفظ ذاكرة المشروع؛ أعد المحاولة."
                        } finally {
                            savingMemory = false
                        }
                    }
                }) { Text(if (savingMemory) "جارٍ الحفظ…" else "حفظ الذاكرة") }
            },
            dismissButton = {
                TextButton(enabled = !savingMemory, onClick = { editMemory = false }) { Text("إلغاء") }
            }
        )
    }
}
