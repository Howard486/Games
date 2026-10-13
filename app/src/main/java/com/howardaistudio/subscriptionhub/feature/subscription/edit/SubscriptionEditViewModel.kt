package com.howardaistudio.subscriptionhub.feature.subscription.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog
import com.howardaistudio.subscriptionhub.domain.query.DuplicateNames
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.domain.usecase.ManageSubscriptions
import com.howardaistudio.subscriptionhub.domain.usecase.SaveResult
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import com.howardaistudio.subscriptionhub.domain.validation.DraftField
import com.howardaistudio.subscriptionhub.domain.validation.DraftValidation
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraft
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraftValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionFormState(
    val isNew: Boolean,
    val loading: Boolean = true,
    val draft: SubscriptionDraft = SubscriptionDraft(
        name = "",
        category = Category.OTHER,
        amountInput = "",
        currencyCode = CurrencyCatalog.DEFAULT_CODE,
        cycle = BillingCycle.MONTHLY,
        nextBillingDate = null,
    ),
    val errors: Map<DraftField, DraftError> = emptyMap(),
    /** Names of other subscriptions with the same name (SA FR-008: a hint, never a blocker). */
    val duplicateNames: List<String> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val notFound: Boolean = false,
) {
    /** Ask "last day of every month?" only when the chosen date makes it ambiguous (dev plan G2). */
    val showMonthEndOption: Boolean
        get() = draft.cycle != BillingCycle.WEEKLY &&
            draft.nextBillingDate?.let(BillingSchedule::isMonthEndCandidate) == true
}

/** Create (no `id` argument) or edit (`id` argument) a subscription (SA SCR-04). */
@HiltViewModel
class SubscriptionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SubscriptionRepository,
    private val settings: SettingsRepository,
    private val manage: ManageSubscriptions,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.get<String>(ARG_ID)
    private val _state = MutableStateFlow(SubscriptionFormState(isNew = editingId == null))
    val state: StateFlow<SubscriptionFormState> = _state.asStateFlow()

    private var others: List<Subscription> = emptyList()
    /** After the first save attempt, errors follow every edit; before that the form stays quiet. */
    private var submitted = false

    init {
        viewModelScope.launch {
            val all = repository.getAll()
            others = all.filter { it.id != editingId }
            if (editingId == null) {
                val currency = settings.settings.first().defaultCurrency
                _state.update { it.copy(loading = false, draft = it.draft.copy(currencyCode = currency)) }
            } else {
                val existing = all.firstOrNull { it.id == editingId }
                _state.update {
                    if (existing == null) {
                        it.copy(loading = false, notFound = true)
                    } else {
                        it.copy(loading = false, draft = manage.draftFor(existing, timeSource.today()))
                    }
                }
            }
            refreshDerived()
        }
    }

    fun update(transform: (SubscriptionDraft) -> SubscriptionDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
        refreshDerived()
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || current.saved) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = manage.save(current.draft, editingId)) {
                is SaveResult.Saved -> _state.update { it.copy(saving = false, saved = true, errors = emptyMap()) }
                is SaveResult.Invalid -> {
                    submitted = true
                    _state.update { it.copy(saving = false, errors = result.errors) }
                }
                SaveResult.NotFound -> _state.update { it.copy(saving = false, notFound = true) }
            }
        }
    }

    private fun refreshDerived() {
        _state.update { state ->
            val errors = if (submitted) {
                (SubscriptionDraftValidator.validate(state.draft) as? DraftValidation.Invalid)?.errors.orEmpty()
            } else {
                state.errors
            }
            val duplicates = DuplicateNames.matches(state.draft.name, others).map { it.name }.distinct()
            state.copy(errors = errors, duplicateNames = duplicates)
        }
    }

    companion object {
        /** Matches the `id` property of the edit route; absent for the create route. */
        const val ARG_ID = "id"
    }
}
