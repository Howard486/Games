package com.howardaistudio.subscriptionhub.domain.settings

import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPreferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User preferences (SA §7.1 AppSettings). `notifications_enabled`, `reminder_days` and
 * `reminder_local_time` live in [reminders]. UI language follows the system locale for now.
 */
data class AppSettings(
    val defaultCurrency: String = CurrencyCatalog.DEFAULT_CODE,
    val reminders: ReminderPreferences = ReminderPreferences.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = false,
    /** SA D-05: no third-party analytics in the MVP; stored for a future opt-in. */
    val analyticsOptIn: Boolean = false,
)
