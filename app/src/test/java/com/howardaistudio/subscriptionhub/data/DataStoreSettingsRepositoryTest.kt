package com.howardaistudio.subscriptionhub.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.core.DataStore
import com.howardaistudio.subscriptionhub.data.preferences.DataStoreSettingsRepository
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPreferences
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `empty store yields defaults`() = runBlocking {
        assertEquals(AppSettings(), repository.settings.first())
        assertEquals(ReminderPreferences.DEFAULT, repository.settings.first().reminders)
    }

    @Test
    fun `update round trips every field`() = runBlocking {
        val expected = AppSettings(
            defaultCurrency = "USD",
            reminders = ReminderPreferences(enabled = false, daysBefore = setOf(0, 7), preferredTime = LocalTime.of(20, 30)),
            themeMode = ThemeMode.DARK,
            onboardingDone = true,
            analyticsOptIn = false,
        )
        repository.update { expected }
        assertEquals(expected, repository.settings.first())

        repository.update { it.copy(themeMode = ThemeMode.LIGHT) }
        assertEquals(expected.copy(themeMode = ThemeMode.LIGHT), repository.settings.first())
    }

    @Test
    fun `corrupted values fall back to defaults`() = runBlocking {
        dataStore.edit {
            it[stringPreferencesKey("default_currency")] = "XYZ"
            it[stringPreferencesKey("reminder_local_time")] = "25:99"
            it[stringPreferencesKey("theme_mode")] = "NEON"
            it[stringSetPreferencesKey("reminder_days")] = setOf("3", "abc", "999")
        }
        val settings = repository.settings.first()
        assertEquals("TWD", settings.defaultCurrency)
        assertEquals(LocalTime.of(9, 0), settings.reminders.preferredTime)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(setOf(3), settings.reminders.daysBefore)
    }
}
