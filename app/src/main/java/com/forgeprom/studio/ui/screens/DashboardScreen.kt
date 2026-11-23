package com.forgeprom.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.registry.FeatureRegistry
import com.forgeprom.studio.ui.components.ActivityRow
import com.forgeprom.studio.ui.components.EmptyState
import com.forgeprom.studio.ui.components.ErrorState
import com.forgeprom.studio.ui.components.QuickActionCard
import com.forgeprom.studio.ui.components.ScreenHeading
import com.forgeprom.studio.ui.components.SectionCard
import com.forgeprom.studio.ui.components.SkeletonCard
import com.forgeprom.studio.ui.components.StatCard
import com.forgeprom.studio.ui.components.StatusChip
import com.forgeprom.studio.ui.components.WelcomeHeroCard
import com.forgeprom.studio.ui.components.observeScreenData
import com.forgeprom.studio.ui.i18n.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DashboardStats(
    val projects: Int = 0,
    val prompts: Int = 0,
    val activities: Int = 0,
    val readyFeatures: Int = FeatureRegistry.readyCount()
)

/**
 * الرئيسية — لمحة إحصائية + إجراءات سريعة + خط زمني للنشاط + حالة الميزات الصادقة.
 */
@Composable
fun DashboardScreen(
    container: AppContainer,
    onCreate: () -> Unit,
    onProjects: () -> Unit,
    onImage: () -> Unit,
    onAbout: () -> Unit,
    onPromptHistory: () -> Unit
) {
    var retry by remember { mutableStateOf(0) }
    val source = remember(container) { container.db.activityDao().recent(10) }
    val load by observeScreenData(source, retry)
    val stats by produceState(DashboardStats(), container, retry) {
        value = withContext(Dispatchers.IO) {
            DashboardStats(
                projects = container.db.projectDao().count(),
                prompts = container.db.promptDao().count(),
                activities = container.db.activityDao().count()
            )
        }
    }
    val fmt = SimpleDateFormat("d MMM yyyy، HH:mm", Locale.getDefault())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeading(
            "Forgeprom Code Studio",
            subtitle = "مساحة عملك الذكية لبناء أفكارك.",
            icon = Icons.Outlined.AutoAwesome
        )

        WelcomeHeroCard(
            title = "من الفكرة إلى خطة تنفيذ واضحة",
            subtitle = "حلّل طلبك محلياً، راجع الافتراضات، ثم أنشئ موجهاً احترافياً جاهزاً.",
            actionLabel = "ابدأ إنشاء موجه",
            icon = Icons.Outlined.AutoAwesome,
            onAction = onCreate
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Outlined.Folder, stats.projects.toString(), "مشاريع", Modifier.weight(1f))
            StatCard(Icons.Outlined.Description, stats.prompts.toString(), "موجّهات", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Outlined.Verified, stats.readyFeatures.toString(), "ميزات جاهزة", Modifier.weight(1f), highlighted = true)
            StatCard(Icons.Outlined.History, stats.activities.toString(), "نشاطات", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                icon = Icons.Outlined.Folder,
                title = "المشاريع",
                subtitle = "إدارة الموجهات المحفوظة",
                onClick = onProjects,
                compact = true,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Outlined.Image,
                title = "تحليل صورة واجهة",
                subtitle = "محلي أو Gemini بموافقتك",
                onClick = onImage,
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(onClick = onPromptHistory, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.History, contentDescription = null)
            Text("الموجهات السابقة المحفوظة", Modifier.padding(start = 8.dp))
        }

        SectionCard("آخر النشاطات", icon = Icons.Outlined.History) {
            val current = load
            val activities = current?.getOrNull()
            when {
                activities == null && current == null -> SkeletonCard(lines = 3)
                activities == null -> ErrorState("تعذّر تحميل النشاطات.", onRetry = { retry++ })
                activities.isEmpty() -> EmptyState(
                    "لا نشاطات بعد — ابدأ بإنشاء موجه جديد.",
                    icon = Icons.Outlined.Inbox
                )
                else -> activities.forEach { a ->
                    ActivityRow(
                        icon = activityIcon(a.type),
                        title = activityTitle(a.type) + ": " + a.detail.take(60),
                        time = fmt.format(Date(a.at))
                    )
                }
            }
        }

        SectionCard("حالة البناء (صادقة)", icon = Icons.Outlined.Verified) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(
                    "جاهز: " + FeatureRegistry.readyCount() + " ميزة — منها المحرك المحلي دون إنترنت",
                    ready = true
                )
                StatusChip("النموذج العصبي اختياري: استورد حزمة متوافقة من شاشة المحركات", ready = false)
                StatusChip("تحليل الصور: محلي محدود أو Gemini بموافقة", ready = true)
                Text(
                    "اضغط للاطلاع على القائمة الكاملة في «حول التطبيق» من الإعدادات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Text("حول التطبيق وسجل الميزات", Modifier.padding(start = 8.dp))
        }
    }
}

private fun activityIcon(type: String): ImageVector = when (type) {
    "analyze" -> Icons.Outlined.Search
    "generate" -> Icons.Outlined.AutoAwesome
    "save" -> Icons.Outlined.Save
    "export" -> Icons.Outlined.Share
    "image" -> Icons.Outlined.Image
    else -> Icons.Outlined.History
}

private fun activityTitle(type: String): String = when (type) {
    "analyze" -> "تحليل"
    "generate" -> "توليد"
    "save" -> "حفظ"
    "export" -> "تصدير"
    "image" -> "تحليل صورة"
    else -> type
}
