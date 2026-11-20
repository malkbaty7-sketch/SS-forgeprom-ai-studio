package com.forgeprom.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.R
import com.forgeprom.studio.ui.components.ActionGroup
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.backup.PromptFile
import com.forgeprom.studio.core.data.ProjectEntity
import com.forgeprom.studio.core.data.PromptEntity
import com.forgeprom.studio.core.util.UriText
import com.forgeprom.studio.domain.ProjectFilter
import com.forgeprom.studio.domain.ProjectFilterMode
import com.forgeprom.studio.domain.ProjectFilters
import com.forgeprom.studio.domain.ProjectSearchFields
import com.forgeprom.studio.domain.ProjectTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import com.forgeprom.studio.ui.components.ScreenNotice
import com.forgeprom.studio.ui.components.observeScreenData

/**
 * R2 — إدارة المشاريع: إنشاء، تثبيت، أرشفة، حذف، فتح التفاصيل.
 * R14 — بحث عربي مطبَّع + تصفية بالنطاق والوسوم.
 */
@Composable
fun ProjectsScreen(container: AppContainer, onOpen: (Long) -> Unit) {
    val dao = container.db.projectDao()
    val lists = remember(dao) { dao.activeProjects().combine(dao.archivedProjects()) { active, archived -> active to archived } }
    var retry by remember { mutableStateOf(0) }
    val load by observeScreenData(lists, retry)
    val scope = rememberCoroutineScope()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleting by remember { mutableStateOf<ProjectEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ProjectFilterMode.ALL) }
    var tag by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var importing by remember { mutableStateOf(false) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    fun mutate(success: String, inForm: Boolean = false, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        formError = null
        scope.launch {
            try {
                operation()
                notice = success
                if (inForm) showForm = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val message = "تعذّر تنفيذ الإجراء. أعد المحاولة من الزر نفسه."
                if (inForm) formError = message else notice = message
            } finally {
                busy = false
            }
        }
    }

    fun openForm(project: ProjectEntity? = null) {
        editing = project
        formError = null
        showForm = true
    }

    fun clearFilters() {
        query = ""
        mode = ProjectFilterMode.ALL
        tag = null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importNotice = null
        importError = null
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    UriText.readLimited(context, uri, PromptFile.MAX_FILE_CHARS)
                }
                if (text == null) {
                    importError = "تعذّرت قراءة الملف أو أنه يتجاوز الحد الأقصى."
                } else {
                    val data = PromptFile.decode(text)
                    if (data.content.isBlank()) {
                        importError = "ملف الموجّه لا يحتوي نصاً قابلاً للاستيراد."
                    } else {
                        val cleanTitle = data.title.trim().take(120).ifBlank { "موجّه مستورد" }
                        val projectId = dao.insert(
                            ProjectEntity(
                                name = cleanTitle,
                                description = data.goal.trim().take(2000),
                                kind = "prompt",
                                tags = ProjectTags.normalize(data.tags)
                            )
                        )
                        container.db.promptDao().insert(
                            PromptEntity(
                                projectId = projectId,
                                title = cleanTitle,
                                content = data.content,
                                language = data.language.ifBlank { "ar" },
                                goal = data.goal.trim().take(2000),
                                tech = data.tech.trim().take(2000)
                            )
                        )
                        container.logActivity("import", "استيراد موجّه: " + cleanTitle)
                        importNotice = "استُورد الموجّه في مشروع جديد: " + cleanTitle
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                importError = "الملف ليس ملف موجّه Forgeprom صالحاً أو أن إصداره غير مدعوم."
            } catch (_: Exception) {
                importError = "تعذّر الاستيراد. تحقّق من الملف ثم أعد المحاولة."
            } finally {
                importing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenHeading("المشاريع")
            Button(enabled = !busy, onClick = { openForm() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" جديد")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "مسح البحث")
                    }
                }
            },
            placeholder = { Text("ابحث بالاسم أو الوصف أو الوسم") },
            label = { Text("بحث") }
        )

        OutlinedButton(
            enabled = !busy && !importing,
            onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        ) { Text("استيراد موجّه (.forgeprom)") }

        if (importing) ScreenNotice("جارٍ استيراد الموجّه…", loading = true)
        importNotice?.let { ScreenNotice(it) }
        importError?.let { ScreenNotice(it) }
        if (busy && !showForm) ScreenNotice("جارٍ تحديث المشاريع…", loading = true)
        notice?.let { ScreenNotice(it) }
        val current = load
        val rows = current?.getOrNull()
        if (rows == null) {
            if (current == null) ScreenNotice("جارٍ تحميل المشاريع…", loading = true)
            else ScreenNotice("تعذّر تحميل المشاريع.", action = "إعادة المحاولة") { retry++ }
        } else {
            val (projects, archived) = rows
            val all = projects + archived
            val availableTags = ProjectTags.all(all.map { it.tags })
            val filter = ProjectFilter(query = query, mode = mode, tag = tag)
            val visibleActive = if (mode == ProjectFilterMode.ARCHIVED) emptyList() else projects.filter { it.matches(filter) }
            val visibleArchived = if (mode == ProjectFilterMode.PINNED) emptyList() else archived.filter { it.matches(filter) }

            ActionGroup {
                FilterChip(
                    selected = mode == ProjectFilterMode.ALL,
                    onClick = { mode = ProjectFilterMode.ALL },
                    label = { Text("الكل") }
                )
                FilterChip(
                    selected = mode == ProjectFilterMode.PINNED,
                    onClick = { mode = ProjectFilterMode.PINNED },
                    label = { Text("المثبتة") }
                )
                FilterChip(
                    selected = mode == ProjectFilterMode.ARCHIVED,
                    onClick = { mode = ProjectFilterMode.ARCHIVED },
                    label = { Text("المؤرشفة") }
                )
            }

            if (availableTags.isNotEmpty()) {
                ActionGroup {
                    availableTags.forEach { available ->
                        val selected = tag != null && ProjectTags.sameTag(available, tag!!)
                        FilterChip(
                            selected = selected,
                            onClick = { tag = if (selected) null else available },
                            label = { Text("#" + available) }
                        )
                    }
                }
            }

            if (filter.isActive && (visibleActive.isNotEmpty() || visibleArchived.isNotEmpty())) {
                Text(
                    "النتائج: " + (visibleActive.size + visibleArchived.size) + " من " + all.size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (all.isEmpty()) {
                    item {
                        ScreenNotice(stringResource(R.string.projects_empty), action = stringResource(R.string.project_new)) {
                            if (!busy) openForm()
                        }
                    }
                } else if (visibleActive.isEmpty() && visibleArchived.isEmpty()) {
                    item {
                        ScreenNotice(
                            "لا توجد مشاريع مطابقة للبحث أو التصفية الحالية.",
                            action = "مسح الفلاتر"
                        ) { clearFilters() }
                    }
                }
                items(visibleActive, key = { "active-${it.id}" }) { p ->
                    ProjectRow(
                        project = p,
                        enabled = !busy,
                        onEdit = { openForm(p) },
                        onOpen = { onOpen(p.id) },
                        onTagClick = { clicked -> tag = if (tag != null && ProjectTags.sameTag(clicked, tag!!)) null else clicked },
                        onPin = {
                            mutate(if (p.isPinned) "أُلغي تثبيت المشروع." else "ثُبّت المشروع.") {
                                dao.setPinned(p.id, !p.isPinned, System.currentTimeMillis())
                            }
                        },
                        onArchive = {
                            mutate("أُرشف المشروع.") { dao.setArchived(p.id, true, System.currentTimeMillis()) }
                        },
                        onDelete = { deleting = p }
                    )
                }
                if (visibleArchived.isNotEmpty()) {
                    item {
                        Text(
                            "المؤرشفة (" + visibleArchived.size + ")",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(visibleArchived, key = { "archived-${it.id}" }) { p ->
                        ProjectRow(
                            project = p,
                            enabled = !busy,
                            onEdit = { openForm(p) },
                            onOpen = { onOpen(p.id) },
                            onTagClick = { clicked -> tag = if (tag != null && ProjectTags.sameTag(clicked, tag!!)) null else clicked },
                            onPin = null,
                            onArchive = {
                                mutate("استُعيد المشروع.") { dao.setArchived(p.id, false, System.currentTimeMillis()) }
                            },
                            onDelete = { deleting = p }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        val original = editing
        var name by remember(original?.id) { mutableStateOf(original?.name.orEmpty()) }
        var description by remember(original?.id) { mutableStateOf(original?.description.orEmpty()) }
        var tagsText by remember(original?.id) { mutableStateOf(original?.tags.orEmpty()) }
        val nameError = name.trim().length > 120
        val descriptionError = description.trim().length > 2000
        AlertDialog(
            onDismissRequest = { if (!busy) showForm = false },
            title = { Text(stringResource(if (original == null) R.string.project_new else R.string.project_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; formError = null }, enabled = !busy,
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.project_name)) }, isError = nameError,
                        supportingText = { Text(stringResource(if (nameError) R.string.project_name_limit else R.string.project_name_required)) }
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it; formError = null }, enabled = !busy,
                        maxLines = 4, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.project_description)) }, isError = descriptionError,
                        supportingText = { if (descriptionError) Text(stringResource(R.string.project_description_limit)) }
                    )
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it.take(300); formError = null },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الوسوم (اختياري)") },
                        supportingText = { Text("حتى ١٢ وسماً، مفصولة بفاصلة. مثال: تصميم، عربي") }
                    )
                    if (busy) ScreenNotice(stringResource(R.string.project_saving), loading = true)
                    formError?.let { ScreenNotice(it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && name.isNotBlank() && !nameError && !descriptionError,
                    onClick = {
                        val cleanName = name.trim()
                        val cleanDescription = description.trim()
                        val cleanTags = ProjectTags.normalize(tagsText)
                        mutate(if (original == null) "أُنشئ المشروع." else "حُفظت تعديلات المشروع.", inForm = true) {
                            if (original == null) {
                                dao.insert(ProjectEntity(name = cleanName, description = cleanDescription, tags = cleanTags))
                                container.logActivity("project", "إنشاء مشروع: $cleanName")
                            } else {
                                check(
                                    dao.updateDetails(
                                        original.id, cleanName, cleanDescription, cleanTags,
                                        System.currentTimeMillis()
                                    ) == 1
                                )
                            }
                        }
                    }
                ) { Text(stringResource(if (original == null) R.string.project_create else R.string.action_save)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showForm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.project_delete)) },
            text = { Text(stringResource(R.string.project_delete_confirmation, "\u2068${project.name}\u2069")) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    mutate("حُذف المشروع.") { dao.delete(project.id) }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

private fun ProjectEntity.matches(filter: ProjectFilter): Boolean = ProjectFilters.matches(
    ProjectSearchFields(
        name = name,
        description = description,
        tags = tags,
        isPinned = isPinned,
        isArchived = isArchived
    ),
    filter
)

@Composable
private fun ProjectRow(
    project: ProjectEntity,
    enabled: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onTagClick: (String) -> Unit,
    onPin: (() -> Unit)?,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val tags = ProjectTags.parse(project.tags)
    Card(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content))
            if (project.isPinned && !project.isArchived) Text("مشروع مثبّت", style = MaterialTheme.typography.labelMedium)
            if (project.description.isNotBlank()) Text(
                project.description, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tags.isNotEmpty()) {
                ActionGroup {
                    tags.forEach { value ->
                        AssistChip(
                            onClick = { onTagClick(value) },
                            enabled = enabled,
                            label = { Text("#" + value, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            ActionGroup {
                IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.project_edit)) }
                if (onPin != null) IconButton(onClick = onPin, enabled = enabled) {
                    Icon(Icons.Filled.Star, contentDescription = if (project.isPinned) "إلغاء التثبيت" else "تثبيت المشروع")
                }
                IconButton(onClick = onArchive, enabled = enabled) {
                    Icon(Icons.Filled.Archive, contentDescription = if (project.isArchived) "استعادة المشروع" else "أرشفة المشروع")
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.project_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
