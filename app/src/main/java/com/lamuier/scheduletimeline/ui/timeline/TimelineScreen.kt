package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.animation.core.tween
import com.lamuier.scheduletimeline.data.ScheduleTimeSnapshot
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.ScheduleDayState
import com.lamuier.scheduletimeline.data.DayStats
import com.lamuier.scheduletimeline.data.NearestScheduleHint
import com.lamuier.scheduletimeline.data.ScheduleExport
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.data.TimelineItem
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.teamDisplay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: ScheduleViewModel,
    onAdd: () -> Unit,
    onEditEvent: (Long) -> Unit,
    onRequestNotificationPermission: (Boolean) -> Unit,
    requestOpenImport: Boolean = false,
    onOpenImportConsumed: () -> Unit = {},
    clock: java.time.Clock = java.time.Clock.systemUTC(),
) {
    val dayState by viewModel.dayState.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }
    // shortcut 直达导入：面板打开时是否停在导入步骤，关闭后复位
    var manageOpenAtImport by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<TimelineItem.Event?>(null) }

    LaunchedEffect(requestOpenImport) {
        if (requestOpenImport) {
            showManageSheet = true
            manageOpenAtImport = true
            onOpenImportConsumed()
        }
    }
    val time by rememberScheduleTime(clock)
    val scheduledDayKeys by viewModel.scheduledDayKeys.collectAsStateWithLifecycle()

    // 无限分页：页码围绕中心页展开，每页对应 baseDate 的前后偏移一天。
    // HorizontalPager 自带跟手拖拽与松手吸附，替代原先手写平移 +
    // AnimatedContent 的双段动画（拖动与切换动画脱节导致的不连贯）。
    var baseDate by remember { mutableStateOf(viewModel.currentDate.value) }
    val pagerState = rememberPagerState(initialPage = DAY_PAGE_CENTER) { DAY_PAGE_COUNT }
    val dateForPage: (Int) -> LocalDate = { page ->
        baseDate.plusDays((page - DAY_PAGE_CENTER).toLong())
    }

    // Only a real drag may commit a pager date; cancelled animations do not own the date.
    var wasScrolling by remember { mutableStateOf(false) }
    var requestedDate by remember { mutableStateOf(viewModel.currentDate.value) }
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }

    // 按钮直接发起分页操作；返回今天、最近日程及日期选择不等待弹簧吸附。
    // requestedDate 区分自己的导航与外部 Shortcut，避免日期流反过来重启动画。
    val navigateToDate: (LocalDate, Boolean) -> Unit = { date, animate ->
        wasScrolling = false
        navigationJob?.cancel()
        requestedDate = date
        viewModel.changeDate(date)
        val distance = date.toEpochDay() - baseDate.toEpochDay()
        if (distance !in -(DAY_PAGE_CENTER - 2L)..(DAY_PAGE_CENTER - 3L)) {
            baseDate = date
            pagerState.requestScrollToPage(DAY_PAGE_CENTER)
        } else {
            val target = DAY_PAGE_CENTER + distance.toInt()
            if (animate && abs(target - pagerState.currentPage) <= 3) {
                navigationJob = scope.launch {
                    pagerState.animateScrollToPage(target, animationSpec = tween(180))
                }
            } else {
                pagerState.requestScrollToPage(target)
            }
        }
    }

    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                navigationJob?.cancel()
                wasScrolling = true
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (scrolling, page) ->
                if (!scrolling && wasScrolling) {
                    wasScrolling = false
                    val date = dateForPage(page)
                    requestedDate = date
                    viewModel.changeDate(date)
                }
            }
    }
    LaunchedEffect(currentDate) {
        if (currentDate != requestedDate) navigateToDate(currentDate, false)
    }

    // 按屏幕最接近的页同步日期与返回按钮，不等待松手后的 settledPage。
    val displayedDate by remember(baseDate) {
        derivedStateOf { dateForPage(pagerState.currentPage) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TimelineTopBar(
                currentDate = displayedDate,
                onPreviousDay = {
                    val from = if (wasScrolling) displayedDate else viewModel.currentDate.value
                    navigateToDate(from.minusDays(1), true)
                },
                onNextDay = {
                    val from = if (wasScrolling) displayedDate else viewModel.currentDate.value
                    navigateToDate(from.plusDays(1), true)
                },
                onSelectDate = { showDatePicker = true },
                onManage = { viewModel.changeDate(dateForPage(pagerState.currentPage)); showManageSheet = true },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("date-pager"),
                beyondViewportPageCount = 1,
                key = { dateForPage(it).toString() },
            ) { page ->
                val pageDate = dateForPage(page)
                // ViewModel 按日期缓存共享 StateFlow：回访页 initialValue 即上次数据，不再从空态闪变。
                val pageState by remember(pageDate) { viewModel.observeDayState(pageDate) }
                    .collectAsStateWithLifecycle()
                val nearestHint = remember(pageDate, scheduledDayKeys, pageState.events.isEmpty()) {
                    if (pageState.events.isEmpty()) {
                        ScheduleExport.nearestScheduleHint(pageDate, scheduledDayKeys)
                    } else null
                }
                DayPage(
                    state = pageState,
                    time = time,
                    nearestHint = nearestHint,
                    onJumpToNearest = { hint ->
                        runCatching { LocalDate.parse(hint.dayKey) }.getOrNull()?.let {
                            navigateToDate(it, false)
                        }
                    },
                    onSelectEvent = { viewModel.clearWriteError(); selectedEvent = it },
                )
            }

            if (displayedDate != time.today) {
                val backToTodayLabel = stringResource(R.string.action_back_to_today)
                ExtendedFloatingActionButton(
                    onClick = { navigateToDate(time.today, false) },
                    icon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                        )
                    },
                    text = { Text(backToTodayLabel) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .semantics { contentDescription = backToTodayLabel },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            FloatingActionButton(
                onClick = { viewModel.changeDate(dateForPage(pagerState.currentPage)); onAdd() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_event))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = displayedDate.atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        navigateToDate(date, false)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showManageSheet) {
        ManageDataSheet(
            viewModel = viewModel,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onDismiss = {
                showManageSheet = false
                manageOpenAtImport = false
            },
            openAtImport = manageOpenAtImport,
        )
    }

    val detailWriteState by viewModel.writeState.collectAsStateWithLifecycle()
    selectedEvent?.let { event ->
        EventDetailSheet(
            item = event,
            events = dayState.events,
            busy = detailWriteState.busy,
            failed = detailWriteState.failed,
            onEdit = {
                val id = event.event.id
                selectedEvent = null
                onEditEvent(id)
            },
            onCompleteTokuten = {
                val id = event.event.id
                viewModel.completeTokuten(id) { selectedEvent = null }
            }.takeIf {
                EventType.fromStorage(event.event.eventType) == EventType.TOKUTEN &&
                    !event.event.completed
            },
            onDismiss = { selectedEvent = null; viewModel.clearWriteError() },
        )
    }
}

private const val DAY_PAGE_COUNT = 20_000
private const val DAY_PAGE_CENTER = DAY_PAGE_COUNT / 2

@Composable
private fun DayPage(
    state: ScheduleDayState,
    time: ScheduleTimeSnapshot,
    nearestHint: NearestScheduleHint?,
    onJumpToNearest: (NearestScheduleHint) -> Unit,
    onSelectEvent: (TimelineItem.Event) -> Unit,
) {
    val pageItems = state.items
    val pageStats = state.stats
    val events = state.events
    val isToday = state.date == time.today
    val nowMinutes = time.minutes
    val effectiveNow = if (isToday) nowMinutes else null

    Column(modifier = Modifier.fillMaxSize().testTag("day-page-${state.date}")) {
        if (isToday) {
            CurrentStatusBanner(pageItems, nowMinutes)
        }

        if (pageStats != null) {
            StatsBar(stats = pageStats)
        }
        val invalidEvents = state.invalidEvents
        if (invalidEvents.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                invalidEvents.forEach { event ->
                    Text(stringResource(R.string.invalid_saved_event, event.teamDisplay.ifBlank { event.title }),
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelectEvent(TimelineItem.Event(event, note = event.note.takeIf { it.isNotBlank() }))
                        }.padding(12.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (pageItems.isEmpty()) {
            EmptyState(
                nearestHint = nearestHint,
                onJumpToNearest = onJumpToNearest,
            )
        } else {
            TimelineList(
                items = pageItems,
                events = events,
                nowMinutes = effectiveNow,
                onSelectEvent = onSelectEvent,
            )
        }
    }
}

@Composable
private fun CurrentStatusBanner(items: List<TimelineItem>, nowMinutes: Int) {
    val eventItems = items.flatMap { item ->
        when (item) {
            is TimelineItem.Event -> listOf(item)
            is TimelineItem.OverlapGroup -> item.events
            is TimelineItem.Gap -> emptyList()
        }
    }
    val occupyingNow = eventItems.filter {
        nowMinutes in it.startMinutes until it.endMinutes
    }
    val currentEvents = occupyingNow.filter { !it.event.completed }
    val completedOnlyNow = occupyingNow.isNotEmpty() && currentEvents.isEmpty()
    val currentGap = items.filterIsInstance<TimelineItem.Gap>()
        .find { nowMinutes in it.startMinutes until it.endMinutes }
    val nextEvent = eventItems
        .filter { !it.event.completed && it.startMinutes > nowMinutes }
        .minByOrNull { it.startMinutes }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val statusText = when {
                    currentEvents.size == 1 -> stringResource(
                        R.string.status_ongoing,
                        currentEvents.single().event.teamDisplay.ifBlank {
                            currentEvents.single().event.title
                        },
                    )
                    currentEvents.size > 1 -> stringResource(
                        R.string.status_ongoing_multiple,
                        currentEvents.size,
                        currentEvents.joinToString("、") {
                            it.event.teamDisplay.ifBlank { it.event.title }
                        },
                    )
                    currentGap != null || completedOnlyNow -> stringResource(R.string.status_free)
                    else -> stringResource(R.string.status_none)
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )

                if (nextEvent != null) {
                    val diff = nextEvent.startMinutes - nowMinutes
                    Text(
                        text = stringResource(
                            R.string.status_next,
                            nextEvent.event.teamDisplay.ifBlank { nextEvent.event.title },
                            TimeFormat.durationLabel(0, diff),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    nearestHint: NearestScheduleHint?,
    onJumpToNearest: (NearestScheduleHint) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.empty_timeline),
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (nearestHint != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val hintText = if (nearestHint.isFuture) {
                    stringResource(R.string.empty_nearest_future, nearestHint.daysAway)
                } else {
                    stringResource(R.string.empty_nearest_past, nearestHint.daysAway)
                }
                TextButton(onClick = { onJumpToNearest(nearestHint) }) {
                    Text(hintText)
                }
            }
        }
    }
}

@Composable
private fun StatsBar(stats: DayStats) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            StatItem(value = "${stats.eventCount}", label = stringResource(R.string.stats_events))
            StatItem(
                value = TimeFormat.durationLabel(0, stats.busyMinutes),
                label = stringResource(R.string.stats_busy),
            )
            StatItem(
                value = TimeFormat.durationLabel(0, stats.freeMinutes),
                label = stringResource(R.string.stats_free),
            )
            StatItem(
                value = "${stats.conflictCount}",
                label = stringResource(R.string.stats_conflict),
                highlight = stats.conflictCount > 0,
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
