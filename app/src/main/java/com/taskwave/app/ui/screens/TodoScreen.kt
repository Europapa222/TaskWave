package com.taskwave.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.taskwave.app.data.TodoItem
import com.taskwave.app.viewmodel.FilterType
import com.taskwave.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    if (state.showAddDialog) {
        AddTaskDialog(onDismiss = { vm.hideAddDialog() }, onAdd = { t, d, p -> vm.addItem(t, d, p) })
    }
    if (state.showSettings) {
        SettingsSheet(
            darkModeOverride = state.darkModeOverride,
            onDarkModeChange = { vm.setDarkModeOverride(it) },
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
            item { HeaderCard(state.doneCount, state.totalCount, state.progress) { vm.showSettings() } }
            item { FilterRow(state.filter) { vm.setFilter(it) } }
            if (state.totalCount > 0) {
                item { StatsRow(state.totalCount, state.doneCount, state.totalCount - state.doneCount) }
            }
            if (state.filteredItems.isEmpty()) {
                item { EmptyState(state.filter) }
            }
            items(state.filteredItems, key = { it.id }) { item ->
                TaskCard(item, onToggle = { vm.toggleDone(item.id) }, onDelete = { vm.deleteItem(item.id) })
            }
        }
    }
}

@Composable
private fun HeaderCard(doneCount: Int, totalCount: Int, progress: Float, onSettingsClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val animProgress by animateFloatAsState(progress, tween(700, easing = EaseOutCubic), label = "p")

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
                            stringResource(R.string.completed_of, doneCount, totalCount),
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
                            totalCount == 0 -> stringResource(R.string.add_first)
                            doneCount == totalCount -> stringResource(R.string.all_done)
                            doneCount == 0 -> stringResource(R.string.start_working)
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
private fun FilterRow(currentFilter: FilterType, onFilterChange: (FilterType) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val labels = mapOf(
            FilterType.ALL to stringResource(R.string.filter_all),
            FilterType.ACTIVE to stringResource(R.string.filter_active),
            FilterType.DONE to stringResource(R.string.filter_done)
        )
        FilterType.values().forEach { f ->
            val selected = f == currentFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(f) },
                label = {
                    Text(
                        labels[f] ?: "",
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
private fun StatsRow(total: Int, done: Int, active: Int) {
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
    }
}

@Composable
private fun StatCard(label: String, value: Int, bg: Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = scheme.onSurface
        )
        Text(label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(filter: FilterType) {
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
                    FilterType.DONE -> Icons.Outlined.CheckCircle
                    FilterType.ACTIVE -> Icons.Outlined.Star
                    FilterType.ALL -> Icons.Outlined.List
                },
                null,
                tint = scheme.outline,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when (filter) {
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
                FilterType.ALL -> stringResource(R.string.empty_all_sub)
                FilterType.ACTIVE -> stringResource(R.string.empty_active_sub)
                FilterType.DONE -> stringResource(R.string.empty_done_sub)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TaskCard(item: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val priorityColor = when (item.priority) {
        Priority.HIGH -> Color(0xFFEF5350)
        Priority.MEDIUM -> Color(0xFFFF9800)
        Priority.LOW -> Color(0xFF66BB6A)
    }

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
                Spacer(Modifier.height(6.dp))
                val priorityLabel = when (item.priority) {
                    Priority.HIGH -> stringResource(R.string.priority_high)
                    Priority.MEDIUM -> stringResource(R.string.priority_medium)
                    Priority.LOW -> stringResource(R.string.priority_low)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.isDone) scheme.outline.copy(.1f) else priorityColor.copy(.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        priorityLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isDone) scheme.onSurfaceVariant else priorityColor
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete, null,
                    tint = scheme.error.copy(0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String, Priority) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    val scheme = MaterialTheme.colorScheme

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
                Text(
                    stringResource(R.string.priority),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, description, priority) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.add), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
