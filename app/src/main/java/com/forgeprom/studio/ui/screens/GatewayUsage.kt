package com.forgeprom.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.ai.OpenAiCompatEngine
import com.forgeprom.studio.core.ai.UsageHttpException
import com.forgeprom.studio.domain.UsageDay
import com.forgeprom.studio.domain.UsageSummary
import com.forgeprom.studio.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale

private sealed interface UsageState {
    data object Idle : UsageState
    data object Loading : UsageState
    data class Failed(val message: String) : UsageState
    data class Loaded(val days: List<UsageDay>) : UsageState
}

/**
 * بطاقة استهلاك البوابة (آخر 30 يوماً) للحساب المهيّأ نفسه.
 * سجل مراقبة لا فوترة، ويُقرأ فقط بعد الموافقة السحابية.
 */
@Composable
internal fun GatewayUsage(container: AppContainer, cloudConsent: Boolean) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UsageState>(UsageState.Idle) }

    fun load() {
        state = UsageState.Loading
        scope.launch {
            try {
                val settings = container.engines.cloudGate().getOrNull()
                if (settings == null) {
                    state = UsageState.Failed("فعّل الموافقة السحابية في هذه الصفحة، ثم أعد المحاولة.")
                    return@launch
                }
                val engine = container.engines.engineById("cloud.openai_compat") as OpenAiCompatEngine
                if (!engine.status(settings).isReady) {
                    state = UsageState.Failed("احفظ عنوان بوابة Forgeprom ورمز حسابك في قسم مفاتيح API، ثم أعد المحاولة.")
                    return@launch
                }
                state = UsageState.Loaded(parseDays(engine.usageDays()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UsageHttpException) {
                state = UsageState.Failed(when (e.status) {
                    401, 403 -> "لم تقبل البوابة رمز الحساب. تحقّق منه ثم أعد المحاولة."
                    404 -> "سجل الاستهلاك غير مُفعّل على هذه البوابة. اطلب من مشغّلها تفعيل ENABLE_USAGE."
                    429 -> "بلغت حد الطلبات. انتظر قليلاً ثم أعد المحاولة."
                    else -> "لم تتمكن البوابة من جلب الاستهلاك الآن. أعد المحاولة لاحقاً."
                })
            } catch (_: IllegalArgumentException) {
                state = UsageState.Failed("تحقّق من عنوان البوابة؛ يجب أن يبدأ بـ HTTPS، ومن وجود رمز حساب صالح.")
            } catch (_: Exception) {
                state = UsageState.Failed("تعذّر جلب الاستهلاك. تحقّق من الاتصال ثم أعد المحاولة.")
            }
        }
    }

    SectionCard("استهلاك البوابة (آخر ٣٠ يوماً)") {
        Text(
            "عدادات الطلبات والرموز التي يعيدها المزوّد. هذه مراقبة استهلاك وليست فاتورة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val current = state
        val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        if (!cloudConsent) Text("يلزم تفعيل الموافقة السحابية قبل قراءة سجل الاستهلاك.", modifier = live)
        when (current) {
            UsageState.Idle -> Unit
            UsageState.Loading -> {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics { contentDescription = "تحميل سجل الاستهلاك" }
                )
                Text("جارٍ جلب الاستهلاك…", modifier = live)
            }
            is UsageState.Failed -> Text(current.message, modifier = live, color = MaterialTheme.colorScheme.error)
            is UsageState.Loaded -> UsageBody(current.days)
        }
        OutlinedButton(
            enabled = cloudConsent && current != UsageState.Loading,
            onClick = { load() }
        ) {
            Text(when (current) {
                UsageState.Idle -> "عرض الاستهلاك"
                UsageState.Loading -> "جارٍ التحميل…"
                is UsageState.Failed -> "إعادة المحاولة"
                is UsageState.Loaded -> "تحديث الاستهلاك"
            })
        }
    }
}

@Composable
private fun UsageBody(days: List<UsageDay>) {
    val totals = UsageSummary.totals(days)
    val numbers = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("ar")) }
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (totals.requests == 0L) {
        Text("لا يوجد استهلاك مسجل في آخر ٣٠ يوماً لهذا الحساب.", modifier = live)
        return
    }
    Text(
        "الطلبات: " + numbers.format(totals.requests) +
            " • رموز الإدخال: " + numbers.format(totals.promptTokens) +
            " • رموز الإخراج: " + numbers.format(totals.completionTokens),
        modifier = live,
        style = MaterialTheme.typography.bodyMedium
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UsageSummary.byModel(days).take(4).forEach { (model, modelTotals) ->
            Text(
                model + ": " + numbers.format(modelTotals.requests) + " طلباً · " +
                    numbers.format(modelTotals.tokens) + " رمزاً",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseDays(raw: JSONArray): List<UsageDay> = buildList {
    for (i in 0 until raw.length()) {
        val row = raw.optJSONObject(i) ?: continue
        val day = row.optString("day").takeIf { it.isNotBlank() } ?: continue
        add(
            UsageDay(
                day = day,
                model = row.optString("model").ifBlank { "غير معروف" },
                requests = UsageSummary.count(row.opt("requests")),
                promptTokens = UsageSummary.count(row.opt("prompt_tokens")),
                completionTokens = UsageSummary.count(row.opt("completion_tokens"))
            )
        )
    }
}
