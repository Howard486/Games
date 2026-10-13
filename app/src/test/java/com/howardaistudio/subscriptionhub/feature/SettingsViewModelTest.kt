package com.howardaistudio.subscriptionhub.feature

import android.app.Application
import android.net.Uri
import com.howardaistudio.subscriptionhub.data.export.SubscriptionExporter
import com.howardaistudio.subscriptionhub.domain.reminder.DueReminder
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.settings.ThemeMode
import com.howardaistudio.subscriptionhub.feature.settings.SettingsMessage
import com.howardaistudio.subscriptionhub.feature.settings.SettingsViewModel
import com.howardaistudio.subscriptionhub.feature.subscription.FakeSettingsRepository
import com.howardaistudio.subscriptionhub.feature.subscription.FixedTime
import com.howardaistudio.subscriptionhub.feature.subscription.InMemorySubscriptionRepository
import com.howardaistudio.subscriptionhub.feature.subscription.MainDispatcherRule
import com.howardaistudio.subscriptionhub.feature.subscription.testSubscription
import com.howardaistudio.subscriptionhub.notification.ReminderNotifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeExporter(var fail: Boolean = false) : SubscriptionExporter {
        val targets = mutableListOf<Uri>()
        override suspend fun export(target: Uri): Int {
            if (fail) throw IOException("disk full")
            targets += target
            return 2
        }
    }

    private class RecordingNotifier : ReminderNotifier {
        var cancelled = false
        override fun canNotify() = true
        override fun show(reminder: DueReminder, today: LocalDate) = true
        override fun cancelAll() {
            cancelled = true
        }
    }

    private val repo = InMemorySubscriptionRepository(listOf(testSubscription("a"), testSubscription("b")))
    private val settings = FakeSettingsRepository(AppSettings())
    private val exporter = FakeExporter()
    private val notifier = RecordingNotifier()
    private fun viewModel() = SettingsViewModel(settings, repo, exporter, notifier, FixedTime)

    @Test
    fun `updates preferences`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.setDefaultCurrency("USD")
        vm.setTheme(ThemeMode.DARK)
        vm.toggleReminderDay(7)
        vm.toggleReminderDay(0)
        vm.setReminderTime(LocalTime.of(20, 30))

        val current = vm.uiState.value.settings!!
        assertEquals("USD", current.defaultCurrency)
        assertEquals(ThemeMode.DARK, current.themeMode)
        assertEquals(setOf(3, 1, 0), current.reminders.daysBefore)
        assertEquals(LocalTime.of(20, 30), current.reminders.preferredTime)
        assertEquals(2, vm.uiState.value.subscriptionCount)
    }

    // AT-26: cancelling the document picker creates nothing and claims nothing.
    @Test
    fun `export reports success, cancellation and failure`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.export(null)
        assertEquals(SettingsMessage.ExportCancelled, vm.uiState.value.message)
        assertTrue(exporter.targets.isEmpty())
        vm.messageShown()

        vm.export(Uri.parse("content://test/export.csv"))
        assertEquals(SettingsMessage.Exported(2), vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)

        exporter.fail = true
        vm.export(Uri.parse("content://test/export2.csv"))
        assertEquals(SettingsMessage.ExportFailed, vm.uiState.value.message)
    }

    // AT-28
    @Test
    fun `clear all removes subscriptions and posted reminders but keeps preferences`() = runTest(main.dispatcher) {
        settings.update { it.copy(defaultCurrency = "JPY", onboardingDone = true) }
        val vm = viewModel()
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.clearAllData()

        assertTrue(repo.getAll().isEmpty())
        assertTrue(notifier.cancelled)
        assertEquals(SettingsMessage.DataCleared, vm.uiState.value.message)
        assertEquals("JPY", vm.uiState.value.settings!!.defaultCurrency)
        assertTrue(vm.uiState.value.settings!!.onboardingDone)
    }

    @Test
    fun `export file name carries the date`() {
        assertEquals("subscription-hub-20261010.csv", viewModel().exportFileName())
    }
}
