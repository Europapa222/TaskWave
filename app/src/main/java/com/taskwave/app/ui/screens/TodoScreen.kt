package com.taskwave.app.ui.screens

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taskwave.app.R
import com.taskwave.app.data.Priority
import com.taskwave.app.data.TaskFolder
import com.taskwave.app.data.TodoItem
import com.taskwave.app.viewmodel.AppUiState
import com.taskwave.app.viewmodel.FilterType
import com.taskwave.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val folderColors = listOf(
    Color(0xFF6C63FF),
    Color(0xFF4FC3F7),
    Color(0xFF66BB6A),
    Color(0xFFFF9800),
    Color(0xFFEF5350),
    Color(0xFFAB47BC)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    if (state.showAddDialog) {
        AddTaskDialog(
            state = state,
            onDismiss = { vm.hideAddDialog() },
            onAdd = { title, description, priority, folderId, dueAt, reminderAt ->
                vm.addItem(title, description, priority, folderId, dueAt, reminderAt)
            }
        )
    }
    if (state.showFolderDialog) {
        AddFolderDialog(
            onDismiss = { vm.hideFolderDialog() },
            onAdd = { vm.addFolder(it) }
        )
    }
    if (state.showSettings) {
        SettingsSheet(
            darkModeOverride = state.darkModeOverride,
            onDarkModeChange = { vm.setDarkModeOverride(it) },
            smartAiEnabled = state.smartAiEnabled,
            onSmartAiChange = { vm.setSmartAiEnabled(it) },
            onDismiss = { vm.hideSettings() }
        )
    }

    Scaffold(
        containerColor = scheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.showAddDialog() },
                containerColor = scheme.primary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.shadow(16.dp, RoundedCornerShape(20.dp))
            ) {
                Icon(Icons.Default.Add, null, tint = scheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.padding(padding)
        ) {
            item { HeaderCard(state) { vm.showSettings() } }
            item { SearchField(state.searchQuery) { vm.setSearchQuery(it) } }
            item {
                FolderRow(
                    folders = state.folders,
                    selectedFolderId = state.selectedFolderId,
                    tasks = state.items,
                    onSelect = { vm.selectFolder(it) },
                    onAddFolder = { vm.showFolderDialog() },
                    onDeleteFolder = { vm.deleteFolder(it) }
                )
            }
            item { FilterRow(state.filter) { vm.setFilter(it) } }
            if (state.totalCount > 0) {
                item {
                    StatsRow(
                        total = state.totalCount,
                        active = state.activeCount,
                        done = state.doneCount,
                        overdue = state.overdueCount
                    )
                }
                item { ProductivityCard(state) }
                if (state.showAntiOverload) {
                    item { AntiOverloadCard(state.topThreeToday) }
                }
                state.focusTask?.let { task ->
                    item { FocusCard(task = task, folder = state.folders.firstOrNull { it.id == task.folderId }) }
                }
            }
            if (state.filteredItems.isEmpty()) {
                item { EmptyState(state.filter, state.searchQuery) }
            }
            items(state.filteredItems, key = { it.id }) { item ->
                TaskCard(
                    item = item,
                    folder = state.folders.firstOrNull { it.id == item.folderId },
                    onToggle = { vm.toggleDone(item.id) },
                    onDelete = { vm.deleteItem(item.id) },
                    onSplit = { vm.splitTask(item.id) },
                    onSubtaskToggle = { subtaskId -> vm.toggleSubtask(item.id, subtaskId) }
                )
            }
        }
    }
}

@Composable
private fun ProductivityCard(state: AppUiState) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Outlined.Star, null, tint = Color(0xFFFF9800), modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.motivation_title), fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Text(
                    stringResource(
                        R.string.motivation_stats,
                        state.productivity.streak,
                        state.productivity.points,
                        state.productivityLevel,
                        state.productivity.completedToday
                    ),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AntiOverloadCard(tasks: List<TodoItem>) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primary.copy(0.10f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.anti_overload_title), color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(stringResource(R.string.anti_overload_subtitle), color = scheme.onSurfaceVariant, fontSize = 12.sp)
            tasks.forEachIndexed { index, task ->
                Text("${index + 1}. ${task.title}", color = scheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.search_tasks)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun HeaderCard(state: AppUiState, onSettingsClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val animProgress by animateFloatAsState(state.progress, tween(700, easing = EaseOutCubic), label = "p")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(scheme.primary, scheme.primaryContainer)))
            .padding(start = 24.dp, end = 24.dp, top = 52.dp, bottom = 28.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        SimpleDateFormat("d MMMM, EEEE", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimary.copy(0.75f)
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.onPrimary.copy(0.15f))
                ) {
                    Icon(Icons.Default.Settings, null, tint = scheme.onPrimary)
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.onPrimary.copy(0.12f))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.progress),
                            color = scheme.onPrimary.copy(0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.completed_of, state.doneCount, state.totalCount),
                            color = scheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = scheme.onPrimary,
                        trackColor = scheme.onPrimary.copy(0.2f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            state.totalCount == 0 -> stringResource(R.string.add_first)
                            state.doneCount == state.totalCount -> stringResource(R.string.all_done)
                            state.doneCount == 0 -> stringResource(R.string.start_working)
                            else -> stringResource(R.string.percent_done, (animProgress * 100).toInt())
                        },
                        color = scheme.onPrimary.copy(0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folders: List<TaskFolder>,
    selectedFolderId: String?,
    tasks: List<TodoItem>,
    onSelect: (String?) -> Unit,
    onAddFolder: () -> Unit,
    onDeleteFolder: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FolderChip(
            label = stringResource(R.string.folder_all),
            count = tasks.size,
            selected = selectedFolderId == null,
            color = MaterialTheme.colorScheme.primary,
            onClick = { onSelect(null) }
        )
        folders.forEach { folder ->
            FolderChip(
                label = folder.name,
                count = tasks.count { it.folderId == folder.id },
                selected = selectedFolderId == folder.id,
                color = folderColors[folder.colorIndex % folderColors.size],
                onClick = { onSelect(folder.id) },
                onDelete = { onDeleteFolder(folder.id) }
            )
        }
        FilterChip(
            selected = false,
            onClick = onAddFolder,
            label = { Text(stringResource(R.string.folder_add), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun FolderChip(
    label: String,
    count: Int,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label · $count", fontSize = 13.sp) },
        trailingIcon = {
            if (selected) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
            } else if (onDelete != null) {
                Text(
                    "×",
                    modifier = Modifier.clickable { onDelete() },
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(0.18f),
            selectedLabelColor = color,
            selectedTrailingIconColor = color,
            containerColor = scheme.surfaceVariant,
            labelColor = scheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun FilterRow(currentFilter: FilterType, onFilterChange: (FilterType) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val labels = mapOf(
            FilterType.TODAY to stringResource(R.string.filter_today),
            FilterType.ALL to stringResource(R.string.filter_all),
            FilterType.ACTIVE to stringResource(R.string.filter_active),
            FilterType.DONE to stringResource(R.string.filter_done)
        )
        FilterType.values().forEach { filter ->
            val selected = filter == currentFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        labels[filter].orEmpty(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = scheme.primary,
                    selectedLabelColor = scheme.onPrimary,
                    containerColor = scheme.surfaceVariant,
                    labelColor = scheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun StatsRow(total: Int, active: Int, done: Int, overdue: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(stringResource(R.string.stat_total), total, scheme.surfaceVariant, Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_active), active, scheme.primary.copy(.1f), Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_done), done, Color(0xFF4CAF50).copy(.12f), Modifier.weight(1f))
        StatCard(stringResource(R.string.stat_overdue), overdue, Color(0xFFEF5350).copy(.12f), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: Int, bg: Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = scheme.onSurface)
        Text(label, fontSize = 10.sp, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FocusCard(task: TodoItem, folder: TaskFolder?) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primary.copy(0.10f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.focus_title), color = scheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(task.title, color = scheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            val meta = listOfNotNull(folder?.name, task.dueAt?.let { formatDate(it) }).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(filter: FilterType, searchQuery: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when (filter) {
                    FilterType.TODAY -> Icons.Outlined.Star
                    FilterType.DONE -> Icons.Outlined.CheckCircle
                    FilterType.ACTIVE -> Icons.Outlined.Star
                    FilterType.ALL -> Icons.AutoMirrored.Outlined.List
                },
                null,
                tint = scheme.outline,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when (filter) {
                FilterType.TODAY -> stringResource(R.string.empty_today)
                FilterType.ALL -> stringResource(R.string.empty_all)
                FilterType.ACTIVE -> stringResource(R.string.empty_active)
                FilterType.DONE -> stringResource(R.string.empty_done)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (filter) {
                FilterType.TODAY -> stringResource(R.string.empty_today_sub)
                FilterType.ALL -> stringResource(R.string.empty_all_sub)
                FilterType.ACTIVE -> stringResource(R.string.empty_active_sub)
                FilterType.DONE -> stringResource(R.string.empty_done_sub)
            }.let { if (searchQuery.isBlank()) it else stringResource(R.string.empty_search_sub) },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TaskCard(
    item: TodoItem,
    folder: TaskFolder?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSplit: () -> Unit,
    onSubtaskToggle: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val priorityColor = when (item.priority) {
        Priority.HIGH -> Color(0xFFEF5350)
        Priority.MEDIUM -> Color(0xFFFF9800)
        Priority.LOW -> Color(0xFF66BB6A)
    }
    val now = System.currentTimeMillis()
    val isOverdue = !item.isDone && item.dueAt != null && item.dueAt < now

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .shadow(if (item.isDone) 0.dp else 3.dp, RoundedCornerShape(20.dp), ambientColor = scheme.primary.copy(0.08f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDone) scheme.surfaceVariant.copy(0.5f) else scheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = priorityColor,
                    uncheckedColor = priorityColor.copy(0.5f),
                    checkmarkColor = Color.White
                )
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isDone) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.subtasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    item.subtasks.forEach { subtask ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = subtask.isDone,
                                enabled = !item.isDone,
                                onCheckedChange = { onSubtaskToggle(subtask.id) },
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                subtask.title,
                                color = scheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textDecoration = if (subtask.isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaPill(priorityLabel(item.priority), priorityColor, item.isDone)
                    folder?.let { MetaPill(it.name, folderColors[it.colorIndex % folderColors.size], item.isDone) }
                    item.dueAt?.let {
                        MetaPill(
                            label = if (isOverdue) stringResource(R.string.overdue, formatDate(it)) else stringResource(R.string.due, formatDate(it)),
                            color = if (isOverdue) scheme.error else scheme.primary,
                            muted = item.isDone
                        )
                    }
                    item.reminderAt?.let {
                        if (!item.isDone) MetaPill(stringResource(R.string.reminder_short, formatDateTime(it)), Color(0xFF4FC3F7), false)
                    }
                }
                if (item.isDone) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.auto_delete_notice),
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                if (!item.isDone && item.subtasks.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.split_task),
                        modifier = Modifier.clickable { onSplit() },
                        color = scheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, tint = scheme.error.copy(0.4f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MetaPill(label: String, color: Color, muted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (muted) scheme.outline.copy(.1f) else color.copy(.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (muted) scheme.onSurfaceVariant else color,
            maxLines = 1
        )
    }
}

@Composable
private fun priorityLabel(priority: Priority): String {
    return when (priority) {
        Priority.HIGH -> stringResource(R.string.priority_high)
        Priority.MEDIUM -> stringResource(R.string.priority_medium)
        Priority.LOW -> stringResource(R.string.priority_low)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onAdd: (String, String, Priority, String?, Long?, Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var folderId by remember { mutableStateOf(state.selectedFolderId ?: state.folders.firstOrNull()?.id) }
    var dueAt by remember { mutableStateOf<Long?>(null) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderAt by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueAt ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            dueAt = endOfDay(selected)
                            if (reminderEnabled) reminderAt = dueAt?.minus(60 * 60 * 1000L)
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.add), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                stringResource(R.string.new_task),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.task_description)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                ChoiceSection(stringResource(R.string.priority)) {
                    listOf(
                        Priority.HIGH to Pair(stringResource(R.string.priority_high), Color(0xFFEF5350)),
                        Priority.MEDIUM to Pair(stringResource(R.string.priority_medium), Color(0xFFFF9800)),
                        Priority.LOW to Pair(stringResource(R.string.priority_low), Color(0xFF66BB6A))
                    ).forEach { (p, pair) ->
                        val (label, color) = pair
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(.15f),
                                selectedLabelColor = color
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                ChoiceSection(stringResource(R.string.folder)) {
                    state.folders.forEach { folder ->
                        FilterChip(
                            selected = folderId == folder.id,
                            onClick = { folderId = folder.id },
                            label = { Text(folder.name, fontSize = 12.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                ChoiceSection(stringResource(R.string.deadline)) {
                    dateOptions().forEach { option ->
                        FilterChip(
                            selected = dueAt == option.time,
                            onClick = {
                                dueAt = option.time
                                if (reminderEnabled && reminderAt == null) reminderAt = option.time - 60 * 60 * 1000L
                            },
                            label = { Text(option.label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showDatePicker = true },
                        label = {
                            Text(
                                dueAt?.let { stringResource(R.string.deadline_calendar_selected, formatDate(it)) }
                                    ?: stringResource(R.string.deadline_calendar),
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    FilterChip(
                        selected = dueAt == null,
                        onClick = {
                            dueAt = null
                            reminderEnabled = false
                            reminderAt = null
                        },
                        label = { Text(stringResource(R.string.no_deadline), fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminder), fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text(stringResource(R.string.reminder_optional), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = reminderEnabled,
                        enabled = dueAt != null,
                        onCheckedChange = { checked ->
                            reminderEnabled = checked
                            reminderAt = if (checked) dueAt?.minus(60 * 60 * 1000L) else null
                        }
                    )
                }
                if (reminderEnabled) {
                    ChoiceSection(stringResource(R.string.remind_when)) {
                        val due = dueAt
                        if (due != null) {
                            listOf(
                                stringResource(R.string.remind_one_hour) to due - 60 * 60 * 1000L,
                                stringResource(R.string.remind_one_day) to due - 24 * 60 * 60 * 1000L,
                                stringResource(R.string.remind_at_deadline) to due
                            ).forEach { option ->
                                FilterChip(
                                    selected = reminderAt == option.second,
                                    onClick = { reminderAt = option.second },
                                    label = { Text(option.first, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, description, priority, folderId, dueAt, reminderAt) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.add), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ChoiceSection(title: String, content: @Composable RowScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun AddFolderDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.folder_new), fontWeight = FontWeight.ExtraBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.add), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private data class DateOption(val label: String, val time: Long)

@Composable
private fun dateOptions(): List<DateOption> {
    return listOf(
        DateOption(stringResource(R.string.deadline_today), endOfDay(0)),
        DateOption(stringResource(R.string.deadline_tomorrow), endOfDay(1)),
        DateOption(stringResource(R.string.deadline_week), endOfDay(7))
    )
}

private fun endOfDay(daysFromNow: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, daysFromNow)
    return calendar.endOfDayMillis()
}

private fun endOfDay(timeMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timeMillis
    return calendar.endOfDayMillis()
}

private fun Calendar.endOfDayMillis(): Long {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    return timeInMillis
}

private fun formatDate(time: Long): String {
    return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
}

private fun formatDateTime(time: Long): String {
    return SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(time))
}
