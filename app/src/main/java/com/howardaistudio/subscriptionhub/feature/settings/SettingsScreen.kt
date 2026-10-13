package com.howardaistudio.subscriptionhub.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.BuildConfig
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.ChipRow
import com.howardaistudio.subscriptionhub.core.ui.CurrencyPicker
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.SwitchRow
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPreferences
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.settings.ThemeMode
import com.howardaistudio.subscriptionhub.notification.AndroidReminderNotifier
import com.howardaistudio.subscriptionhub.notification.openNotificationSettings
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        viewModel.export(uri)
    }

    val message = state.message
    val messageText = when (message) {
        is SettingsMessage.Exported -> stringResource(R.string.export_done, message.count)
        SettingsMessage.ExportCancelled -> stringResource(R.string.export_cancelled)
        SettingsMessage.ExportFailed -> stringResource(R.string.export_failed)
        SettingsMessage.DataCleared -> stringResource(R.string.clear_done)
        null -> null
    }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val settings = state.settings
        if (settings == null) {
            LoadingContent(Modifier.padding(padding))
        } else {
            SettingsBody(
                settings = settings,
                subscriptionCount = state.subscriptionCount,
                busy = state.busy,
                viewModel = viewModel,
                onExport = { exportLauncher.launch(viewModel.exportFileName()) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBody(
    settings: AppSettings,
    subscriptionCount: Int,
    busy: Boolean,
    viewModel: SettingsViewModel,
    onExport: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(AndroidReminderNotifier.notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = AndroidReminderNotifier.notificationsAllowed(context)
        onPauseOrDispose { }
    }
    var showTimeDialog by rememberSaveable { mutableStateOf(false) }
    var clearStep by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_general))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_default_currency), modifier = Modifier.weight(1f))
            CurrencyPicker(settings.defaultCurrency, viewModel::setDefaultCurrency)
        }
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        ChipRow {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setTheme(mode) },
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_reminders))
        SwitchRow(stringResource(R.string.settings_reminders_enabled), settings.reminders.enabled, viewModel::setRemindersEnabled)
        Text(stringResource(R.string.settings_reminder_days), style = MaterialTheme.typography.bodyLarge)
        ChipRow {
            ReminderPreferences.SELECTABLE_DAYS.forEach { days ->
                FilterChip(
                    selected = days in settings.reminders.daysBefore,
                    onClick = { viewModel.toggleReminderDay(days) },
                    enabled = settings.reminders.enabled,
                    label = {
                        Text(if (days == 0) stringResource(R.string.reminder_day_zero) else stringResource(R.string.reminder_day_option, days))
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_reminder_time), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showTimeDialog = true }, enabled = settings.reminders.enabled) {
                Text(settings.reminders.preferredTime.toString())
            }
        }
        Text(stringResource(R.string.settings_reminder_time_hint), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_notification_status), modifier = Modifier.weight(1f))
            Text(
                stringResource(if (notificationsAllowed) R.string.notification_status_on else R.string.notification_status_off),
                color = if (notificationsAllowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        if (!notificationsAllowed) {
            TextButton(onClick = { openNotificationSettings(context) }) {
                Text(stringResource(R.string.action_open_notification_settings))
            }
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_data))
        OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_export_csv))
        }
        Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { clearStep = 1 },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.action_clear_data))
        }
        Text(stringResource(R.string.clear_hint), style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_privacy))
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showTimeDialog) {
        ReminderTimeDialog(
            initial = settings.reminders.preferredTime,
            onDismiss = { showTimeDialog = false },
            onConfirm = {
                viewModel.setReminderTime(it)
                showTimeDialog = false
            },
        )
    }

    // SA FR-028: two separate confirmations before deleting everything.
    when (clearStep) {
        1 -> AlertDialog(
            onDismissRequest = { clearStep = 0 },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_body, subscriptionCount)) },
            confirmButton = { TextButton(onClick = { clearStep = 2 }) { Text(stringResource(R.string.action_continue)) } },
            dismissButton = { TextButton(onClick = { clearStep = 0 }) { Text(stringResource(R.string.action_cancel)) } },
        )
        2 -> AlertDialog(
            onDismissRequest = { clearStep = 0 },
            title = { Text(stringResource(R.string.clear_confirm_again_title)) },
            text = { Text(stringResource(R.string.clear_confirm_again_body)) },
            confirmButton = {
                TextButton(onClick = {
                    clearStep = 0
                    viewModel.clearAllData()
                }) { Text(stringResource(R.string.action_clear_now), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearStep = 0 }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
