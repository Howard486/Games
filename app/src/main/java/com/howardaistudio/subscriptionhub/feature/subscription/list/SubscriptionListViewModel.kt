package com.howardaistudio.subscriptionhub.feature.subscription.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.query.DuplicateNames
import com.howardaistudio.subscriptionhub.domain.query.SortOrder
import com.howardaistudio.subscriptionhub.domain.query.SubscriptionFilter
import com.howardaistudio.subscriptionhub.domain.query.SubscriptionQuery
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

data class SubscriptionRow(
    val subscription: Subscription,
    val nextBillingDate: LocalDate?,
    val inTrial: Boolean,
    val possibleDuplicate: Boolean,
)

sealed interface SubscriptionListUiState {
    data object Loading : SubscriptionListUiState

    data class Ready(
        val filter: SubscriptionFilter,
        val rows: List<SubscriptionRow>,
        /** All stored subscriptions, before filtering; 0 shows the first-run empty state. */
        val totalCount: Int,
    ) : SubscriptionListUiState
}

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    repository: SubscriptionRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val filter = MutableStateFlow(SubscriptionFilter())

    val uiState: StateFlow<SubscriptionListUiState> =
        combine(repository.observeAll(), filter) { all, currentFilter ->
            val today = timeSource.today()
            val duplicates = DuplicateNames.duplicateIds(all)
            val collator = Collator.getInstance(Locale.getDefault())
            val rows = SubscriptionQuery.apply(all, currentFilter, today) { a, b -> collator.compare(a, b) }
                .map { SubscriptionRow(it, it.nextBillingDate(today), it.isInTrial(today), it.id in duplicates) }
            SubscriptionListUiState.Ready(currentFilter, rows, all.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionListUiState.Loading)

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }

    fun onSortChange(sort: SortOrder) = filter.update { it.copy(sort = sort) }

    fun onToggleStatus(status: SubscriptionStatus) = filter.update { it.copy(statuses = it.statuses.toggle(status)) }

    fun onToggleCategory(category: Category) = filter.update { it.copy(categories = it.categories.toggle(category)) }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}
