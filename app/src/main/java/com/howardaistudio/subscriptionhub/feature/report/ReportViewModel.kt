package com.howardaistudio.subscriptionhub.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.domain.forecast.MonthReport
import com.howardaistudio.subscriptionhub.domain.forecast.Summaries
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.di.ComputeDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

sealed interface ReportUiState {
    data object Loading : ReportUiState
    data class Ready(val report: MonthReport, val subscriptionCount: Int) : ReportUiState
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    repository: SubscriptionRepository,
    timeSource: TimeSource,
    @ComputeDispatcher computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(timeSource.today()))

    val uiState: StateFlow<ReportUiState> =
        combine(repository.observeAll(), month) { subscriptions, currentMonth ->
            ReportUiState.Ready(Summaries.report(subscriptions, currentMonth), subscriptions.size)
        }.flowOn(computeDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState.Loading)

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }
}
