package com.howardaistudio.subscriptionhub.feature

import android.app.Application
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.feature.calendar.CalendarViewModel
import com.howardaistudio.subscriptionhub.feature.dashboard.DashboardUiState
import com.howardaistudio.subscriptionhub.feature.dashboard.DashboardViewModel
import com.howardaistudio.subscriptionhub.feature.report.ReportUiState
import com.howardaistudio.subscriptionhub.feature.report.ReportViewModel
import com.howardaistudio.subscriptionhub.feature.subscription.FixedTime
import com.howardaistudio.subscriptionhub.feature.subscription.InMemorySubscriptionRepository
import com.howardaistudio.subscriptionhub.feature.subscription.MainDispatcherRule
import com.howardaistudio.subscriptionhub.feature.subscription.testSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OverviewViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = InMemorySubscriptionRepository(
        listOf(
            testSubscription("usd", minor = 2000, currency = "USD", anchor = "2026-10-15"),
            testSubscription("twd", name = "Netflix", minor = 39000, currency = "TWD", anchor = "2026-10-15"),
        ),
    )

    // AT-14 / AT-23
    @Test
    fun `dashboard keeps currencies apart`() = runTest(main.dispatcher) {
        val vm = DashboardViewModel(repo, FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        val summary = (vm.uiState.value as DashboardUiState.Ready).summary
        assertEquals(listOf(Money(39000, "TWD", 2), Money(2000, "USD", 2)), summary.monthTotals)
        assertEquals(2, summary.upcoming.size)
    }

    // AT-24
    @Test
    fun `calendar navigates months and keeps today selected only in its month`() = runTest(main.dispatcher) {
        val vm = CalendarViewModel(repo, FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        val october = vm.uiState.value
        assertEquals(YearMonth.of(2026, 10), october.month)
        assertEquals(LocalDate.of(2026, 10, 10), october.selectedDate)
        assertEquals(2, october.data!!.eventsByDate.getValue(LocalDate.of(2026, 10, 15)).size)

        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.month)
        assertNull(vm.uiState.value.selectedDate)

        vm.select(LocalDate.of(2026, 11, 15))
        assertEquals(LocalDate.of(2026, 11, 15), vm.uiState.value.selectedDate)

        vm.previousMonth()
        assertEquals(LocalDate.of(2026, 10, 10), vm.uiState.value.selectedDate)
    }

    // AT-25
    @Test
    fun `report changes month and splits categories per currency`() = runTest(main.dispatcher) {
        val vm = ReportViewModel(repo, FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        val ready = vm.uiState.value as ReportUiState.Ready
        assertEquals(2, ready.subscriptionCount)
        assertEquals(listOf("TWD", "USD"), ready.report.categoryBreakdown.map { it.currencyCode })

        vm.previousMonth()
        assertEquals(YearMonth.of(2026, 9), (vm.uiState.value as ReportUiState.Ready).report.month)
        assertEquals(emptyList<Money>(), (vm.uiState.value as ReportUiState.Ready).report.monthTotals)
    }
}
