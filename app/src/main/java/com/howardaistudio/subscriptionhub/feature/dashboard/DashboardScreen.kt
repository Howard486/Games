package com.howardaistudio.subscriptionhub.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.BillingEventRow
import com.howardaistudio.subscriptionhub.core.ui.EmptyState
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.SectionCard
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.core.ui.monthTitle
import com.howardaistudio.subscriptionhub.domain.forecast.DashboardSummary
import com.howardaistudio.subscriptionhub.notification.NotificationPermissionBanner

@Composable
fun DashboardScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(state, onAdd, onOpen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(state: DashboardUiState, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_dashboard)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_subscription))
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            DashboardUiState.Loading -> LoadingContent(modifier)
            is DashboardUiState.Ready ->
                if (state.summary.totalCount == 0) {
                    EmptyState(
                        title = stringResource(R.string.empty_title),
                        body = stringResource(R.string.empty_body),
                        actionLabel = stringResource(R.string.empty_action),
                        onAction = onAdd,
                        modifier = modifier,
                    )
                } else {
                    DashboardBody(state.summary, onOpen, modifier)
                }
        }
    }
}

@Composable
private fun DashboardBody(summary: DashboardSummary, onOpen: (String) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NotificationPermissionBanner() }
        item {
            SectionCard(stringResource(R.string.dashboard_month_title, monthTitle(summary.month))) {
                if (summary.monthTotals.isEmpty()) {
                    Text(stringResource(R.string.dashboard_no_charges_this_month), style = MaterialTheme.typography.bodyLarge)
                } else {
                    // One line per currency: amounts in different currencies are never added (FR-014).
                    summary.monthTotals.forEach { Text(UiFormat.money(it), style = MaterialTheme.typography.headlineSmall) }
                    Text(
                        stringResource(R.string.dashboard_month_count, summary.monthEventCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (summary.monthlyEquivalents.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.dashboard_monthly_equivalent)) {
                    summary.monthlyEquivalents.forEach { Text(UiFormat.estimate(it), style = MaterialTheme.typography.titleMedium) }
                    Text(
                        stringResource(R.string.dashboard_active_count, summary.activeCount, summary.totalCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(
                    if (summary.upcoming.isEmpty()) R.string.dashboard_upcoming_empty else R.string.dashboard_upcoming_title,
                    summary.upcomingDays.toInt(),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(summary.upcoming, key = { "${it.subscriptionId}-${it.date}" }) { event ->
            BillingEventRow(event, summary.today, onClick = { onOpen(event.subscriptionId) })
        }
        item {
            Text(
                stringResource(R.string.estimate_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
