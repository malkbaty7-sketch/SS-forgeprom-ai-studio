package com.forgeprom.studio.ui.screens

import com.forgeprom.studio.ui.components.ActionGroup
import com.forgeprom.studio.ui.components.ScreenHeading
import com.forgeprom.studio.ui.components.ScreenNotice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.ai.EngineStatus
import com.forgeprom.studio.core.settings.AppSettings
import com.forgeprom.studio.ui.components.PhaseNote
import com.forgeprom.studio.ui.components.SectionCard
import com.forgeprom.studio.ui.components.StatusChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * R5 — مركز المحركات: الحالة الصادقة + المحرك الافتراضي لكل مهمة.
 */
@Composable
fun ModelsScreen(container: AppContainer) {
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<List<EngineStatus>?>(null) }
    var statusError by remember { mutableStateOf(false) }

    var modelBusy by remember { mutableStateOf(false) }
    var modelMessage by remember { mutableStateOf("") }
    var modelRevision by remember { mutableStateOf(0) }
    fun changeModel(success: String, action: suspend () -> Unit) {
        modelBusy = true
        modelMessage = ""
        scope.launch {
            try {
                action()
                modelMessage = success
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                modelMessage = "تعذّر تغيير النموذج. تحقّق من توافق الحزمة والمساحة المتاحة."
            } catch (_: LinkageError) {
                modelMessage = "مكتبة الاستدلال غير متاحة على هذا الجهاز."
            } finally {
                modelBusy = false
                modelRevision++
            }
        }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) changeModel("ثُبّت النموذج محليًا.") { container.neuralEngine.install(uri) }
    }
    val neuralReady = statuses.orEmpty().any { it.id == container.neuralEngine.id && it.isReady }

    LaunchedEffect(settings.cloudConsent, modelRevision) {
        statuses = null
        statusError = false
        try {
            statuses = container.engines.allEngines().map { it.status(settings) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            statusError = true
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeading("المحركات")

        if (statusError) ScreenNotice("تعذّر قراءة حالة المحركات.", action = "إعادة المحاولة") { modelRevision++ }
        else if (statuses == null) ScreenNotice("جارٍ قراءة حالة المحركات…", loading = true)
        statuses.orEmpty().forEach { st ->
            Card(Modifier.padding(0.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionGroup {
                        Text(st.label, style = MaterialTheme.typography.titleSmall)
                        StatusChip(label = if (st.isReady) "جاهز" else "غير جاهز", ready = st.isReady)
                    }
                    Text(
                        st.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SectionCard("النموذج العصبي على الجهاز") {
            Text("استورد حزمة MediaPipe LLM بصيغة .task من مصدر موثوق (حتى 4 جيبيبايت). لا تُرفق حزمة مع التطبيق، ولا يُرسل النص إلى الشبكة عند استخدام النموذج المحلي.")
            OutlinedButton(enabled = !modelBusy && container.neuralEngine.supportedDevice, onClick = {
                modelPicker.launch(arrayOf("*/*"))
            }) { Text(if (modelBusy) "جارٍ تجهيز النموذج…" else "استيراد نموذج محلي") }
            if (neuralReady) OutlinedButton(enabled = !modelBusy, onClick = {
                changeModel("حُذف النموذج من الجهاز.") { container.neuralEngine.remove() }
            }) { Text("إزالة النموذج") }
            if (modelMessage.isNotEmpty()) Text(modelMessage)
        }

        SectionCard("المحرك الافتراضي لكل مهمة") {
            listOf(
                "general" to "التوليد العام",
                "code" to "توليد الكود",
                "analysis" to "التحليل"
            ).forEach { (task, label) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    val current = when (task) {
                        "general" -> settings.engineGeneral
                        "code" -> settings.engineCode
                        else -> settings.engineAnalysis
                    }
                    val options = buildList {
                        add("local.rules" to "محلي قاعدي")
                        if (task == "general") add(container.neuralEngine.id to "محلي عصبي")
                        add("cloud.gemini" to "Gemini")
                        add("cloud.openai_compat" to "متوافق OpenAI")
                    }
                    options.chunked(2).forEach { group ->
                        ActionGroup {
                            group.forEach { (id, name) ->
                                FilterChip(
                                    selected = current == id,
                                    enabled = id != container.neuralEngine.id || (neuralReady && !modelBusy),
                                    onClick = { scope.launch { container.settings.setEngineForTask(task, id) } },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }
            }
        }

        PhaseNote(
            "المحرك القاعدي والنموذج العصبي المثبّت يعملان دون إنترنت. المحركات السحابية لا تُستخدم إلا بعد موافقتك الصريحة وإدخال مفتاحك."
        )
    }
}
