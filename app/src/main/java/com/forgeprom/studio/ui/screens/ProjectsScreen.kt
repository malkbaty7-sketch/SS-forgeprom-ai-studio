package com.forgeprom.studio.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import com.forgeprom.studio.R
import com.forgeprom.studio.ui.components.ActionGroup
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.data.ProjectEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import com.forgeprom.studio.ui.components.ScreenNotice
import com.forgeprom.studio.ui.components.observeScreenData

/**
 * R2 — إدارة المشاريع: إنشاء، تثبيت، أرشفة، حذف، فتح التفاصيل.
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

        if (busy && !showForm) ScreenNotice("جارٍ تحديث المشاريع…", loading = true)
        notice?.let { ScreenNotice(it) }
        val current = load
        val rows = current?.getOrNull()
        if (rows == null) {
            if (current == null) ScreenNotice("جارٍ تحميل المشاريع…", loading = true)
            else ScreenNotice("تعذّر تحميل المشاريع.", action = "إعادة المحاولة") { retry++ }
        } else {
            val (projects, archived) = rows
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (projects.isEmpty() && archived.isEmpty()) {
                    item {
                        ScreenNotice(stringResource(R.string.projects_empty), action = stringResource(R.string.project_new)) {
                            if (!busy) openForm()
                        }
                    }
                }
                items(projects, key = { "active-${it.id}" }) { p ->
                    ProjectRow(
                        project = p,
                        enabled = !busy,
                        onEdit = { openForm(p) },
                        onOpen = { onOpen(p.id) },
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
                if (archived.isNotEmpty()) {
                    item {
                        Text(
                            "المؤرشفة (" + archived.size + ")",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(archived, key = { "archived-${it.id}" }) { p ->
                        ProjectRow(
                            project = p,
                            enabled = !busy,
                            onEdit = { openForm(p) },
                            onOpen = { onOpen(p.id) },
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
                        mutate(if (original == null) "أُنشئ المشروع." else "حُفظت تعديلات المشروع.", inForm = true) {
                            if (original == null) {
                                dao.insert(ProjectEntity(name = cleanName, description = cleanDescription))
                                container.logActivity("project", "إنشاء مشروع: $cleanName")
                            } else {
                                check(dao.updateDetails(original.id, cleanName, cleanDescription, System.currentTimeMillis()) == 1)
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

@Composable
private fun ProjectRow(
    project: ProjectEntity,
    enabled: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPin: (() -> Unit)?,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content))
            if (project.isPinned && !project.isArchived) Text("مشروع مثبّت", style = MaterialTheme.typography.labelMedium)
            if (project.description.isNotBlank()) Text(
                project.description, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
