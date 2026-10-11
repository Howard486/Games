package com.howardaistudio.subscriptionhub.data.local

import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderKey
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import java.time.Instant
import java.time.LocalDate

/** [today] is only used to fill the derived `next_billing_date` cache. */
fun Subscription.toEntity(today: LocalDate): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    category = category.name,
    status = status.name,
    amountMinor = price.amountMinor,
    currencyScale = price.scale,
    currencyCode = price.currencyCode,
    billingCycle = schedule.cycle.name,
    anchorDate = schedule.anchorDate.toString(),
    billingDay = schedule.billingDay,
    nextBillingDate = nextBillingDate(today)?.toString(),
    note = note,
    reminderEnabled = reminderEnabled,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    // Unknown names fall back rather than crash, so a downgrade never bricks the list.
    category = enumValueOrNull<Category>(category) ?: Category.OTHER,
    status = enumValueOrNull<SubscriptionStatus>(status) ?: SubscriptionStatus.PAUSED,
    price = Money(amountMinor, currencyCode, currencyScale),
    schedule = BillingSchedule(BillingCycle.valueOf(billingCycle), LocalDate.parse(anchorDate), billingDay),
    note = note,
    reminderEnabled = reminderEnabled,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

fun ReminderKeyRow.toDomain(): ReminderKey = ReminderKey(subscriptionId, LocalDate.parse(renewalDate), daysBefore)

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
