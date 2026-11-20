package com.forgeprom.studio.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.forgeprom.studio.core.backup.BackupDocument
import com.forgeprom.studio.core.backup.BackupRules
import com.forgeprom.studio.ui.components.PhaseNote
import com.forgeprom.studio.ui.components.ScreenHeading
import com.forgeprom.studio.ui.components.ScreenNotice
import com.forgeprom.studio.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * نسخ احتياطي محلي لمشاريع المستخدم وموجّاته إلى ملف JSON، واستعادتها دمجاً.
 *
 * التصدير يمر عبر منتقي ملفات النظام (SAF) — لا يحتاج أي إذن تخزين،
 * والاستعادة لا تحذف بيانات حالية أبداً.
 */
@Composable
fun BackupScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = container.backups

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupDocument?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    fun fail(text: String) {
        error = text
        message = null
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                val json = repository.exportJson()
                if (json.length > BackupRules.MAX_FILE_CHARS) {
                    fail(
                        "حجم بياناتك يتجاوز حد ملف النسخة الاحتياطية (٨ ميغابايت). " +
                            "أرشِف بعض المشاريع أو احذف ما لا تحتاجه ثم أعد المحاولة."
                    )
                    return@launch
                }
                val written = withContext(Dispatchers.IO) { writeText(context, uri, json) }
                if (written) {
                    message = "تم إنشاء النسخة الاحتياطية. احفظ الملف في مكان آمن."
                } else {
                    fail("تعذّر الكتابة إلى الملف المختار. جرّب موقعاً آخر.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail("تعذّر إنشاء النسخة الاحتياطية. أعد المحاولة.")
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    readLimited(context, uri, BackupRules.MAX_FILE_CHARS)
                }
                if (text == null) {
                    fail("تعذّر قراءة الملف، أو أنه يتجاوز الحد الأقصى (٨ ميغابايت).")
                } else {
                    pending = BackupDocument.fromJson(text)
                    pendingJson = text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                fail("الملف ليس نسخة احتياطية صالحة من Forgeprom، أو أن إصداره غير مدعوم.")
            } catch (_: Exception) {
                fail("تعذّر قراءة الملف. تحقّق من صلاحيته ثم أعد المحاولة.")
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading("النسخ الاحتياطي والاستعادة")

        if (busy) ScreenNotice("جارٍ تنفيذ العملية…", loading = true)
        message?.let { ScreenNotice(it) }
        error?.let { ScreenNotice(it) }

        SectionCard("تصدير نسخة احتياطية") {
            Text(
                "ملف JSON واحد يحتوي مشاريعك وموجّاتك وتحليلاتك وملفات المحرر. " +
                    "لا يحتوي مفاتيح API ولا إعدادات الجهاز ولا مسودات غير محفوظة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = !busy,
                onClick = { exportLauncher.launch("forgeprom-backup-" + LocalDate.now() + ".json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تصدير نسخة احتياطية") }
        }

        SectionCard("استعادة من ملف") {
            Text(
                "الاستعادة دمج فقط: تُضاف المشاريع غير الموجودة، ولا يُحذف أو يُستبدل أي شيء. " +
                    "المشروع الموجود مسبقاً (بالاسم وتاريخ الإنشاء نفسيهما) يُتجاهل مع ملحقاته.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("اختيار ملف استعادة") }
        }

        PhaseNote(
            "الملف يُقرأ ويُكتب عبر منتقي ملفات النظام، فلا يحتاج التطبيق إذن تخزين. " +
                "احتفظ بالنسخة في مكان تثق به؛ فهي تحتوي نصوص مشاريعك."
        )
    }

    pending?.let { document ->
        AlertDialog(
            onDismissRequest = { if (!busy) { pending = null; pendingJson = null } },
            title = { Text("تأكيد الاستعادة") },
            text = {
                Text(
                    "الملف يحتوي " + document.projects.size + " مشروعاً و" +
                        document.prompts.size + " موجّهاً. سيُضاف منها ما ليس موجوداً فقط، " +
                        "ولن تُحذف بياناتك الحالية. هل تريد المتابعة؟"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val json = pendingJson ?: return@TextButton
                        busy = true
                        error = null
                        message = null
                        scope.launch {
                            try {
                                val report = repository.restoreJson(json)
                                message = "اكتملت الاستعادة: " + report.projectsRestored + " مشروعاً، " +
                                    report.promptsRestored + " موجّهاً، " +
                                    report.attachmentsRestored + " عنصراً ملحقاً." +
                                    if (report.projectsSkipped > 0) {
                                        " تُجوهل " + report.projectsSkipped + " مشروعاً موجوداً مسبقاً."
                                    } else ""
                                pending = null
                                pendingJson = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                fail("تعذّرت الاستعادة. لم تُحفظ أي تغييرات؛ أعد المحاولة أو جرّب ملفاً آخر.")
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("استعادة") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pending = null; pendingJson = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
    } != null
}.getOrDefault(false)

private fun readLimited(context: Context, uri: Uri, maxChars: Int): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val reader = input.reader(Charsets.UTF_8)
        val buffer = CharArray(8192)
        val collected = StringBuilder()
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            collected.append(buffer, 0, read)
            if (collected.length > maxChars) return null
        }
        collected.toString()
    }
}.getOrNull()
