package com.lamuier.scheduletimeline.ui.timeline

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.teamDisplay
import com.lamuier.scheduletimeline.data.ScheduleExport
import com.lamuier.scheduletimeline.data.ThemeMode
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.ui.theme.LocalDarkTheme
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import com.lamuier.scheduletimeline.ui.theme.adaptTo
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.ui.theme.eventTypeColors
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private enum class ManageStep { Menu, Import, ClearConfirm, ClearDayConfirm }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDataSheet(
    viewModel: ScheduleViewModel,
    onRequestNotificationPermission: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(ManageStep.Menu) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val liveUpdatesAlwaysOn by viewModel.liveUpdatesAlwaysOn.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enterOffset = if (forward) 40 else -40
                val exitOffset = if (forward) -40 else 40
                (slideInHorizontally { enterOffset } + fadeIn()) togetherWith
                    (slideOutHorizontally { exitOffset } + fadeOut())
            },
            label = "manageStep",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        ) { step ->
            when (step) {
                ManageStep.Menu -> MainMenu(
                    themeMode = themeMode,
                    onThemeModeChange = viewModel::setThemeMode,
                    notificationsEnabled = notificationsEnabled,
                    liveUpdatesAlwaysOn = liveUpdatesAlwaysOn,
                    onNotificationsEnabledChange = { enabled ->
                        if (enabled) {
                            onRequestNotificationPermission(false)
                        } else {
                            viewModel.setNotificationsEnabled(false)
                        }
                    },
                    onLiveUpdatesAlwaysOnChange = { enabled ->
                        if (enabled) {
                            onRequestNotificationPermission(true)
                        } else {
                            viewModel.setLiveUpdatesAlwaysOn(false)
                        }
                    },
                    onImport = { currentStep = ManageStep.Import },
                    onExport = {
                        scope.launch {
                            val csv = viewModel.exportCurrentDayCsv()
                            if (csv.isBlank()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.export_empty),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        context.getString(R.string.export_share_title),
                                    ),
                                )
                                onDismiss()
                            }
                        }
                    },
                    onClearDay = { currentStep = ManageStep.ClearDayConfirm },
                    onClear = { currentStep = ManageStep.ClearConfirm },
                )
                ManageStep.Import -> {
                    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
                    val fallbackDayKey = remember(currentDate) {
                        currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    ImportView(
                        fallbackDayKey = fallbackDayKey,
                        onBack = { currentStep = ManageStep.Menu },
                        onImport = { csv ->
                            viewModel.batchImport(csv)
                            onDismiss()
                        },
                    )
                }
                ManageStep.ClearConfirm -> ClearConfirmView(
                    title = stringResource(R.string.clear_all_title),
                    message = stringResource(R.string.clear_all_message),
                    confirmText = stringResource(R.string.clear_all_confirm),
                    onBack = { currentStep = ManageStep.Menu },
                    onConfirm = {
                        viewModel.clearAllData()
                        onDismiss()
                    },
                )
                ManageStep.ClearDayConfirm -> {
                    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
                    val dayState by viewModel.observeDayState(currentDate)
                        .collectAsStateWithLifecycle()
                    val pattern = stringResource(R.string.date_pattern)
                    val dateText = remember(currentDate, pattern) {
                        currentDate.format(DateTimeFormatter.ofPattern(pattern))
                    }
                    ClearConfirmView(
                        title = stringResource(R.string.clear_day_title),
                        message = stringResource(
                            R.string.clear_day_message,
                            dateText,
                            dayState.events.size,
                        ),
                        confirmText = stringResource(R.string.clear_day_confirm),
                        onBack = { currentStep = ManageStep.Menu },
                        onConfirm = {
                            viewModel.clearDayData(currentDate)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMenu(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    notificationsEnabled: Boolean,
    liveUpdatesAlwaysOn: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onLiveUpdatesAlwaysOnChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onClearDay: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.manage_data_title),
            subtitle = stringResource(R.string.manage_data_subtitle),
        )

        Text(
            text = stringResource(R.string.theme_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        ThemeModeSelector(
            selected = themeMode,
            onSelected = onThemeModeChange,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.notification_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        NotificationToggleRow(
            title = stringResource(R.string.notification_toggle_title),
            summary = stringResource(R.string.notification_toggle_summary),
            checked = notificationsEnabled,
            onCheckedChange = onNotificationsEnabledChange,
        )
        Spacer(Modifier.height(8.dp))
        NotificationToggleRow(
            title = stringResource(R.string.notification_always_on_title),
            summary = stringResource(R.string.notification_always_on_summary),
            checked = liveUpdatesAlwaysOn,
            onCheckedChange = onLiveUpdatesAlwaysOnChange,
        )
        Spacer(Modifier.height(8.dp))

        // 后台保活入口：厂商杀后台是「通知不更新 / 开机后通知失效」的最常见根因，
        // 引导用户进本机厂商设置页加白名单。Intent 数据综合自 backgroundable-android
        // 与 dontkillmyapp.com。
        ActionGroup {
            ActionRow(
                icon = Icons.Default.Settings,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconContainer = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                title = stringResource(R.string.background_keep_alive_title),
                summary = stringResource(R.string.background_keep_alive_summary),
                onClick = {
                    BackgroundRestrictionHelper.open(
                        context = context,
                        fallbackToastResId = R.string.background_keep_alive_fallback_toast,
                    )
                },
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.manage_data_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        ActionGroup {
            ActionRow(
                icon = Icons.Default.Add,
                iconTint = MaterialTheme.colorScheme.primary,
                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                title = stringResource(R.string.cd_batch_import),
                summary = stringResource(R.string.manage_import_summary),
                onClick = onImport,
            )
            GroupDivider()
            ActionRow(
                icon = Icons.Default.Share,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconContainer = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                title = stringResource(R.string.cd_export),
                summary = stringResource(R.string.manage_export_summary),
                onClick = onExport,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.manage_danger_zone),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        ActionGroup(
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.28f),
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        ) {
            ActionRow(
                icon = Icons.Default.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                iconContainer = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                title = stringResource(R.string.cd_clear_day),
                summary = stringResource(R.string.manage_clear_day_summary),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onClearDay,
            )
            GroupDivider()
            ActionRow(
                icon = Icons.Default.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                iconContainer = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                title = stringResource(R.string.cd_clear_all),
                summary = stringResource(R.string.manage_clear_summary),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.System to stringResource(R.string.theme_system),
        ThemeMode.Light to stringResource(R.string.theme_light),
        ThemeMode.Dark to stringResource(R.string.theme_dark),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionGroup(
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
    ) {
        content()
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        thickness = 0.5.dp,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconContainer: Color,
    title: String,
    summary: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ImportView(
    fallbackDayKey: String,
    onBack: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val previewEvents = remember(text, fallbackDayKey) {
        ScheduleExport.parseImport(text, fallbackDayKey = fallbackDayKey)
    }
    val dark = LocalDarkTheme.current
    val sample = ScheduleExport.IMPORT_SAMPLE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.cd_batch_import),
            subtitle = stringResource(R.string.import_hint),
            leading = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
        )

        ImportSampleHint(sample = sample)

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(
                    text = sample,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        Spacer(Modifier.height(16.dp))

        PreviewSection(
            events = previewEvents,
            sample = sample,
            dark = dark,
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { onImport(text) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = previewEvents.isNotEmpty(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = stringResource(R.string.action_import),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportSampleHint(sample: String) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { copyImportSample(context, sample) },
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.import_format_fields),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = sample,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.import_sample_long_press),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
        }
    }
}

private fun copyImportSample(context: Context, sample: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("import_sample", sample))
    Toast.makeText(
        context,
        context.getString(R.string.import_sample_copied),
        Toast.LENGTH_SHORT,
    ).show()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewSection(
    events: List<ScheduleEvent>,
    sample: String,
    dark: Boolean,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (events.isEmpty()) {
                stringResource(R.string.import_empty_preview)
            } else {
                stringResource(R.string.import_preview_title, events.size)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (events.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { copyImportSample(context, sample) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            } else {
                events.forEach { event ->
                    ImportPreviewRow(event = event, dark = dark)
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewRow(
    event: ScheduleEvent,
    dark: Boolean,
) {
    val colors = eventTypeColors(event).adaptTo(dark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.teamDisplay.ifBlank { event.title }.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                event.dayKey.takeIf { it.isNotBlank() },
                EventLabels.typeChip(event),
                event.location.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "${TimeFormat.minutesToHm(event.startMinutes)}–${TimeFormat.minutesToHm(event.endMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.accent,
        )
    }
}

@Composable
private fun ClearConfirmView(
    title: String,
    message: String,
    confirmText: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = confirmText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainMenuPreview() {
    ScheduleTimelineTheme {
        Surface {
            MainMenu(
                themeMode = ThemeMode.System,
                onThemeModeChange = {},
                notificationsEnabled = true,
                liveUpdatesAlwaysOn = true,
                onNotificationsEnabledChange = {},
                onLiveUpdatesAlwaysOnChange = {},
                onImport = {},
                onExport = {},
                onClearDay = {},
                onClear = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportViewPreview() {
    ScheduleTimelineTheme {
        Surface {
            ImportView(
                fallbackDayKey = "2026-07-12",
                onBack = {},
                onImport = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClearConfirmPreview() {
    ScheduleTimelineTheme {
        Surface {
            ClearConfirmView(
                title = "清空当日日程？",
                message = "将删除 2026年08月16日 的 3 项日程，其他日期不受影响。此操作无法撤销。",
                confirmText = "确认清空",
                onBack = {},
                onConfirm = {},
            )
        }
    }
}
