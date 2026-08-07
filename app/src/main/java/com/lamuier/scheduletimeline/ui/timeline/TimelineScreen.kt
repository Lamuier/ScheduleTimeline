package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.data.ChartLayout
import com.lamuier.scheduletimeline.data.DayStats
import com.lamuier.scheduletimeline.data.NearestScheduleHint
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.data.TimelineBuilder
import com.lamuier.scheduletimeline.data.TimelineItem
import com.lamuier.scheduletimeline.data.teamDisplay
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: ScheduleViewModel,
    onAdd: () -> Unit,
    onEditEvent: (Long) -> Unit,
    onRequestNotificationPermission: (Boolean) -> Unit,
) {
    val dayState by viewModel.dayState.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }
    val isToday = currentDate == LocalDate.now()
    var nearestHint by remember { mutableStateOf<NearestScheduleHint?>(null) }
    LaunchedEffect(dayState.date, dayState.events.isEmpty()) {
        nearestHint = if (dayState.events.isEmpty()) {
            viewModel.findNearestScheduleHint()
        } else {
            null
        }
    }

    // 无限分页：页码围绕中心页展开，每页对应 baseDate 的前后偏移一天。
    // HorizontalPager 自带跟手拖拽与松手吸附，替代原先手写平移 +
    // AnimatedContent 的双段动画（拖动与切换动画脱节导致的不连贯）。
    val baseDate = remember { viewModel.currentDate.value }
    val pagerState = rememberPagerState(initialPage = DAY_PAGE_CENTER) { DAY_PAGE_COUNT }
    val dateForPage: (Int) -> LocalDate = { page ->
        baseDate.plusDays((page - DAY_PAGE_CENTER).toLong())
    }

    // 分页落定 → 同步到 ViewModel（驱动 TopBar 与数据层）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val date = dateForPage(page)
            if (date != viewModel.currentDate.value) {
                viewModel.changeDate(date)
            }
        }
    }
    // 外部改期（按钮、日期选择器、返回今天、空状态跳转）→ 驱动分页
    LaunchedEffect(currentDate) {
        val target = DAY_PAGE_CENTER + (currentDate.toEpochDay() - baseDate.toEpochDay()).toInt()
        if (pagerState.currentPage != target) {
            if (abs(target - pagerState.currentPage) <= 3) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }
        }
    }

    // 顶栏日期跟随拖动：滚动中显示目标页日期，静止时显示落定页日期。
    // 避免「页面已滑走、顶栏日期仍停留在旧日期，松手后才跳变」的脱节感。
    val displayedDate by remember {
        derivedStateOf {
            val page = if (pagerState.isScrollInProgress) {
                pagerState.targetPage
            } else {
                pagerState.settledPage
            }
            dateForPage(page)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TimelineTopBar(
                currentDate = displayedDate,
                onPreviousDay = { viewModel.shiftDate(-1) },
                onNextDay = { viewModel.shiftDate(1) },
                onSelectDate = { showDatePicker = true },
                onManage = { showManageSheet = true },
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
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val pageDate = dateForPage(page)
                // ViewModel 按日期缓存共享 StateFlow：回访页 initialValue 即上次数据，不再从空态闪变。
                val pageState by remember(pageDate) { viewModel.observeDayState(pageDate) }
                    .collectAsStateWithLifecycle()
                DayPage(
                    date = pageState.date,
                    events = pageState.events,
                    nearestHint = if (pageDate == currentDate) nearestHint else null,
                    onJumpToNearest = { hint ->
                        runCatching { LocalDate.parse(hint.dayKey) }.getOrNull()?.let {
                            viewModel.changeDate(it)
                        }
                    },
                    onEditEvent = onEditEvent,
                )
            }

            if (!isToday) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.changeDate(LocalDate.now()) },
                    icon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.action_back_to_today)) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            FloatingActionButton(
                onClick = onAdd,
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
            initialSelectedDateMillis = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.changeDate(date)
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
            onDismiss = { showManageSheet = false },
        )
    }
}

private const val DAY_PAGE_COUNT = 20_000
private const val DAY_PAGE_CENTER = DAY_PAGE_COUNT / 2

@Composable
private fun DayPage(
    date: LocalDate,
    events: List<ScheduleEvent>,
    nearestHint: NearestScheduleHint?,
    onJumpToNearest: (NearestScheduleHint) -> Unit,
    onEditEvent: (Long) -> Unit,
) {
    val pageItems = remember(events) { TimelineBuilder.build(events) }
    val pageStats = remember(events) { ChartLayout.stats(events) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (date == LocalDate.now()) {
            CurrentStatusBanner(pageItems)
        }

        if (pageStats != null) {
            StatsBar(stats = pageStats)
        }
        if (pageItems.isEmpty()) {
            EmptyState(
                nearestHint = nearestHint,
                onJumpToNearest = onJumpToNearest,
            )
        } else {
            TimelineList(
                items = pageItems,
                onEditEvent = onEditEvent,
                events = events,
            )
        }
    }
}

@Composable
private fun CurrentStatusBanner(items: List<TimelineItem>) {
    var nowMinutes by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
            delay(30_000)
        }
    }

    val eventItems = items.flatMap { item ->
        when (item) {
            is TimelineItem.Event -> listOf(item)
            is TimelineItem.OverlapGroup -> item.events
            is TimelineItem.Gap -> emptyList()
        }
    }
    val currentEvents = eventItems.filter { nowMinutes in it.startMinutes until it.endMinutes }
    val currentGap = items.filterIsInstance<TimelineItem.Gap>()
        .find { nowMinutes in it.startMinutes until it.endMinutes }
    val nextEvent = eventItems
        .filter { it.startMinutes > nowMinutes }
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
                    currentGap != null -> stringResource(R.string.status_free)
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
