package com.forgeprom.studio.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.domain.TokenEstimate

/**
 * سطر تقدير الرموز قبل الإرسال. مكوّن مستقل كي يُختبر ويُعاد استخدامه
 * في المحرر ونافذة المشهد دون تكرار نص التقدير.
 */
@Composable
fun TokenEstimateCard(
    estimate: TokenEstimate,
    modifier: Modifier = Modifier,
    label: String = "تقدير الطلب:"
) {
    Text(
        "$label ~${estimate.inputTokens} رمز إدخال + حتى ${estimate.maxOutputTokens} إخراج.",
        modifier = modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
