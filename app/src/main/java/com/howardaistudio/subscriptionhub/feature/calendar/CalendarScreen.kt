package com.howardaistudio.subscriptionhub.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.BillingEventRow
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.MonthSwitcher
import com.howardaistudio.subscriptionhub.core.ui.SectionCard
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.domain.forecast.CalendarGrid
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarScreen(onOpen: (String) -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(state, viewModel::previousMonth, viewModel::nextMonth, viewModel::select, onOpen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarContent(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_calendar)) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val data = state.data
        if (data == null) {
            LoadingContent(Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonthSwitcher(state.month, onPrevious, onNext)
                MonthGrid(state, data.eventsByDate.mapValues { it.value.size }, onSelect)

                SectionCard(stringResource(R.string.calendar_month_total)) {
                    if (data.totals.isEmpty()) {
                        Text(stringResource(R.string.report_none))
                    } else {
                        data.totals.forEach { Text(UiFormat.money(it), style = MaterialTheme.typography.titleMedium) }
                    }
                }

                state.selectedDate?.let { date ->
                    Text(
                        stringResource(R.string.calendar_day_title, UiFormat.date(date)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val events = data.eventsByDate[date].orEmpty()
                    if (events.isEmpty()) {
                        Text(stringResource(R.string.calendar_no_events), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        events.forEach { event ->
                            BillingEventRow(event, state.today, onClick = { onOpen(event.subscriptionId) })
                        }
                    }
                }

                Text(
                    stringResource(R.string.estimate_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, counts: Map<LocalDate, Int>, onSelect: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weeks = remember(state.month, firstDay) { CalendarGrid.weeks(state.month, firstDay) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            CalendarGrid.weekdays(firstDay).forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            count = counts[date] ?: 0,
                            isToday = date == state.today,
                            isSelected = date == state.selectedDate,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, count: Int, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.calendar_day_description, UiFormat.date(date), count)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isSelected) colors.primaryContainer else colors.surface)
            .then(if (isToday) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
            )
            // Marker only; the count is in the content description and the day list below.
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (count > 0) colors.primary else colors.surface.copy(alpha = 0f)),
            )
        }
    }
}
