package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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

@Composable
internal fun TimelineList(
    items: List<TimelineItem>,
    onEditEvent: (Long) -> Unit,
    events: List<ScheduleEvent> = emptyList(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
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
                        linkedPerformances = events.filter {
                            EventType.fromStorage(item.event.eventType) == EventType.TOKUTEN &&
                                EventType.fromStorage(it.eventType) == EventType.PERFORMANCE &&
                                item.event.sharesTeamWith(it)
                        }.sortedBy { it.startMinutes },
                        linkedTokuten = events.filter {
                            EventType.fromStorage(item.event.eventType) == EventType.PERFORMANCE &&
                                EventType.fromStorage(it.eventType) == EventType.TOKUTEN &&
                                item.event.sharesTeamWith(it)
                        }.sortedBy { it.startMinutes },
                        onClick = { onEditEvent(item.event.id) },
                    )
                    is TimelineItem.OverlapGroup -> ParallelEventCards(
                        group = item,
                        events = events,
                        onEditEvent = onEditEvent,
                    )
                    is TimelineItem.Gap -> GapCard(item = item)
                }
            }
        }
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
                .padding(top = 12.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = TimeFormat.minutesToHm(item.startMinutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
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

@Composable
private fun EventCard(
    item: TimelineItem.Event,
    linkedPerformances: List<ScheduleEvent>,
    linkedTokuten: List<ScheduleEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val event = item.event
    val dark = LocalDarkTheme.current
    val colors = remember(event.eventType, event.tokutenKind, dark) {
        eventTypeColors(event).adaptTo(dark)
    }
    // 卡片底用类型主色低透明度染色：让卡片在并列时间轨道的空白区里明显跳出来，
    // 同时比 chip / 红框更弱，避免夺走层级焦点。
    val cardContainer = colors.accent.copy(alpha = if (dark) 0.18f else 0.12f)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
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

            linkedPerformances.forEach { linkedPerformance ->
                Text(
                    text = stringResource(
                        R.string.event_linked_performance,
                        linkedPerformance.teamDisplay.ifBlank { linkedPerformance.title },
                        TimeFormat.rangeLabel(
                            linkedPerformance.startMinutes,
                            linkedPerformance.endMinutes,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (!compact) linkedTokuten.forEach { tokuten ->
                Text(
                    text = stringResource(
                        R.string.event_linked_tokuten,
                        EventLabels.typeChip(tokuten),
                        TimeFormat.rangeLabel(tokuten.startMinutes, tokuten.endMinutes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (event.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.location,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item.overlapNotes.forEach { note ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
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

/** 卡片最小可读高度：保证时间、团队、类型与重叠提示至少可见。 */
private val PARALLEL_MIN_CARD_HEIGHT = 96.dp

@Composable
private fun ParallelEventCards(
    group: TimelineItem.OverlapGroup,
    events: List<ScheduleEvent>,
    onEditEvent: (Long) -> Unit,
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
            events = events,
            onEditEvent = onEditEvent,
            modifier = Modifier.weight(1f),
        )
        EventLane(
            label = stringResource(R.string.event_type_tokuten),
            items = group.tokutenEvents,
            groupStart = group.startMinutes,
            groupEnd = group.endMinutes,
            events = events,
            onEditEvent = onEditEvent,
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
    events: List<ScheduleEvent>,
    onEditEvent: (Long) -> Unit,
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
                val linkedPerformances = events.filter {
                    EventType.fromStorage(event.eventType) == EventType.TOKUTEN &&
                        EventType.fromStorage(it.eventType) == EventType.PERFORMANCE &&
                        event.sharesTeamWith(it)
                }.sortedBy { it.startMinutes }
                val linkedTokuten = events.filter {
                    EventType.fromStorage(event.eventType) == EventType.PERFORMANCE &&
                        EventType.fromStorage(it.eventType) == EventType.TOKUTEN &&
                        event.sharesTeamWith(it)
                }.sortedBy { it.startMinutes }
                EventCard(
                    item = item,
                    linkedPerformances = linkedPerformances,
                    linkedTokuten = linkedTokuten,
                    onClick = { onEditEvent(event.id) },
                    compact = true,
                    modifier = Modifier
                        .offset(y = cardOffset)
                        .height(cardHeight),
                )
            }
        }
    }
}

@Composable
private fun GapCard(item: TimelineItem.Gap) {
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
            linkedPerformances = listOf(
                ScheduleEvent(
                    id = 1,
                    team = "StarDiary",
                    eventType = EventType.PERFORMANCE.storage,
                    startMinutes = 14 * 60 + 20,
                    endMinutes = 14 * 60 + 40,
                    dayKey = "2026-07-12",
                ),
            ),
            linkedTokuten = emptyList(),
            onClick = {},
        )
    }
}
