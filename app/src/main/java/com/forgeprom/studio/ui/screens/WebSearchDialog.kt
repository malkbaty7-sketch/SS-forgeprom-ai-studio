package com.forgeprom.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.forgeprom.studio.ui.i18n.Text
import com.forgeprom.studio.ui.i18n.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.ai.OpenAiCompatEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray

@Composable
internal fun WebSearchDialog(container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<JSONArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("البحث على الإنترنت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("عند الضغط على زر البحث، تُرسل هذه العبارة فقط إلى بوابة Forgeprom ومنها إلى Brave Search. لا يُرسل نص المحرّر أو ملاحظات القصة.")
                OutlinedTextField(query, {
                    if (it.length <= 300) query = it
                    results = null
                    message = ""
                }, enabled = !busy, singleLine = true, label = { Text("عبارة البحث") }, modifier = Modifier.fillMaxWidth())
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotEmpty()) Text(message)
                val items = results
                if (items != null) for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val url = item.optString("url").toHttpUrlOrNull() ?: continue
                    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) continue
                    HorizontalDivider()
                    Text(item.optString("title"), style = MaterialTheme.typography.titleSmall)
                    Text(item.optString("snippet"))
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
                            .onFailure { message = "تعذّر فتح الرابط في المتصفح." }
                    }) { Text("فتح المصدر: ${url.host}") }
                }
            }
        },
        confirmButton = {
            val trimmed = query.trim()
            Button(enabled = !busy && trimmed.isNotEmpty() && trimmed.none { it.code < 32 || it.code == 127 }, onClick = {
                busy = true
                results = null
                message = ""
                scope.launch {
                    try {
                        val settings = container.engines.cloudGate().getOrNull()
                        if (settings == null) {
                            message = "فعّل الموافقة السحابية من الإعدادات قبل البحث."
                            return@launch
                        }
                        val engine = container.engines.engineById("cloud.openai_compat")
                        if (engine !is OpenAiCompatEngine || !engine.status(settings).isReady) {
                            message = "اضبط عنوان بوابة Forgeprom ورمز حسابك في إعدادات OpenAI-compatible."
                            return@launch
                        }
                        results = engine.webSearch(trimmed)
                        if (results?.length() == 0) message = "لم يُعثر على نتائج. جرّب عبارة أخرى."
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IllegalStateException) {
                        message = e.message ?: "تعذّر البحث الآن."
                    } catch (_: Exception) {
                        message = "تعذّر البحث. تحقّق من الاتصال وعنوان البوابة."
                    } finally {
                        busy = false
                    }
                }
            }) { Text("إرسال عبارة البحث") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
