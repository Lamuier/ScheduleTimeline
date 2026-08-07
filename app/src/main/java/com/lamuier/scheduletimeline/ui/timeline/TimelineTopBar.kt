package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.lamuier.scheduletimeline.R
import com.lamuier.scheduletimeline.ui.theme.ScheduleTimelineTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineTopBar(
    currentDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: () -> Unit,
    onManage: () -> Unit,
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            actions = {
                IconButton(onClick = onManage) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousDay) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_prev_day),
                    )
                }
                AnimatedContent(
                    targetState = currentDate,
                    transitionSpec = {
                        val forward = targetState.isAfter(initialState)
                        val enter = slideInHorizontally { width -> if (forward) width else -width } + fadeIn()
                        val exit = slideOutHorizontally { width -> if (forward) -width else width } + fadeOut()
                        enter togetherWith exit
                    },
                    contentKey = { it },
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onSelectDate),
                    contentAlignment = Alignment.Center,
                    label = "timeline-date",
                ) { date ->
                    Text(
                        text = date.format(
                            DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern)),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onNextDay) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_next_day),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineTopBarPreview() {
    ScheduleTimelineTheme {
        TimelineTopBar(
            currentDate = LocalDate.of(2026, 8, 4),
            onPreviousDay = {},
            onNextDay = {},
            onSelectDate = {},
            onManage = {},
        )
    }
}
