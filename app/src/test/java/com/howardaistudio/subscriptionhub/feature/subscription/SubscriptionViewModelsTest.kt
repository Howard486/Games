package com.howardaistudio.subscriptionhub.feature.subscription

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.query.SortOrder
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.usecase.ManageSubscriptions
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import com.howardaistudio.subscriptionhub.domain.validation.DraftField
import com.howardaistudio.subscriptionhub.feature.subscription.detail.SubscriptionDetailUiState
import com.howardaistudio.subscriptionhub.feature.subscription.detail.SubscriptionDetailViewModel
import com.howardaistudio.subscriptionhub.feature.subscription.edit.SubscriptionEditViewModel
import com.howardaistudio.subscriptionhub.feature.subscription.list.SubscriptionListUiState
import com.howardaistudio.subscriptionhub.feature.subscription.list.SubscriptionListViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubscriptionViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun manage(repo: InMemorySubscriptionRepository) = ManageSubscriptions(repo, FixedTime) { "new-id" }

    private fun editViewModel(repo: InMemorySubscriptionRepository, id: String? = null, currency: String = "USD") =
        SubscriptionEditViewModel(
            savedStateHandle = SavedStateHandle(if (id == null) emptyMap() else mapOf(SubscriptionEditViewModel.ARG_ID to id)),
            repository = repo,
            settings = FakeSettingsRepository(AppSettings(defaultCurrency = currency)),
            manage = manage(repo),
            timeSource = FixedTime,
        )

    // AT-01 / AT-02 through the form
    @Test
    fun `create form uses default currency, reports errors, then saves`() = runTest(main.dispatcher) {
        val repo = InMemorySubscriptionRepository()
        val vm = editViewModel(repo, currency = "JPY")

        val initial = vm.state.value
        assertTrue(initial.isNew)
        assertFalse(initial.loading)
        assertEquals("JPY", initial.draft.currencyCode)

        vm.save()
        assertEquals(DraftError.REQUIRED, vm.state.value.errors[DraftField.NAME])
        assertEquals(DraftError.REQUIRED, vm.state.value.errors[DraftField.NEXT_BILLING_DATE])
        assertFalse(vm.state.value.saved)

        // After a failed save, errors follow edits live.
        vm.update { it.copy(name = "Netflix") }
        assertNull(vm.state.value.errors[DraftField.NAME])

        vm.update { it.copy(amountInput = "390", nextBillingDate = LocalDate.of(2026, 10, 25)) }
        vm.save()

        assertTrue(vm.state.value.saved)
        val saved = repo.get("new-id")!!
        assertEquals(Money(390, "JPY", 0), saved.price)
    }

    @Test
    fun `edit form is prefilled and flags duplicate names`() = runTest(main.dispatcher) {
        val repo = InMemorySubscriptionRepository(
            listOf(testSubscription("a", name = "Netflix", minor = 39000, currency = "TWD"), testSubscription("b", name = "Spotify")),
        )
        val vm = editViewModel(repo, id = "a")

        val state = vm.state.value
        assertFalse(state.isNew)
        assertEquals("Netflix", state.draft.name)
        assertEquals("390", state.draft.amountInput)
        assertEquals(LocalDate.of(2026, 10, 15), state.draft.nextBillingDate)
        assertTrue(state.duplicateNames.isEmpty())

        vm.update { it.copy(name = " spotify ") }
        assertEquals(listOf("Spotify"), vm.state.value.duplicateNames)
    }

    @Test
    fun `month-end option only for ambiguous dates`() = runTest(main.dispatcher) {
        val vm = editViewModel(InMemorySubscriptionRepository())
        vm.update { it.copy(nextBillingDate = LocalDate.of(2027, 2, 28)) }
        assertTrue(vm.state.value.showMonthEndOption)
        vm.update { it.copy(nextBillingDate = LocalDate.of(2027, 3, 31)) }
        assertFalse(vm.state.value.showMonthEndOption)
    }

    @Test
    fun `editing a missing subscription shows not found`() = runTest(main.dispatcher) {
        val vm = editViewModel(InMemorySubscriptionRepository(), id = "gone")
        assertTrue(vm.state.value.notFound)
    }

    // AT-07 / AT-08
    @Test
    fun `list filters, sorts and flags duplicates`() = runTest(main.dispatcher) {
        val repo = InMemorySubscriptionRepository(
            listOf(
                testSubscription("1", name = "Netflix", anchor = "2026-10-25"),
                testSubscription("2", name = "netflix", anchor = "2026-10-12"),
                testSubscription("3", name = "Claude Pro", anchor = "2026-10-20"),
                testSubscription("4", name = "Old", status = SubscriptionStatus.ARCHIVED),
            ),
        )
        val vm = SubscriptionListViewModel(repo, FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        val ready = vm.uiState.value as SubscriptionListUiState.Ready
        assertEquals(4, ready.totalCount)
        assertEquals(listOf("2", "3", "1"), ready.rows.map { it.subscription.id })
        assertEquals(setOf("1", "2"), ready.rows.filter { it.possibleDuplicate }.map { it.subscription.id }.toSet())

        vm.onQueryChange("claude")
        assertEquals(listOf("3"), (vm.uiState.value as SubscriptionListUiState.Ready).rows.map { it.subscription.id })

        vm.onQueryChange("")
        vm.onToggleStatus(SubscriptionStatus.ARCHIVED)
        assertEquals(listOf("4"), (vm.uiState.value as SubscriptionListUiState.Ready).rows.map { it.subscription.id })

        vm.onToggleStatus(SubscriptionStatus.ARCHIVED)
        vm.onSortChange(SortOrder.NAME)
        assertEquals("3", (vm.uiState.value as SubscriptionListUiState.Ready).rows.first().subscription.id)
    }

    @Test
    fun `empty repository reports zero total`() = runTest(main.dispatcher) {
        val vm = SubscriptionListViewModel(InMemorySubscriptionRepository(), FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        assertEquals(0, (vm.uiState.value as SubscriptionListUiState.Ready).totalCount)
    }

    // AT-06 / FR-021
    @Test
    fun `detail shows estimates, changes status and handles deletion`() = runTest(main.dispatcher) {
        val repo = InMemorySubscriptionRepository(listOf(testSubscription("s", anchor = "2026-10-15")))
        val vm = SubscriptionDetailViewModel(SavedStateHandle(mapOf(SubscriptionDetailViewModel.ARG_ID to "s")), repo, manage(repo), FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        val detail = (vm.uiState.value as SubscriptionDetailUiState.Ready).detail
        assertEquals(LocalDate.of(2026, 10, 15), detail.nextBillingDate)
        assertEquals(5, detail.upcoming.size)
        assertEquals("20.00", detail.monthlyEquivalent?.value?.toPlainString())

        vm.stop(SubscriptionStatus.PAUSED)
        val paused = (vm.uiState.value as SubscriptionDetailUiState.Ready).detail
        assertEquals(SubscriptionStatus.PAUSED, paused.subscription.status)
        assertNull(paused.nextBillingDate)
        assertTrue(paused.upcoming.isEmpty())

        vm.resume(vm.suggestedResumeDate())
        assertEquals(SubscriptionStatus.ACTIVE, (vm.uiState.value as SubscriptionDetailUiState.Ready).detail.subscription.status)

        vm.delete()
        assertEquals(SubscriptionDetailUiState.Deleted, vm.uiState.value)
        assertNull(repo.get("s"))
    }

    @Test
    fun `detail for a missing id reports not found`() = runTest(main.dispatcher) {
        val repo = InMemorySubscriptionRepository()
        val vm = SubscriptionDetailViewModel(SavedStateHandle(mapOf(SubscriptionDetailViewModel.ARG_ID to "x")), repo, manage(repo), FixedTime)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        assertEquals(SubscriptionDetailUiState.NotFound, vm.uiState.value)
    }
}
