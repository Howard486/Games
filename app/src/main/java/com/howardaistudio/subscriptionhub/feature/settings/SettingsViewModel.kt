package com.howardaistudio.subscriptionhub.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.data.export.SubscriptionExporter
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.settings.ThemeMode
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.notification.ReminderNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed interface SettingsMessage {
    data class Exported(val count: Int) : SettingsMessage
    data object ExportCancelled : SettingsMessage
    data object ExportFailed : SettingsMessage
    data object DataCleared : SettingsMessage
}

data class SettingsUiState(
    val settings: AppSettings? = null,
    val subscriptionCount: Int = 0,
    val busy: Boolean = false,
    val message: SettingsMessage? = null,
)

/** SA SCR-08: preferences, reminder options, CSV export and data deletion. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val subscriptions: SubscriptionRepository,
    private val exporter: SubscriptionExporter,
    private val notifier: ReminderNotifier,
    private val timeSource: TimeSource,
) : ViewModel() {

    private data class Transient(val busy: Boolean = false, val message: SettingsMessage? = null)

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, subscriptions.observeAll().map { it.size }, transient) { settings, count, t ->
            SettingsUiState(settings, count, t.busy, t.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Suggested export file name, e.g. `subscription-hub-20261013.csv`. */
    fun exportFileName(): String = "subscription-hub-${timeSource.today().format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"

    fun setDefaultCurrency(code: String) = edit { it.copy(defaultCurrency = code) }

    fun setTheme(mode: ThemeMode) = edit { it.copy(themeMode = mode) }

    fun setRemindersEnabled(enabled: Boolean) = edit { it.copy(reminders = it.reminders.copy(enabled = enabled)) }

    fun toggleReminderDay(days: Int) = edit {
        val current = it.reminders.daysBefore
        it.copy(reminders = it.reminders.copy(daysBefore = if (days in current) current - days else current + days))
    }

    fun setReminderTime(time: LocalTime) = edit { it.copy(reminders = it.reminders.copy(preferredTime = time)) }

    /** [target] is null when the user cancelled the document picker (AT-26). */
    fun export(target: Uri?) {
        if (target == null) {
            transient.update { it.copy(message = SettingsMessage.ExportCancelled) }
            return
        }
        transient.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                SettingsMessage.Exported(exporter.export(target))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsMessage.ExportFailed
            }
            transient.update { Transient(busy = false, message = message) }
        }
    }

    /** Deletes all subscriptions and reminder logs and clears posted reminders (SA FR-028, G7). */
    fun clearAllData() {
        transient.update { it.copy(busy = true) }
        viewModelScope.launch {
            subscriptions.deleteAll()
            notifier.cancelAll()
            transient.update { Transient(busy = false, message = SettingsMessage.DataCleared) }
        }
    }

    fun messageShown() = transient.update { it.copy(message = null) }

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
