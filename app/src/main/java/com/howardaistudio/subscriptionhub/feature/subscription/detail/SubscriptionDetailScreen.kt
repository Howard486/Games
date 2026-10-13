package com.howardaistudio.subscriptionhub.feature.subscription.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.LocalDatePickerDialog
import com.howardaistudio.subscriptionhub.core.ui.MessageContent
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.core.ui.labelRes
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import java.time.LocalDate

@Composable
fun SubscriptionDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SubscriptionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == SubscriptionDetailUiState.Deleted) onBack() }
    SubscriptionDetailContent(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onStop = viewModel::stop,
        suggestedResumeDate = viewModel::suggestedResumeDate,
        onResume = viewModel::resume,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDetailContent(
    state: SubscriptionDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStop: (SubscriptionStatus) -> Unit,
    suggestedResumeDate: () -> LocalDate,
    onResume: (LocalDate) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var resumeDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val ready = state as? SubscriptionDetailUiState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(ready?.detail?.subscription?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { onEdit(ready.detail.subscription.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            SubscriptionDetailUiState.Loading, SubscriptionDetailUiState.Deleted -> LoadingContent(modifier)
            SubscriptionDetailUiState.NotFound -> MessageContent(stringResource(R.string.subscription_not_found), modifier)
            is SubscriptionDetailUiState.Ready -> DetailBody(
                detail = state.detail,
                onStop = onStop,
                onResumeRequest = { resumeDate = suggestedResumeDate() },
                modifier = modifier,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    resumeDate?.let { initial ->
        LocalDatePickerDialog(
            initial = initial,
            onDismiss = { resumeDate = null },
            onConfirm = onResume,
        )
    }
}

@Composable
private fun DetailBody(
    detail: SubscriptionDetail,
    onStop: (SubscriptionStatus) -> Unit,
    onResumeRequest: () -> Unit,
    modifier: Modifier,
) {
    val sub = detail.subscription
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(UiFormat.money(sub.price), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(sub.schedule.cycle.labelRes()), style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()

        InfoRow(stringResource(R.string.detail_status), stringResource(sub.status.labelRes()))
        InfoRow(stringResource(R.string.detail_category), stringResource(sub.category.labelRes()))
        InfoRow(
            stringResource(R.string.detail_next),
            detail.nextBillingDate?.let(UiFormat::date) ?: stringResource(R.string.detail_no_projection),
        )
        if (detail.inTrial) {
            Text(
                stringResource(R.string.detail_trial_until, UiFormat.date(sub.schedule.anchorDate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        detail.monthlyEquivalent?.let {
            InfoRow(stringResource(R.string.detail_monthly_equivalent), UiFormat.estimate(it))
        }
        InfoRow(
            stringResource(R.string.detail_reminder),
            stringResource(if (sub.reminderEnabled) R.string.reminder_on else R.string.reminder_off),
        )
        sub.note?.let { InfoRow(stringResource(R.string.detail_note), it) }

        if (detail.upcoming.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.detail_upcoming), style = MaterialTheme.typography.titleSmall)
            detail.upcoming.forEach { Text(UiFormat.date(it), style = MaterialTheme.typography.bodyMedium) }
        }

        HorizontalDivider()
        StatusActions(sub.status, onStop, onResumeRequest)

        Text(
            stringResource(R.string.estimate_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusActions(status: SubscriptionStatus, onStop: (SubscriptionStatus) -> Unit, onResumeRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.projectsCharges) {
            FilledTonalButton(onClick = { onStop(SubscriptionStatus.PAUSED) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_pause))
            }
            OutlinedButton(onClick = { onStop(SubscriptionStatus.CANCELLED) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel_renewal))
            }
        } else {
            FilledTonalButton(onClick = onResumeRequest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_resume))
            }
            Text(stringResource(R.string.resume_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (status != SubscriptionStatus.ARCHIVED) {
            OutlinedButton(onClick = { onStop(SubscriptionStatus.ARCHIVED) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_archive))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
