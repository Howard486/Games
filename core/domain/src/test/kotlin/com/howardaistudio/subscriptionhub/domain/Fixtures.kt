package com.howardaistudio.subscriptionhub.domain

import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

fun date(iso: String): LocalDate = LocalDate.parse(iso)

fun money(amount: String, currency: String): Money = Money.of(BigDecimal(amount), CurrencyCatalog.require(currency))

private val FIXED_INSTANT: Instant = Instant.parse("2026-10-10T00:00:00Z")

fun subscription(
    id: String = "sub-1",
    name: String = "ChatGPT Plus",
    amount: String = "20.00",
    currency: String = "USD",
    cycle: BillingCycle = BillingCycle.MONTHLY,
    anchor: String = "2026-10-15",
    billingDay: Int? = null,
    status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    category: Category = Category.AI,
    reminderEnabled: Boolean = true,
    note: String? = null,
): Subscription {
    val anchorDate = date(anchor)
    return Subscription(
        id = id,
        name = name,
        category = category,
        status = status,
        price = money(amount, currency),
        schedule = BillingSchedule(cycle, anchorDate, billingDay ?: anchorDate.dayOfMonth),
        note = note,
        reminderEnabled = reminderEnabled,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )
}
