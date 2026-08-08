package com.lamuier.scheduletimeline.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.data.TokutenKind
import com.lamuier.scheduletimeline.ui.theme.LocalDarkTheme
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import com.lamuier.scheduletimeline.ui.theme.adaptTo
import com.lamuier.scheduletimeline.ui.theme.eventTypeColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditEventScreen(
    eventId: Long?,
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.editUiState.collectAsStateWithLifecycle()
    val teams by viewModel.teams.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var teamToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(eventId) {
        viewModel.prepareEdit(eventId)
    }

    EditEventContent(
        state = state,
        teamNames = teams.map { it.name },
        onBack = onBack,
        onEventTypeChange = viewModel::setEditEventType,
        onTokutenKindChange = { kind ->
            viewModel.updateEdit { s -> s.copy(tokutenKind = kind) }
        },
        onTeamInputChange = viewModel::setEditTeamInput,
        onToggleTeam = viewModel::toggleEditTeam,
        onAddTeam = viewModel::addEditTeamInput,
        onTitleChange = { viewModel.updateEdit { s -> s.copy(title = it) } },
        onLocationChange = { viewModel.updateEdit { s -> s.copy(location = it) } },
        onNoteChange = { viewModel.updateEdit { s -> s.copy(note = it) } },
        onPickStart = { pickingStart = true },
        onPickEnd = { pickingEnd = true },
        onSave = { viewModel.saveEdit(onDone = onBack) },
        onRequestDelete = { showDeleteConfirm = true },
        onRequestDeleteTeam = { teamToDelete = it },
    )

    teamToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { teamToDelete = null },
            title = { Text(stringResource(R.string.edit_delete_team_title)) },
            text = { Text(stringResource(R.string.edit_delete_team_message, name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTeam(name)
                    teamToDelete = null
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (pickingStart) {
        TimePickerDialog(
            title = stringResource(R.string.edit_pick_start),
            initialMinutes = state.startMinutes,
            onConfirm = {
                viewModel.setStartMinutes(it)
                pickingStart = false
            },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        TimePickerDialog(
            title = stringResource(R.string.edit_pick_end),
            initialMinutes = state.endMinutes,
            onConfirm = {
                viewModel.setEndMinutes(it)
                pickingEnd = false
            },
            onDismiss = { pickingEnd = false },
        )
    }

    if (showDeleteConfirm && eventId != null) {
        val label = state.effectiveTeamNames().joinToString(" / ").ifBlank { state.title }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.edit_delete_event_title)) },
            text = { Text(stringResource(R.string.edit_delete_event_message, label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete(eventId, onDone = onBack)
                    },
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditEventContent(
    state: EditUiState,
    teamNames: List<String>,
    onBack: () -> Unit,
    onEventTypeChange: (EventType) -> Unit,
    onTokutenKindChange: (TokutenKind) -> Unit,
    onTeamInputChange: (String) -> Unit,
    onToggleTeam: (String) -> Unit,
    onAddTeam: () -> Unit,
    onTitleChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestDeleteTeam: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isNew) R.string.edit_add_title else R.string.edit_edit_title,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    // 保存移到右上角（原来在底部），删除移到列表底部——删除更不易误触。
                    if (!state.isNew) {
                        IconButton(onClick = onSave) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.action_save_event),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.edit_label_event_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.eventType == EventType.PERFORMANCE,
                        onClick = { onEventTypeChange(EventType.PERFORMANCE) },
                        label = { Text(stringResource(R.string.event_type_performance)) },
                    )
                    FilterChip(
                        selected = state.eventType == EventType.TOKUTEN,
                        onClick = { onEventTypeChange(EventType.TOKUTEN) },
                        label = { Text(stringResource(R.string.event_type_tokuten)) },
                    )
                }
            }

            if (state.eventType == EventType.TOKUTEN) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.edit_label_tokuten_kind),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TokutenKind.entries.forEach { kind ->
                            FilterChip(
                                selected = state.tokutenKind == kind,
                                onClick = { onTokutenKindChange(kind) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (kind) {
                                                TokutenKind.PRE -> R.string.tokuten_kind_pre
                                                TokutenKind.PARALLEL -> R.string.tokuten_kind_parallel
                                                TokutenKind.FINAL -> R.string.tokuten_kind_final
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        if (state.eventType == EventType.TOKUTEN) {
                            R.string.edit_label_teams_multiple
                        } else {
                            R.string.edit_label_team_single
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    teamNames.forEach { candidate ->
                        val colors = eventTypeColors(state.eventType).adaptTo(LocalDarkTheme.current)
                        val isSelected = candidate in state.teamNames
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                colors.container
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isSelected) {
                                colors.accent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { onToggleTeam(candidate) },
                                    onLongClick = { onRequestDeleteTeam(candidate) },
                                ),
                        ) {
                            Text(
                                text = candidate,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.teamInput,
                    onValueChange = onTeamInputChange,
                    placeholder = {
                        Text(
                            stringResource(
                                if (state.eventType == EventType.TOKUTEN) {
                                    R.string.edit_hint_teams_multiple
                                } else {
                                    R.string.edit_hint_team
                                },
                            ),
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = onAddTeam,
                            enabled = state.teamInput.isNotBlank(),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_add_team),
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Text(
                    text = stringResource(R.string.edit_team_relation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.edit_label_session)) },
                placeholder = { Text(stringResource(R.string.edit_hint_session)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = state.location,
                onValueChange = onLocationChange,
                label = { Text(stringResource(R.string.edit_label_location)) },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.edit_hint_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.edit_label_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TimeField(
                        label = stringResource(R.string.edit_label_start),
                        minutes = state.startMinutes,
                        onClick = onPickStart,
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = stringResource(R.string.edit_label_end),
                        minutes = state.endMinutes,
                        onClick = onPickEnd,
                        modifier = Modifier.weight(1f),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = stringResource(
                            R.string.edit_duration,
                            TimeFormat.durationLabel(state.startMinutes, state.endMinutes),
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.edit_label_note)) },
                placeholder = { Text(stringResource(R.string.edit_hint_note)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            when (error) {
                                EditValidationError.BlankTeam -> R.string.edit_error_blank_team
                                EditValidationError.MissingTokutenKind ->
                                    R.string.edit_error_missing_tokuten_kind
                                EditValidationError.EndBeforeStart ->
                                    R.string.edit_error_end_before_start
                            },
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            if (!state.isNew) {
                // 已有事件：底部放删除（ destructive 风格），与右上角保存互换。
                Button(
                    onClick = onRequestDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                // 新建事件：无删除，底部仍是保存。
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(R.string.action_save_event),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = TimeFormat.minutesToHm(minutes),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun EditEventContentPreview() {
    ScheduleTimelineTheme {
        EditEventContent(
            state = EditUiState(
                teamNames = listOf("StarDiary", "银烁花火"),
                eventType = EventType.TOKUTEN,
                tokutenKind = TokutenKind.PARALLEL,
                title = "午场",
                location = "吧台A",
                startMinutes = 17 * 60,
                endMinutes = 19 * 60,
            ),
            teamNames = listOf("StarDiary", "银烁花火"),
            onBack = {},
            onEventTypeChange = {},
            onTokutenKindChange = {},
            onTeamInputChange = {},
            onToggleTeam = {},
            onAddTeam = {},
            onTitleChange = {},
            onLocationChange = {},
            onNoteChange = {},
            onPickStart = {},
            onPickEnd = {},
            onSave = {},
            onRequestDelete = {},
            onRequestDeleteTeam = {},
        )
    }
}
