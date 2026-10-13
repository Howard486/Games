package com.howardaistudio.subscriptionhub.domain.usecase

import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import com.howardaistudio.subscriptionhub.domain.validation.DraftField
import com.howardaistudio.subscriptionhub.domain.validation.DraftValidation
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraft
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraftValidator
import java.time.LocalDate
import java.util.UUID

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Invalid(val errors: Map<DraftField, DraftError>) : SaveResult
    data object NotFound : SaveResult
}

/**
 * Subscription commands for the list, form and detail screens (SA FR-001..FR-008, §5.1).
 * All writes go through here so every screen applies the same rules.
 */
class ManageSubscriptions(
    private val repository: SubscriptionRepository,
    private val timeSource: TimeSource,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** Form values for editing [subscription], showing its next billing date as of [today]. */
    fun draftFor(subscription: Subscription, today: LocalDate): SubscriptionDraft {
        val next = subscription.schedule.firstOnOrAfter(today)
        return SubscriptionDraft(
            name = subscription.name,
            category = subscription.category,
            amountInput = subscription.price.toBigDecimal().stripTrailingZeros().toPlainString(),
            currencyCode = subscription.price.currencyCode,
            cycle = subscription.schedule.cycle,
            nextBillingDate = next,
            monthEnd = subscription.schedule.impliesMonthEnd(next),
            note = subscription.note.orEmpty(),
            reminderEnabled = subscription.reminderEnabled,
            trial = subscription.status == SubscriptionStatus.TRIAL,
        )
    }

    /**
     * Creates ([existingId] == null) or updates a subscription.
     *
     * On edit, an unchanged cycle / date / month-end choice keeps the original schedule, so the
     * billing anchor is not silently moved (FR-011). A changed date becomes the new anchor
     * (SA §6.1 boundary rule). Status and creation time are preserved; a new price only affects
     * future estimates because history is never stored (FR-013).
     */
    suspend fun save(draft: SubscriptionDraft, existingId: String?): SaveResult {
        val existing = existingId?.let { repository.get(it) ?: return SaveResult.NotFound }
        val valid = when (val result = SubscriptionDraftValidator.validate(draft)) {
            is DraftValidation.Invalid -> return SaveResult.Invalid(result.errors)
            is DraftValidation.Valid -> result
        }
        val now = timeSource.now()
        val subscription = if (existing == null) {
            Subscription(
                id = newId(),
                name = valid.name,
                category = valid.category,
                status = if (draft.trial) SubscriptionStatus.TRIAL else SubscriptionStatus.ACTIVE,
                price = valid.price,
                schedule = valid.schedule,
                note = valid.note,
                reminderEnabled = draft.reminderEnabled,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            val original = draftFor(existing, timeSource.today())
            val scheduleUnchanged = draft.cycle == original.cycle &&
                draft.nextBillingDate == original.nextBillingDate &&
                draft.monthEnd == original.monthEnd
            existing.copy(
                name = valid.name,
                category = valid.category,
                price = valid.price,
                schedule = if (scheduleUnchanged) existing.schedule else valid.schedule,
                note = valid.note,
                reminderEnabled = draft.reminderEnabled,
                updatedAt = now,
            )
        }
        repository.upsert(subscription)
        return SaveResult.Saved(subscription.id)
    }

    /**
     * Stops projecting charges: PAUSED, CANCELLED or ARCHIVED. Reminders stop automatically
     * because the planner only considers ACTIVE / TRIAL (FR-022). Use [resume] to reactivate.
     */
    suspend fun stop(id: String, status: SubscriptionStatus): Boolean {
        require(!status.projectsCharges) { "Use resume() to reactivate a subscription" }
        val existing = repository.get(id) ?: return false
        if (existing.status == status) return true
        repository.upsert(existing.copy(status = status, updatedAt = timeSource.now()))
        return true
    }

    /**
     * Reactivates with a user-confirmed next billing date (SA §5.1: resuming requires
     * re-confirming the renewal date). Keeps the original schedule when the date still matches it.
     */
    suspend fun resume(id: String, nextBillingDate: LocalDate): Boolean {
        val existing = repository.get(id) ?: return false
        val schedule = if (existing.schedule.isBillingDate(nextBillingDate)) {
            existing.schedule
        } else {
            BillingSchedule.fromUserInput(
                existing.schedule.cycle,
                nextBillingDate,
                monthEnd = existing.schedule.impliesMonthEnd(nextBillingDate),
            )
        }
        repository.upsert(existing.copy(status = SubscriptionStatus.ACTIVE, schedule = schedule, updatedAt = timeSource.now()))
        return true
    }

    /** Deletes the subscription and, by cascade, its reminder logs (FR-006). */
    suspend fun delete(id: String) {
        repository.delete(id)
    }
}
