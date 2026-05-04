package com.taskwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskwave.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    darkModeOverride: String,
    onDarkModeChange: (String) -> Unit,
    smartAiEnabled: Boolean,
    onSmartAiChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = scheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.appearance),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // Theme options: system / light / dark
            val options = listOf(
                "system" to stringResource(R.string.dark_mode_sub),
                "light" to "Light",
                "dark" to "Dark"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (key, label) ->
                    val selected = darkModeOverride == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) scheme.primary.copy(alpha = 0.08f)
                                else scheme.surfaceVariant
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) scheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onDarkModeChange(key) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) scheme.primary else scheme.onSurface,
                            fontSize = 15.sp
                        )
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.smart_ai),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_ai_title), color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.smart_ai_subtitle), color = scheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Switch(
                    checked = smartAiEnabled,
                    onCheckedChange = onSmartAiChange
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.language_system), color = scheme.onSurface, fontSize = 15.sp)
                Text("Auto", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.widgets),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.widgets_help_title),
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    stringResource(R.string.widgets_help_body),
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                WidgetPreviewCards()
            }
        }
    }
}

@Composable
private fun WidgetPreviewCards() {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetPreview(stringResource(R.string.widget_title)) {
            Text(stringResource(R.string.widget_preview_today_count), color = scheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            listOf(
                stringResource(R.string.widget_preview_task_1),
                stringResource(R.string.widget_preview_task_2)
            ).forEach {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.onPrimary.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(it, color = scheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        WidgetPreview(stringResource(R.string.widget_focus_title)) {
            Text(stringResource(R.string.widget_preview_focus_badge), color = scheme.onPrimary.copy(alpha = 0.82f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.widget_preview_task_1), color = scheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.widget_preview_focus_meta), color = scheme.onPrimary.copy(alpha = 0.82f), fontSize = 11.sp)
        }

        WidgetPreview(stringResource(R.string.widget_progress_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetMiniStat(stringResource(R.string.stat_active), "5")
                WidgetMiniStat(stringResource(R.string.stat_done), "12")
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.7f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = scheme.onPrimary,
                trackColor = scheme.onPrimary.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
private fun WidgetPreview(title: String, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.primary)
            .padding(14.dp)
    ) {
        Text(title, color = scheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RowScope.WidgetMiniStat(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.onPrimary.copy(alpha = 0.16f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = scheme.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = scheme.onPrimary.copy(alpha = 0.82f), fontSize = 10.sp)
    }
}
