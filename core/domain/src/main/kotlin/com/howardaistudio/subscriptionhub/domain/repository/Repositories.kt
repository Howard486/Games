package com.howardaistudio.subscriptionhub.domain.repository

import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderKey
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface SubscriptionRepository {
    /** All subscriptions, including ARCHIVED; filtering is done by `SubscriptionQuery`. */
    fun observeAll(): Flow<List<Subscription>>

    fun observe(id: String): Flow<Subscription?>

    suspend fun get(id: String): Subscription?

    suspend fun getAll(): List<Subscription>

    /** Inserts or updates in place; never delete-and-reinsert, which would cascade-drop reminder logs. */
    suspend fun upsert(subscription: Subscription)

    /** Deletes one subscription; its reminder logs are removed by FK cascade (SA FR-006, FR-022). */
    suspend fun delete(id: String)

    /** Removes all subscriptions and reminder logs in one transaction (SA FR-028). */
    suspend fun deleteAll()

    /**
     * Rewrites the cached `next_billing_date` column for [today] and resolves finished trials
     * (dev plan G1, G5). The schedule anchor stays the source of truth.
     */
    suspend fun refreshDerivedState(today: LocalDate, now: Instant)
}

enum class ReminderDeliveryStatus { SENT, SKIPPED_PERMISSION, ERROR }

interface ReminderLogRepository {
    /** Keys already SENT for renewals on or after [fromDate]; other statuses never count (dev plan G3). */
    suspend fun sentKeys(fromDate: LocalDate): Set<ReminderKey>

    /**
     * Records a delivery attempt. A SENT row is final; any other existing row for the same key is
     * overwritten, so a later successful delivery can replace SKIPPED_PERMISSION or ERROR.
     */
    suspend fun record(key: ReminderKey, status: ReminderDeliveryStatus, at: Instant)

    /** Drops logs for renewals before [date]; they can no longer affect de-duplication. */
    suspend fun pruneBefore(date: LocalDate)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
