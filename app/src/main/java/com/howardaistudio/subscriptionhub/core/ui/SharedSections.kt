package com.howardaistudio.subscriptionhub.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.domain.forecast.BillingEvent
import com.howardaistudio.subscriptionhub.domain.forecast.CalendarGrid
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun monthTitle(month: YearMonth): String = stringResource(R.string.month_title, month.year, month.monthValue)

@Composable
fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.action_prev_month))
        }
        Text(
            monthTitle(month),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.action_next_month))
        }
    }
}

/** First-run state: tells the user what to do instead of showing an empty chart (SA §4.3 item 4). */
@Composable
fun EmptyState(title: String, body: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

/** Titled card used for dashboard and report sections. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** One projected charge; always worded as an estimate (SA §4.3 item 5). */
@Composable
fun BillingEventRow(event: BillingEvent, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = CalendarGrid.daysUntil(today, event.date)
    val relative = if (days == 0L) stringResource(R.string.days_until_today) else stringResource(R.string.days_until, days.toInt())
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(event.name) },
        supportingContent = {
            Text(stringResource(R.string.event_supporting, stringResource(R.string.item_next_estimate, UiFormat.date(event.date)), relative))
        },
        trailingContent = { Text(UiFormat.money(event.amount), style = MaterialTheme.typography.titleSmall) },
    )
}
