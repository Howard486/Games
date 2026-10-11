package com.howardaistudio.subscriptionhub.domain.model

import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import java.time.Instant
import java.time.LocalDate

enum class Category { AI, VIDEO, CLOUD_STORAGE, SOFTWARE, EDUCATION, OTHER }

/** Subscription lifecycle (SA §5.1). */
enum class SubscriptionStatus {
    /** Future charges are projected. */
    ACTIVE,

    /** Free trial; the schedule anchor is the first paid date (SA BR-10). */
    TRIAL,

    /** No charges projected until resumed; resuming requires re-confirming the billing date. */
    PAUSED,

    /** Renewal cancelled; no further charges projected. */
    CANCELLED,

    /** Kept for records only and hidden from the default list. */
    ARCHIVED,
    ;

    /** Whether charges and reminders are generated for this status (SA BR-07). */
    val projectsCharges: Boolean get() = this == ACTIVE || this == TRIAL
}

data class Subscription(
    val id: String,
    val name: String,
    val category: Category,
    val status: SubscriptionStatus,
    val price: Money,
    val schedule: BillingSchedule,
    val note: String?,
    val reminderEnabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Next estimated billing date on or after [today], or null when no charges are projected. */
    fun nextBillingDate(today: LocalDate): LocalDate? =
        if (status.projectsCharges) schedule.firstOnOrAfter(today) else null

    /** True while a TRIAL has not reached its first paid date. */
    fun isInTrial(today: LocalDate): Boolean = status == SubscriptionStatus.TRIAL && today.isBefore(schedule.anchorDate)

    /**
     * Converts a TRIAL into ACTIVE once its first paid date has been reached (dev plan G5).
     * Returns this instance unchanged otherwise.
     */
    fun withTrialResolved(today: LocalDate, now: Instant): Subscription =
        if (status == SubscriptionStatus.TRIAL && !today.isBefore(schedule.anchorDate)) {
            copy(status = SubscriptionStatus.ACTIVE, updatedAt = now)
        } else {
            this
        }
}
