package com.howardaistudio.subscriptionhub.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPreferences
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore keys follow SA §7.1 AppSettings. Unreadable values fall back to defaults
 * instead of failing, so a corrupted preference never blocks the app.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toAppSettings())) }
    }

    internal object Keys {
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_DAYS = stringSetPreferencesKey("reminder_days")
        val REMINDER_LOCAL_TIME = stringPreferencesKey("reminder_local_time")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ANALYTICS_OPT_IN = booleanPreferencesKey("privacy_analytics_opt_in")
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        val reminderDefaults = defaults.reminders
        val days = this[Keys.REMINDER_DAYS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 0..ReminderPreferences.MAX_DAYS_BEFORE }
            ?.toSet()
            ?: reminderDefaults.daysBefore
        val time = this[Keys.REMINDER_LOCAL_TIME]?.let { parseTimeOrNull(it) } ?: reminderDefaults.preferredTime
        return AppSettings(
            defaultCurrency = this[Keys.DEFAULT_CURRENCY]?.let { CurrencyCatalog.find(it)?.code } ?: defaults.defaultCurrency,
            reminders = ReminderPreferences(
                enabled = this[Keys.NOTIFICATIONS_ENABLED] ?: reminderDefaults.enabled,
                daysBefore = days,
                preferredTime = time,
            ),
            themeMode = this[Keys.THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
            analyticsOptIn = this[Keys.ANALYTICS_OPT_IN] ?: defaults.analyticsOptIn,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.DEFAULT_CURRENCY] = settings.defaultCurrency
        this[Keys.NOTIFICATIONS_ENABLED] = settings.reminders.enabled
        this[Keys.REMINDER_DAYS] = settings.reminders.daysBefore.mapTo(HashSet()) { it.toString() }
        this[Keys.REMINDER_LOCAL_TIME] = settings.reminders.preferredTime.toString()
        this[Keys.THEME_MODE] = settings.themeMode.name
        this[Keys.ONBOARDING_DONE] = settings.onboardingDone
        this[Keys.ANALYTICS_OPT_IN] = settings.analyticsOptIn
    }

    private fun parseTimeOrNull(value: String): LocalTime? =
        try {
            LocalTime.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
}
