package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.data.EventLabels
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.TimeFormat
import com.lamuier.scheduletimeline.data.TimelineItem
import com.lamuier.scheduletimeline.data.TokutenKind
import com.lamuier.scheduletimeline.data.sharesTeamWith
import com.lamuier.scheduletimeline.data.teamDisplay
import com.lamuier.scheduletimeline.ui.theme.LocalDarkTheme
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import com.lamuier.scheduletimeline.ui.theme.adaptTo
import com.lamuier.scheduletimeline.ui.theme.eventTypeColors
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@Composable
internal fun TimelineList(
    items: List<TimelineItem>,
    events: List<ScheduleEvent> = emptyList(),
    nowMinutes: Int? = null,
    onSelectEvent: (TimelineItem.Event) -> Unit,
) {
    // 用 LazyListState 直接取可见 item 在视口内的 offset/size，避免 onPlaced 坐标空间错位。
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val nowItemKey = remember(items, nowMinutes) {
        nowMinutes?.let { now -> nowContainingItemKey(items, now) }
    }
    var lastCenteredKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 有「现在」落点时上下各留半屏空白，才能把时间线滚到视口正中（含当天第一条/最后一条）。
        val extraPad = if (nowItemKey != null) maxHeight / 2 else 8.dp
        val bottomPad = extraPad.coerceAtLeast(80.dp)
        val cardBottomPadPx = with(density) { 16.dp.roundToPx() }

        LaunchedEffect(nowItemKey, items, extraPad) {
            val now = nowMinutes
            if (now == null || nowItemKey == null || nowItemKey == lastCenteredKey) return@LaunchedEffect
            centerNowLine(listState, items, now, cardBottomPadPx)
            lastCenteredKey = nowItemKey
        }

        val jumpDirection by remember(items, nowMinutes, nowItemKey, cardBottomPadPx) {
            derivedStateOf {
                val now = nowMinutes
                if (now == null || nowItemKey == null || items.isEmpty()) {
                    null
                } else {
                    nowJumpDirection(listState, items, now, cardBottomPadPx)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 20.dp,
                    top = extraPad,
                    bottom = bottomPad,
                ),
            ) {
                itemsIndexed(items, key = { index, item ->
                    when (item) {
                        is TimelineItem.Event -> "e-${item.event.id}"
                        is TimelineItem.OverlapGroup -> "o-${item.events.joinToString("-") { it.event.id.toString() }}"
                        is TimelineItem.Gap -> "g-$index-${item.startMinutes}"
                    }
                }) { index, item ->
                    TimelineNode(
                        isFirst = index == 0,
                        isLast = index == items.lastIndex,
                        item = item,
                    ) {
                        when (item) {
                            is TimelineItem.Event -> EventCard(
                                item = item,
                                nowMinutes = nowMinutes,
                                onClick = { onSelectEvent(item) },
                            )
                            is TimelineItem.OverlapGroup -> ParallelEventCards(
                                group = item,
                                events = events,
                                nowMinutes = nowMinutes,
                                onSelectEvent = onSelectEvent,
                            )
                            is TimelineItem.Gap -> GapCard(item = item, nowMinutes = nowMinutes)
                        }
                    }
                }
            }

            // 整轴「现在」水平红线：仅今天页（nowMinutes != null）显示，
            // 位置直接由 LazyListState 的可见 item 信息插值得到，随滚动与当前时间实时更新。
            if (nowMinutes != null) {
                val nowY = computeNowY(listState, items, nowMinutes, cardBottomPadPx)
                if (nowY != null) {
                    NowLine(y = nowY)
                }
            }

            // 当天有日程、当前时间轴滚出视野时，左下角一键跳回并居中。
            // 非今天页 nowMinutes 为 null，此按钮不出现，避免与「返回今天」抢位。
            val direction = jumpDirection
            if (direction != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val now = nowMinutes ?: return@ExtendedFloatingActionButton
                        scope.launch {
                            centerNowLine(listState, items, now, cardBottomPadPx)
                            lastCenteredKey = nowItemKey
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (direction == NowJumpDirection.Up) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = stringResource(R.string.cd_jump_to_now),
                        )
                    },
                    text = { Text(stringResource(R.string.action_jump_to_now)) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun itemTimeRange(item: TimelineItem): Pair<Int, Int> = when (item) {
    is TimelineItem.Event -> item.event.startMinutes to item.event.endMinutes
    is TimelineItem.OverlapGroup -> item.startMinutes to item.endMinutes
    is TimelineItem.Gap -> item.startMinutes to item.endMinutes
}

private fun nowContainingItemKey(items: List<TimelineItem>, now: Int): String? {
    val index = items.indices.firstOrNull { i ->
        val (start, end) = itemTimeRange(items[i])
        now in start until end
    } ?: return null
    return when (val item = items[index]) {
        is TimelineItem.Event -> "e-${item.event.id}"
        is TimelineItem.OverlapGroup ->
            "o-${item.events.joinToString("-") { it.event.id.toString() }}"
        is TimelineItem.Gap -> "g-$index-${item.startMinutes}"
    }
}

private suspend fun centerNowLine(
    listState: LazyListState,
    items: List<TimelineItem>,
    now: Int,
    bottomPadPx: Int,
) {
    val index = items.indices.firstOrNull { i ->
        val (start, end) = itemTimeRange(items[i])
        now in start until end
    } ?: return

    listState.scrollToItem(index)
    val placed = withTimeoutOrNull(750) {
        snapshotFlow { computeNowY(listState, items, now, bottomPadPx) }
            .filterNotNull()
            .first()
    } ?: return
    val nowY = placed
    val viewport = listState.layoutInfo.viewportEndOffset -
        listState.layoutInfo.viewportStartOffset
    if (viewport <= 0) return
    val delta = nowY - viewport / 2f
    if (abs(delta) < 4f) return
    listState.scrollBy(delta)
}

private enum class NowJumpDirection { Up, Down }

/**
 * 当前时间轴是否在视口内。在视口内返回 null（不必显示跳转按钮）；
 * 滚出视野时返回应提示的方向。布局尚未就绪时不显示按钮。
 */
private fun nowJumpDirection(
    listState: LazyListState,
    items: List<TimelineItem>,
    now: Int,
    bottomPadPx: Int,
): NowJumpDirection? {
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    val viewport = listState.layoutInfo.viewportEndOffset -
        listState.layoutInfo.viewportStartOffset
    val nowY = computeNowY(listState, items, now, bottomPadPx)
    if (viewport > 0 && nowY != null && nowY >= 0f && nowY <= viewport) return null

    val index = items.indices.firstOrNull { i ->
        val (start, end) = itemTimeRange(items[i])
        now in start until end
    } ?: return null
    if (nowY != null) return if (nowY < 0f) NowJumpDirection.Up else NowJumpDirection.Down
    return if (index < visible.first().index) NowJumpDirection.Up else NowJumpDirection.Down
}

/**
 * 根据 LazyListState 中可见 item 的实际视口位置，计算「现在」(now, 分钟) 在列表中的 y 像素。
 * 落在可见 item 时间范围内时，按其在「卡片区」(去掉内容区底部 [bottomPadPx] 留白) 内的时间占比插值；
 * 这样 now 线的起点/终点与左侧时间标签（卡片顶/底）严格对齐。当前时刻不可见时返回 null。
 */
private fun computeNowY(
    listState: LazyListState,
    items: List<TimelineItem>,
    now: Int,
    bottomPadPx: Int,
): Float? {
    val layoutInfo = listState.layoutInfo
    for (info in layoutInfo.visibleItemsInfo) {
        val item = items.getOrNull(info.index) ?: continue
        val (start, end) = when (item) {
            is TimelineItem.Event -> item.event.startMinutes to item.event.endMinutes
            is TimelineItem.OverlapGroup -> item.startMinutes to item.endMinutes
            is TimelineItem.Gap -> item.startMinutes to item.endMinutes
        }
        if (now in start until end) {
            val span = (end - start).coerceAtLeast(1)
            val frac = (now - start).toFloat() / span
            val contentHeight = (info.size - bottomPadPx).coerceAtLeast(1)
            return info.offset + frac * contentHeight
        }
    }
    return null
}

/**
 * 整轴「现在」红线：仅在左侧时间轴留白区（列表左 padding 12.dp ~ 卡片左缘 90.dp）画一条
 * 短横线 + 圆点，对齐竖向节点列中心；不进入卡片内容区，避免横穿卡片。
 * 位置由 LazyListState 可见 item 信息插值得出，随 now / 滚动实时移动。
 */
@Composable
private fun NowLine(y: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, y.toInt()) },
    ) {
        // 横线：仅左侧留白（时间标签右缘 58.dp ~ 卡片左缘 90.dp），不压卡片。
        Box(
            modifier = Modifier
                .padding(start = 58.dp)
                .width(32.dp)
                .height(2.dp)
                .background(ProgressRed),
        )
        // 圆点：对齐左侧竖向节点列中心（contentPadding.start 12.dp + 节点 62.dp = 74.dp）。
        // 圆点宽 10.dp，故左移 5.dp 让圆心正好落在节点中心线上，而非偏到右侧。
        Box(
            modifier = Modifier
                .offset { IntOffset((74.dp - 5.dp).roundToPx(), (-4).dp.roundToPx()) }
                .size(10.dp)
                .clip(CircleShape)
                .background(ProgressRed),
        )
    }
}

@Composable
private fun TimelineNode(
    isFirst: Boolean,
    isLast: Boolean,
    item: TimelineItem,
    content: @Composable () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val secondaryColor = MaterialTheme.colorScheme.secondary
        val nodeColor = remember(item, dark) {
        when (item) {
            is TimelineItem.Event -> eventTypeColors(item.event).adaptTo(dark).accent
            is TimelineItem.OverlapGroup -> eventTypeColors(item.events.first().event).adaptTo(dark).accent
            is TimelineItem.Gap -> secondaryColor
        }
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val centerX = (46.dp + 16.dp).toPx()
                val dotY = 21.dp.toPx()
                val lineThickness = 2.dp.toPx()

                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, dotY - 6.dp.toPx()),
                        strokeWidth = lineThickness,
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, dotY + 6.dp.toPx()),
                        end = Offset(centerX, size.height),
                        strokeWidth = lineThickness,
                    )
                }
                drawCircle(
                    color = nodeColor,
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, dotY),
                )
            },
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = TimeFormat.minutesToHm(item.startMinutes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = TimeFormat.minutesToHm(item.endMinutes),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        Spacer(modifier = Modifier.width(32.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp),
        ) {
            content()
        }
    }
}

/**
 * 卡片只展示主要概览（时间、团队/标题、类型）；地点、关联演出/特典、重叠警告、备注
 * 统一收进 [EventDetailSheet]。进行中的事件用边框闪烁标出当前日程；冲突时重叠的各方都闪。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventCard(
    item: TimelineItem.Event,
    nowMinutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fillHeight: Boolean = false,
) {
    val event = item.event
    val dark = LocalDarkTheme.current
    val colors = remember(event.eventType, event.tokutenKind, dark) {
        eventTypeColors(event).adaptTo(dark)
    }
    val cardContainer = colors.accent.copy(alpha = if (dark) 0.18f else 0.12f)

    val inProgress = isEventInProgress(event, nowMinutes)
    val blinkAlpha = rememberBlinkAlpha(enabled = inProgress)
    val cardAlpha = if (event.completed) 0.62f else 1f

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardContainer.copy(alpha = cardContainer.alpha * cardAlpha),
        ),
        border = if (inProgress) {
            BorderStroke(2.dp, ProgressRed.copy(alpha = blinkAlpha))
        } else {
            null
        },
    ) {
            Column(
                modifier = Modifier.padding(if (compact) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeFormat.rangeLabel(event.startMinutes, event.endMinutes),
                        color = colors.accent,
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        color = colors.container,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = EventLabels.typeChip(event),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                        )
                    }
                    if (event.completed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.event_completed),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Text(
                    text = event.teamDisplay.ifBlank { event.title }.ifBlank {
                        stringResource(R.string.event_untitled)
                    },
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )

                if (event.title.isNotBlank() && event.teamDisplay.isNotBlank()) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
}

/**
 * 并行组内每条时间轨道的理想比例尺：每分钟对应的像素高度。
 * 让卡片高度真正反映事件时长，而不是由内容多寡决定。
 */
private const val PARALLEL_PX_PER_MINUTE = 4

/** 轨道高度上限：超过后自动缩小比例尺，保证整组仍能在一屏内放下。 */
private val PARALLEL_MAX_LANE_HEIGHT = 600.dp

/** 卡片最小可读高度：保证时间、团队、类型至少可见。 */
private val PARALLEL_MIN_CARD_HEIGHT = 96.dp

/** 并行轨道内相邻卡片之间的视觉间隙，避免时间连续的卡片贴在一起像一块。 */
private val PARALLEL_CARD_GAP = 4.dp

/** 当前时间轴 / 进行中边框闪烁颜色。 */
private val ProgressRed = Color(0xFFE53935)

private fun isEventInProgress(event: ScheduleEvent, nowMinutes: Int?): Boolean =
    nowMinutes != null &&
        !event.completed &&
        nowMinutes in event.startMinutes until event.endMinutes

@Composable
private fun rememberBlinkAlpha(enabled: Boolean): Float {
    val infinite = rememberInfiniteTransition(label = "event-border-blink")
    val alpha by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "border-alpha",
    )
    return if (enabled) alpha else 1f
}

@Composable
private fun ParallelEventCards(
    group: TimelineItem.OverlapGroup,
    events: List<ScheduleEvent>,
    nowMinutes: Int?,
    onSelectEvent: (TimelineItem.Event) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        EventLane(
            label = stringResource(R.string.event_type_performance),
            items = group.performanceEvents,
            groupStart = group.startMinutes,
            groupEnd = group.endMinutes,
            nowMinutes = nowMinutes,
            onSelectEvent = onSelectEvent,
            modifier = Modifier.weight(1f),
        )
        EventLane(
            label = stringResource(R.string.event_type_tokuten),
            items = group.tokutenEvents,
            groupStart = group.startMinutes,
            groupEnd = group.endMinutes,
            nowMinutes = nowMinutes,
            onSelectEvent = onSelectEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EventLane(
    label: String,
    items: List<TimelineItem.Event>,
    groupStart: Int,
    groupEnd: Int,
    nowMinutes: Int?,
    onSelectEvent: (TimelineItem.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val laneMinutes = (groupEnd - groupStart).coerceAtLeast(1)
    // 理想比例尺下若轨道超过上限，则等比缩小比例尺，整组封顶在 MAX 高度内。
    val scale = minOf(
        PARALLEL_PX_PER_MINUTE.toFloat(),
        (PARALLEL_MAX_LANE_HEIGHT / laneMinutes).value,
    )
    val laneHeight = (laneMinutes * scale).dp

    Column(
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // 轨道高度等于组的时长跨度；卡片按真实起止时间绝对定位，
        // 高度 = 时长 × 比例尺（并保底最小可读高度）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(laneHeight)
                .clipToBounds(),
        ) {
            items.forEach { item ->
                val event = item.event
                val cardOffset = ((event.startMinutes - groupStart) * scale).dp
                val timeHeight = ((event.endMinutes - event.startMinutes).coerceAtLeast(1) * scale).dp
                val cardHeight = timeHeight.coerceAtLeast(PARALLEL_MIN_CARD_HEIGHT)
                EventCard(
                    item = item,
                    nowMinutes = nowMinutes,
                    onClick = { onSelectEvent(item) },
                    compact = true,
                    fillHeight = true,
                    modifier = Modifier
                        .offset(y = cardOffset)
                        .height(cardHeight)
                        .padding(bottom = PARALLEL_CARD_GAP),
                )
            }
        }
    }
}

@Composable
private fun GapCard(item: TimelineItem.Gap, nowMinutes: Int? = null) {
    val inProgress = nowMinutes != null && nowMinutes in item.startMinutes until item.endMinutes
    val progressFraction = if (inProgress && item.endMinutes > item.startMinutes) {
        ((nowMinutes!! - item.startMinutes).toFloat() / (item.endMinutes - item.startMinutes))
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressColor = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.gap_label, TimeFormat.durationLabel(item.startMinutes, item.endMinutes)),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = TimeFormat.rangeLabel(item.startMinutes, item.endMinutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            item.ongoingNotes.forEach { note ->
                Text(
                    text = stringResource(R.string.gap_ongoing, note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
            }

            // 空闲进行中：底部细进度条，宽度 = 已过时长占比，标明当前进行到哪。
            if (inProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(progressColor),
                    )
                }
            }
        }
    }
}

/**
 * 事件详情：点击概览卡片后从底部弹出。展示完整信息（时间、时长、地点、关联演出/特典、
 * 重叠警告与备注），左下角放编辑悬浮按钮，点击进入编辑页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailSheet(
    item: TimelineItem.Event,
    events: List<ScheduleEvent>,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onCompleteTokuten: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val event = item.event
    val dark = LocalDarkTheme.current
    val colors = remember(event.eventType, event.tokutenKind, dark) {
        eventTypeColors(event).adaptTo(dark)
    }

    val linkedPerformances = remember(event, events) {
        events.filter {
            EventType.fromStorage(event.eventType) == EventType.TOKUTEN &&
                EventType.fromStorage(it.eventType) == EventType.PERFORMANCE &&
                event.sharesTeamWith(it)
        }.sortedBy { it.startMinutes }
    }
    val linkedTokuten = remember(event, events) {
        events.filter {
            EventType.fromStorage(event.eventType) == EventType.PERFORMANCE &&
                EventType.fromStorage(it.eventType) == EventType.TOKUTEN &&
                event.sharesTeamWith(it)
        }.sortedBy { it.startMinutes }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.teamDisplay.ifBlank { event.title }.ifBlank {
                            stringResource(R.string.event_untitled)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        color = colors.container,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = EventLabels.typeChip(event),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (event.title.isNotBlank() && event.teamDisplay.isNotBlank()) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = TimeFormat.rangeLabel(event.startMinutes, event.endMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.event_detail_duration,
                        TimeFormat.durationLabel(event.startMinutes, event.endMinutes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (event.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = event.location,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                linkedPerformances.forEach { linked ->
                    Text(
                        text = stringResource(
                            R.string.event_linked_performance,
                            linked.teamDisplay.ifBlank { linked.title },
                            TimeFormat.rangeLabel(linked.startMinutes, linked.endMinutes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                linkedTokuten.forEach { linked ->
                    Text(
                        text = stringResource(
                            R.string.event_linked_tokuten,
                            EventLabels.typeChip(linked),
                            TimeFormat.rangeLabel(linked.startMinutes, linked.endMinutes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 用户备注：中性样式展示，与冲突警告区分。
                item.note?.let { note ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 仅真实时间冲突才显示警告感叹号。
                item.overlapNotes.forEach { note ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                if (EventType.fromStorage(event.eventType) == EventType.TOKUTEN) {
                    if (event.completed) {
                        Text(
                            text = stringResource(R.string.event_completed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (onCompleteTokuten != null) {
                        Button(
                            onClick = onCompleteTokuten,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_complete_event))
                        }
                    }
                }
            }

            // 左下角编辑悬浮按钮：从概览卡进入详情后，再进入编辑页。
            SmallFloatingActionButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cd_edit_event),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventCardPreview() {
    ScheduleTimelineTheme {
        EventCard(
            item = TimelineItem.Event(
                event = ScheduleEvent(
                    id = 2,
                    team = "StarDiary",
                    eventType = EventType.TOKUTEN.storage,
                    tokutenKind = TokutenKind.PARALLEL.storage,
                    title = "午场",
                    location = "吧台A",
                    startMinutes = 17 * 60,
                    endMinutes = 19 * 60,
                    dayKey = "2026-07-12",
                ),
                overlapNotes = listOf("与StarCandy演出重叠"),
            ),
            nowMinutes = 18 * 60,
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EventDetailSheetPreview() {
    ScheduleTimelineTheme {
        EventDetailSheet(
            item = TimelineItem.Event(
                event = ScheduleEvent(
                    id = 1,
                    team = "StarDiary",
                    eventType = EventType.PERFORMANCE.storage,
                    title = "午场",
                    location = "昨日世界酒馆 C区",
                    startMinutes = 14 * 60,
                    endMinutes = 16 * 60,
                    dayKey = "2026-07-12",
                ),
                note = "记得携带会员卡与特典券",
                overlapNotes = listOf("与StarCandy特典重叠"),
            ),
            events = emptyList(),
            onEdit = {},
            onDismiss = {},
        )
    }
}
