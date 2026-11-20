package com.forgeprom.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.ai.OpenAiCompatEngine
import com.forgeprom.studio.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray

@Composable
internal fun GatewayInvoices(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var invoices by remember { mutableStateOf<JSONArray?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    SectionCard("فواتير البوابة") {
        OutlinedButton(enabled = !loading, onClick = {
            loading = true
            invoices = null
            message = ""
            scope.launch {
                try {
                    val settings = container.engines.cloudGate().getOrNull()
                    if (settings == null) {
                        message = "فعّل الموافقة السحابية أولًا."
                        return@launch
                    }
                    val engine = container.engines.engineById(settings.engineGeneral)
                    require(engine is OpenAiCompatEngine && engine.status(settings).isReady)
                    invoices = engine.billingInvoices()
                    if (invoices?.length() == 0) message = "لا توجد فواتير لهذا الحساب."
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message = "تعذّر جلب الفواتير. تحقّق من إعدادات البوابة وتفعيل الفوترة."
                } finally {
                    loading = false
                }
            }
        }) { Text(if (loading) "جارٍ التحديث…" else "عرض الفواتير وتحديث حالة الدفع") }
        if (message.isNotEmpty()) Text(message)
        val result = invoices
        if (result != null) for (i in 0 until result.length()) {
            val invoice = result.optJSONObject(i) ?: continue
            val status = when (invoice.optString("status")) {
                "open" -> "غير مدفوعة"
                "paid" -> "مدفوعة"
                "void" -> "ملغاة"
                "uncollectible" -> "متعذّرة التحصيل"
                else -> "غير معروفة"
            }
            Text("${invoice.optString("number")} — $status")
            fun openLink(raw: String) {
                val url = raw.toHttpUrlOrNull() ?: return
                if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
                    url.port != 443 || url.host !in setOf("invoice.stripe.com", "pay.stripe.com")) return
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
                    .onFailure { message = "تعذّر فتح رابط الفاتورة. لا يتم الدفع داخل التطبيق." }
            }
            val payment = invoice.optString("paymentUrl")
            val pdf = invoice.optString("pdfUrl")
            if (payment.startsWith("https://") && invoice.optString("status") == "open") {
                OutlinedButton(onClick = { openLink(payment) }) { Text("الدفع على Stripe") }
            }
            if (pdf.startsWith("https://")) {
                OutlinedButton(onClick = { openLink(pdf) }) { Text("فتح PDF للفاتورة") }
            }
        }
    }
}
