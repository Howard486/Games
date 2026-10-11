package com.howardaistudio.subscriptionhub.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SA §7.1 Subscription, schema v1. Dates are ISO `YYYY-MM-DD` text (LocalDate, never UTC
 * timestamps); enums are stored by name. `billing_day` is the dev plan G2 addition.
 * `next_billing_date` is a derived cache (G1); `anchor_date` + `billing_cycle` + `billing_day`
 * are the source of truth.
 */
@Entity(
    tableName = "subscription",
    indices = [Index("status")],
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val status: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_scale") val currencyScale: Int,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "billing_cycle") val billingCycle: String,
    @ColumnInfo(name = "anchor_date") val anchorDate: String,
    @ColumnInfo(name = "billing_day") val billingDay: Int,
    @ColumnInfo(name = "next_billing_date") val nextBillingDate: String?,
    val note: String?,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/** SA §7.1 ReminderLog: local notification de-duplication. */
@Entity(
    tableName = "reminder_log",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscription_id", "renewal_date", "days_before"], unique = true),
        Index("renewal_date"),
    ],
)
data class ReminderLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subscription_id") val subscriptionId: String,
    @ColumnInfo(name = "renewal_date") val renewalDate: String,
    @ColumnInfo(name = "days_before") val daysBefore: Int,
    @ColumnInfo(name = "sent_at_epoch_ms") val sentAtEpochMs: Long,
    val status: String,
)

/** Projection used to rebuild de-duplication keys. */
data class ReminderKeyRow(
    @ColumnInfo(name = "subscription_id") val subscriptionId: String,
    @ColumnInfo(name = "renewal_date") val renewalDate: String,
    @ColumnInfo(name = "days_before") val daysBefore: Int,
)
