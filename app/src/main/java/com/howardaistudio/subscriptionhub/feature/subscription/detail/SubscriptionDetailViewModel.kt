package com.howardaistudio.subscriptionhub.feature.subscription.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.domain.forecast.EstimatedAmount
import com.howardaistudio.subscriptionhub.domain.forecast.ForecastCalculator
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.domain.usecase.ManageSubscriptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SubscriptionDetail(
    val subscription: Subscription,
    val nextBillingDate: LocalDate?,
    val inTrial: Boolean,
    val upcoming: List<LocalDate>,
    val monthlyEquivalent: EstimatedAmount?,
)

sealed interface SubscriptionDetailUiState {
    data object Loading : SubscriptionDetailUiState
    /** Deleted elsewhere or opened from a stale link (SA FR-021). */
    data object NotFound : SubscriptionDetailUiState
    data object Deleted : SubscriptionDetailUiState
    data class Ready(val detail: SubscriptionDetail) : SubscriptionDetailUiState
}

@HiltViewModel
class SubscriptionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: SubscriptionRepository,
    private val manage: ManageSubscriptions,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val id: String = checkNotNull(savedStateHandle.get<String>(ARG_ID)) { "Missing subscription id" }
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<SubscriptionDetailUiState> =
        combine(repository.observe(id), deleted) { subscription, wasDeleted ->
            when {
                wasDeleted -> SubscriptionDetailUiState.Deleted
                subscription == null -> SubscriptionDetailUiState.NotFound
                else -> SubscriptionDetailUiState.Ready(detailOf(subscription, timeSource.today()))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionDetailUiState.Loading)

    /** Date proposed when resuming: the next date on the existing schedule. */
    fun suggestedResumeDate(): LocalDate {
        val current = (uiState.value as? SubscriptionDetailUiState.Ready)?.detail?.subscription
        return current?.schedule?.firstOnOrAfter(timeSource.today()) ?: timeSource.today()
    }

    fun stop(status: SubscriptionStatus) {
        viewModelScope.launch { manage.stop(id, status) }
    }

    fun resume(nextBillingDate: LocalDate) {
        viewModelScope.launch { manage.resume(id, nextBillingDate) }
    }

    fun delete() {
        viewModelScope.launch {
            deleted.value = true
            manage.delete(id)
        }
    }

    companion object {
        const val ARG_ID = "id"
        private const val UPCOMING_COUNT = 5

        fun detailOf(subscription: Subscription, today: LocalDate): SubscriptionDetail {
            val projects = subscription.status.projectsCharges
            return SubscriptionDetail(
                subscription = subscription,
                nextBillingDate = subscription.nextBillingDate(today),
                inTrial = subscription.isInTrial(today),
                upcoming = if (projects) subscription.schedule.upcoming(today, UPCOMING_COUNT) else emptyList(),
                monthlyEquivalent = ForecastCalculator.monthlyEquivalents(listOf(subscription)).firstOrNull(),
            )
        }
    }
}
