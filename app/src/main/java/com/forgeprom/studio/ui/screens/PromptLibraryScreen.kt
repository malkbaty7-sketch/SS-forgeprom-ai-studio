package com.forgeprom.studio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.data.PromptLibraryItem
import com.forgeprom.studio.core.util.Exporter
import com.forgeprom.studio.ui.components.EmptyState
import com.forgeprom.studio.ui.components.PrimaryActionButton
import com.forgeprom.studio.ui.components.ScreenHeading
import com.forgeprom.studio.ui.components.ScreenNotice
import com.forgeprom.studio.ui.components.observeScreenData
import com.forgeprom.studio.domain.ArabicSearch
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PromptLibraryScreen(
    container: AppContainer,
    onOpenPrompt: (Long) -> Unit
) {
    val source: Flow<List<PromptLibraryItem>> = remember(container) { container.db.promptDao().library() }
    var retry by remember { mutableStateOf(0) }
    val result by observeScreenData(source, retry)
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("d MMM yyyy، HH:mm", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeading(
            title = "الموجهات السابقة",
            subtitle = "افتح أي موجه محفوظ أو انسخه مجدداً من مكتبتك.",
            icon = Icons.Outlined.History
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "مسح البحث")
                    }
                }
            },
            label = { Text("ابحث في الموجهات") },
            placeholder = { Text("العنوان أو المشروع أو محتوى الموجه") }
        )

        val loaded = result
        when {
            loaded == null -> ScreenNotice("جارٍ تحميل الموجهات…", loading = true)
            loaded.isFailure -> ScreenNotice(
                "تعذّر تحميل الموجهات المحفوظة.",
                action = "إعادة المحاولة"
            ) { retry++ }
            else -> {
                val prompts = loaded.getOrNull().orEmpty()
                val visible = remember(prompts, query) {
                    if (query.isBlank()) prompts
                    else prompts.filter { prompt ->
                        ArabicSearch.matches(
                            listOf(prompt.title, prompt.projectName, prompt.content, prompt.goal, prompt.tech)
                                .joinToString(" "),
                            query
                        )
                    }
                }
                if (visible.isEmpty()) {
                    EmptyState(
                        text = if (prompts.isEmpty())
                            "لا توجد موجهات محفوظة بعد. احفظ الموجه في مشروع ليظهر هنا ويمكنك الرجوع إليه ونسخه لاحقاً."
                        else "لا توجد نتائج مطابقة. جرّب كلمات بحث أخرى.",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.History
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { it.id }) { prompt ->
                            PromptLibraryCard(
                                prompt = prompt,
                                dateLabel = formatter.format(Date(prompt.updatedAt)),
                                onOpen = { onOpenPrompt(prompt.id) },
                                onCopy = {
                                    val copied = Exporter.copyToClipboard(context, prompt.content)
                                    Toast.makeText(
                                        context,
                                        if (copied) "نُسخ الموجه إلى الحافظة" else "تعذّر نسخ الموجه",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptLibraryCard(
    prompt: PromptLibraryItem,
    dateLabel: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(prompt.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        "المشروع: ${prompt.projectName} • ${prompt.qualityScore}/100",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                prompt.content.take(220) + if (prompt.content.length > 220) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpen) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Text("فتح", modifier = Modifier.padding(start = 6.dp))
                }
                PrimaryActionButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("نسخ", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
