package com.howardaistudio.subscriptionhub.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.domain.forecast.CalendarMonth
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
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val today: LocalDate,
    val month: YearMonth,
    val selectedDate: LocalDate?,
    /** Null until the first database emission. */
    val data: CalendarMonth?,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    repository: SubscriptionRepository,
    timeSource: TimeSource,
    @ComputeDispatcher computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val today = timeSource.today()
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selected = MutableStateFlow<LocalDate?>(today)

    val uiState: StateFlow<CalendarUiState> =
        combine(repository.observeAll(), month, selected) { subscriptions, currentMonth, selectedDate ->
            CalendarUiState(today, currentMonth, selectedDate, Summaries.calendar(subscriptions, currentMonth))
        }.flowOn(computeDispatcher).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(today, YearMonth.from(today), today, data = null),
        )

    fun previousMonth() = showMonth(month.value.minusMonths(1))

    fun nextMonth() = showMonth(month.value.plusMonths(1))

    fun select(date: LocalDate) {
        selected.value = date
    }

    private fun showMonth(target: YearMonth) {
        month.value = target
        selected.value = if (YearMonth.from(today) == target) today else null
    }
}
