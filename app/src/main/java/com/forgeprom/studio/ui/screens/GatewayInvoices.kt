package com.forgeprom.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.ai.BillingHttpException
import com.forgeprom.studio.core.ai.OpenAiCompatEngine
import com.forgeprom.studio.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private sealed interface InvoiceState {
    data object Idle : InvoiceState
    data object Loading : InvoiceState
    data class Failed(val message: String) : InvoiceState
    data class Loaded(val invoices: JSONArray) : InvoiceState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GatewayInvoices(container: AppContainer, cloudConsent: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var state by remember { mutableStateOf<InvoiceState>(InvoiceState.Idle) }
    var linkError by remember { mutableStateOf<String?>(null) }

    fun load() {
        state = InvoiceState.Loading
        linkError = null
        scope.launch {
            try {
                val settings = container.engines.cloudGate().getOrNull()
                if (settings == null) {
                    state = InvoiceState.Failed("فعّل الموافقة السحابية في هذه الصفحة، ثم أعد المحاولة.")
                    return@launch
                }
                val engine = container.engines.engineById("cloud.openai_compat") as OpenAiCompatEngine
                if (!engine.status(settings).isReady) {
                    state = InvoiceState.Failed("احفظ عنوان بوابة Forgeprom ورمز حسابك في قسم مفاتيح API، ثم أعد المحاولة.")
                    return@launch
                }
                state = InvoiceState.Loaded(engine.billingInvoices())
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillingHttpException) {
                state = InvoiceState.Failed(when (e.status) {
                    401, 403 -> "لم تقبل البوابة رمز الحساب. تحقّق منه ثم أعد المحاولة."
                    404 -> "الفوترة غير متاحة على هذا العنوان. تحقّق من عنوان البوابة وتفعيل الفوترة."
                    429 -> "بلغت حد الطلبات. انتظر قليلًا ثم أعد المحاولة."
                    else -> "لم تتمكن البوابة من جلب الفواتير الآن. أعد المحاولة لاحقًا."
                })
            } catch (_: IllegalArgumentException) {
                state = InvoiceState.Failed("تحقّق من عنوان البوابة؛ يجب أن يبدأ بـ HTTPS، ومن وجود رمز حساب صالح.")
            } catch (_: Exception) {
                state = InvoiceState.Failed("تعذّر جلب الفواتير. تحقّق من الاتصال ثم أعد المحاولة.")
            }
        }
    }

    fun openLink(url: HttpUrl, message: String) {
        linkError = null
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
        } catch (_: android.content.ActivityNotFoundException) {
            linkError = message
        } catch (_: SecurityException) {
            linkError = message
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        SectionCard("فواتير البوابة", Modifier.widthIn(max = 680.dp)) {
            Text("راجع حالة الدفع أو افتح فاتورة PDF. يتم الدفع على صفحة Stripe خارج التطبيق.")
            val current = state
            val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            if (!cloudConsent) Text("يلزم تفعيل الموافقة السحابية قبل عرض فواتير الحساب.", modifier = live)
            when (current) {
                InvoiceState.Idle -> Unit
                InvoiceState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "تحميل الفواتير" })
                    Text("جارٍ جلب الفواتير…", modifier = live)
                }
                is InvoiceState.Failed -> Text(current.message, modifier = live, color = MaterialTheme.colorScheme.error)
                is InvoiceState.Loaded -> Text(
                    if (current.invoices.length() == 0) "لا توجد فواتير لهذا الحساب حاليًا."
                    else "تم تحديث الفواتير: ${current.invoices.length()}", modifier = live
                )
            }
            OutlinedButton(enabled = cloudConsent && current != InvoiceState.Loading, onClick = { load() }) {
                Text(when (current) {
                    InvoiceState.Idle -> "عرض الفواتير"
                    InvoiceState.Loading -> "جارٍ التحميل…"
                    is InvoiceState.Failed -> "إعادة المحاولة"
                    is InvoiceState.Loaded -> "تحديث الفواتير وحالة الدفع"
                })
            }
            linkError?.let { Text(it, modifier = live, color = MaterialTheme.colorScheme.error) }
            if (current is InvoiceState.Loaded) for (i in 0 until current.invoices.length()) {
                val invoice = current.invoices.getJSONObject(i)
                val number = invoice.textOrNull("number") ?: invoice.textOrNull("id")
                val description = number ?: "${i + 1}"
                val status = invoice.optString("status")
                val statusLabel = when (status) {
                    "open" -> "غير مدفوعة"
                    "paid" -> "مدفوعة"
                    "void" -> "ملغاة"
                    "uncollectible" -> "متعذّرة التحصيل"
                    else -> "غير معروفة"
                }
                HorizontalDivider()
                Text("فاتورة", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                Text("حالة الدفع: $statusLabel")
                val amountField = if (status == "paid") "amountPaid" else "amountDue"
                val amount = (invoice.opt(amountField) as? Number)?.toLong()
                formatInvoiceAmount(amount, invoice.optString("currency"))?.let { value ->
                    Text(if (status == "paid") "المبلغ المدفوع: $value" else "مبلغ الفاتورة: $value")
                }
                val payment = if (status == "open") stripeInvoiceLink(invoice.textOrNull("paymentUrl")) else null
                val pdf = stripeInvoiceLink(invoice.textOrNull("pdfUrl"))
                if (payment != null || pdf != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (payment != null) OutlinedButton(
                        modifier = Modifier.semantics { contentDescription = "الدفع على Stripe للفاتورة $description" },
                        onClick = { openLink(payment, "تعذّر فتح صفحة الدفع. تحقّق من توفر متصفح ثم أعد المحاولة.") }
                    ) { Text("الدفع على Stripe") }
                    if (pdf != null) OutlinedButton(
                        modifier = Modifier.semantics { contentDescription = "فتح PDF للفاتورة $description" },
                        onClick = { openLink(pdf, "تعذّر فتح ملف الفاتورة. تحقّق من توفر متصفح ثم أعد المحاولة.") }
                    ) { Text("فتح PDF") }
                }
                if (status == "open" && payment == null) Text("لا يتوفر رابط دفع صالح لهذه الفاتورة.")
            }
        }
    }
}

private fun JSONObject.textOrNull(key: String): String? = (opt(key) as? String)?.takeIf { it.isNotBlank() }

internal fun stripeInvoiceLink(raw: String?): HttpUrl? = raw?.toHttpUrlOrNull()?.takeIf { url ->
    url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.port == 443 &&
        url.host in setOf("invoice.stripe.com", "pay.stripe.com")
}

internal fun formatInvoiceAmount(minorAmount: Long?, code: String): String? {
    if (minorAmount == null || minorAmount < 0) return null
    val currency = try { Currency.getInstance(code.uppercase(Locale.ROOT)) } catch (_: IllegalArgumentException) { return null }
    val digits = currency.defaultFractionDigits
    if (digits < 0) return null
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("ar")).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(BigDecimal.valueOf(minorAmount, digits))
    return "$number \u2066${currency.currencyCode}\u2069"
}
