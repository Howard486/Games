package com.howardaistudio.subscriptionhub.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.domain.forecast.DashboardSummary
import com.howardaistudio.subscriptionhub.domain.forecast.Summaries
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Ready(val summary: DashboardSummary) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: SubscriptionRepository,
    timeSource: TimeSource,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = repository.observeAll()
        .map { DashboardUiState.Ready(Summaries.dashboard(it, timeSource.today())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)
}
