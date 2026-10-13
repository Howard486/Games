package com.howardaistudio.subscriptionhub.feature.subscription.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.ChipRow
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.MessageContent
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.core.ui.labelRes
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.query.SortOrder

@Composable
fun SubscriptionListScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: SubscriptionListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SubscriptionListContent(
        state = state,
        onAdd = onAdd,
        onOpen = onOpen,
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        onToggleStatus = viewModel::onToggleStatus,
        onToggleCategory = viewModel::onToggleCategory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionListContent(
    state: SubscriptionListUiState,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleStatus: (SubscriptionStatus) -> Unit,
    onToggleCategory: (Category) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_subscriptions)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_subscription))
            }
        },
        // The navigation bar below already handles the system bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            SubscriptionListUiState.Loading -> LoadingContent(modifier)
            is SubscriptionListUiState.Ready ->
                if (state.totalCount == 0) {
                    EmptyState(onAdd, modifier)
                } else {
                    Column(modifier.fillMaxSize()) {
                        Filters(state, onQueryChange, onSortChange, onToggleStatus, onToggleCategory)
                        if (state.rows.isEmpty()) {
                            MessageContent(stringResource(R.string.no_results))
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.rows, key = { it.subscription.id }) { row ->
                                    SubscriptionCard(row, onClick = { onOpen(row.subscription.id) })
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun Filters(
    state: SubscriptionListUiState.Ready,
    onQueryChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleStatus: (SubscriptionStatus) -> Unit,
    onToggleCategory: (Category) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (state.filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear_search))
                    }
                }
            },
        )
        ChipRow {
            SortOrder.entries.forEach { sort ->
                FilterChip(
                    selected = state.filter.sort == sort,
                    onClick = { onSortChange(sort) },
                    label = { Text(stringResource(sort.labelRes())) },
                )
            }
        }
        ChipRow {
            SubscriptionStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in state.filter.statuses,
                    onClick = { onToggleStatus(status) },
                    label = { Text(stringResource(status.labelRes())) },
                )
            }
        }
        ChipRow {
            Category.entries.forEach { category ->
                FilterChip(
                    selected = category in state.filter.categories,
                    onClick = { onToggleCategory(category) },
                    label = { Text(stringResource(category.labelRes())) },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCard(row: SubscriptionRow, onClick: () -> Unit) {
    val sub = row.subscription
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sub.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(UiFormat.money(sub.price), style = MaterialTheme.typography.titleMedium)
            }
            Row {
                Text(
                    stringResource(sub.category.labelRes()) + " · " + stringResource(sub.schedule.cycle.labelRes()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sub.status != SubscriptionStatus.ACTIVE) {
                    Text(
                        stringResource(sub.status.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            val next = row.nextBillingDate
            Text(
                when {
                    next == null -> stringResource(R.string.item_no_projection)
                    row.inTrial -> stringResource(R.string.item_first_payment, UiFormat.date(next))
                    else -> stringResource(R.string.item_next_estimate, UiFormat.date(next))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (row.possibleDuplicate) {
                Text(
                    stringResource(R.string.duplicate_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onAdd) { Text(stringResource(R.string.empty_action)) }
    }
}

private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.NEXT_BILLING_DATE -> R.string.sort_next_date
    SortOrder.AMOUNT -> R.string.sort_amount
    SortOrder.NAME -> R.string.sort_name
}
