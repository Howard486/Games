package com.howardaistudio.subscriptionhub.feature.subscription.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.CheckboxRow
import com.howardaistudio.subscriptionhub.core.ui.ChipRow
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.LocalDatePickerDialog
import com.howardaistudio.subscriptionhub.core.ui.MessageContent
import com.howardaistudio.subscriptionhub.core.ui.SwitchRow
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.core.ui.labelRes
import com.howardaistudio.subscriptionhub.core.ui.messageRes
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import com.howardaistudio.subscriptionhub.domain.validation.DraftField
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraft
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraftValidator

@Composable
fun SubscriptionEditScreen(onDone: () -> Unit, viewModel: SubscriptionEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    SubscriptionEditContent(state = state, onBack = onDone, onChange = viewModel::update, onSave = viewModel::save)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionEditContent(
    state: SubscriptionFormState,
    onBack: () -> Unit,
    onChange: ((SubscriptionDraft) -> SubscriptionDraft) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.title_new_subscription else R.string.title_edit_subscription))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.loading && !state.saving && !state.notFound) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingContent(modifier)
            state.notFound -> MessageContent(stringResource(R.string.subscription_not_found), modifier)
            else -> FormFields(state, onChange, modifier)
        }
    }
}

@Composable
private fun FormFields(
    state: SubscriptionFormState,
    onChange: ((SubscriptionDraft) -> SubscriptionDraft) -> Unit,
    modifier: Modifier,
) {
    val draft = state.draft
    val errors = state.errors
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = errors[DraftField.NAME] != null,
            supportingText = {
                FieldSupport(errors[DraftField.NAME], draft.name.trim().length, SubscriptionDraftValidator.NAME_MAX_LENGTH)
            },
        )
        if (state.duplicateNames.isNotEmpty()) {
            Text(
                stringResource(R.string.duplicate_warning, state.duplicateNames.joinToString("、")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Section(stringResource(R.string.field_category)) {
            ChipRow {
                Category.entries.forEach { category ->
                    FilterChip(
                        selected = draft.category == category,
                        onClick = { onChange { it.copy(category = category) } },
                        label = { Text(stringResource(category.labelRes())) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = draft.amountInput,
                onValueChange = { value -> onChange { it.copy(amountInput = value) } },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.field_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errors[DraftField.AMOUNT] != null,
                supportingText = { errors[DraftField.AMOUNT]?.let { Text(stringResource(it.messageRes())) } },
            )
            CurrencyPicker(
                selected = draft.currencyCode,
                onSelect = { code -> onChange { it.copy(currencyCode = code) } },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Section(stringResource(R.string.field_cycle)) {
            ChipRow {
                BillingCycle.entries.forEach { cycle ->
                    FilterChip(
                        selected = draft.cycle == cycle,
                        onClick = { onChange { it.copy(cycle = cycle) } },
                        label = { Text(stringResource(cycle.labelRes())) },
                    )
                }
            }
        }

        val dateLabel = stringResource(if (draft.trial) R.string.field_first_payment_date else R.string.field_next_date)
        Section(dateLabel) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_nav_calendar), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(draft.nextBillingDate?.let(UiFormat::date) ?: stringResource(R.string.select_date))
            }
            errors[DraftField.NEXT_BILLING_DATE]?.let { ErrorText(it) }
            if (state.showMonthEndOption) {
                CheckboxRow(
                    label = stringResource(R.string.month_end_option),
                    checked = draft.monthEnd,
                    onCheckedChange = { checked -> onChange { it.copy(monthEnd = checked) } },
                )
            }
        }

        if (state.isNew) {
            Column {
                SwitchRow(
                    label = stringResource(R.string.trial_option),
                    checked = draft.trial,
                    onCheckedChange = { checked -> onChange { it.copy(trial = checked) } },
                )
                if (draft.trial) {
                    Text(stringResource(R.string.trial_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SwitchRow(
            label = stringResource(R.string.reminder_option),
            checked = draft.reminderEnabled,
            onCheckedChange = { checked -> onChange { it.copy(reminderEnabled = checked) } },
        )

        OutlinedTextField(
            value = draft.note,
            onValueChange = { value -> onChange { it.copy(note = value) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_note)) },
            minLines = 3,
            isError = errors[DraftField.NOTE] != null,
            supportingText = {
                FieldSupport(errors[DraftField.NOTE], draft.note.trim().length, SubscriptionDraftValidator.NOTE_MAX_LENGTH)
            },
        )

        Text(
            stringResource(R.string.estimate_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            initial = draft.nextBillingDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date -> onChange { it.copy(nextBillingDate = date, monthEnd = false) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun FieldSupport(error: DraftError?, length: Int, max: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(error?.let { stringResource(it.messageRes()) }.orEmpty(), modifier = Modifier.weight(1f))
        Text(stringResource(R.string.char_count, length, max))
    }
}

@Composable
private fun ErrorText(error: DraftError) {
    Text(stringResource(error.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun CurrencyPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = stringResource(R.string.field_currency))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyCatalog.all.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency.code) },
                    onClick = {
                        onSelect(currency.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
