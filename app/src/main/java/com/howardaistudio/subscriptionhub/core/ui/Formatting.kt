package com.howardaistudio.subscriptionhub.core.ui

import androidx.annotation.StringRes
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.money.MoneyFormatter
import com.howardaistudio.subscriptionhub.domain.forecast.EstimatedAmount
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formats shared by all screens (SA §4.3: dates like 2026/10/15, amounts per locale). */
object UiFormat {
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    fun date(date: LocalDate): String = DATE.format(date)

    fun money(money: Money): String = MoneyFormatter.format(money, Locale.getDefault())

    fun estimate(amount: EstimatedAmount): String =
        MoneyFormatter.formatEstimate(amount.currencyCode, amount.value, Locale.getDefault())
}

@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.AI -> R.string.category_ai
    Category.VIDEO -> R.string.category_video
    Category.CLOUD_STORAGE -> R.string.category_cloud_storage
    Category.SOFTWARE -> R.string.category_software
    Category.EDUCATION -> R.string.category_education
    Category.OTHER -> R.string.category_other
}

@StringRes
fun SubscriptionStatus.labelRes(): Int = when (this) {
    SubscriptionStatus.ACTIVE -> R.string.status_active
    SubscriptionStatus.TRIAL -> R.string.status_trial
    SubscriptionStatus.PAUSED -> R.string.status_paused
    SubscriptionStatus.CANCELLED -> R.string.status_cancelled
    SubscriptionStatus.ARCHIVED -> R.string.status_archived
}

@StringRes
fun BillingCycle.labelRes(): Int = when (this) {
    BillingCycle.MONTHLY -> R.string.cycle_monthly
    BillingCycle.YEARLY -> R.string.cycle_yearly
    BillingCycle.WEEKLY -> R.string.cycle_weekly
}

@StringRes
fun DraftError.messageRes(): Int = when (this) {
    DraftError.REQUIRED -> R.string.error_required
    DraftError.TOO_LONG -> R.string.error_too_long
    DraftError.INVALID_FORMAT -> R.string.error_invalid_format
    DraftError.TOO_MANY_DECIMALS -> R.string.error_too_many_decimals
    DraftError.NOT_POSITIVE -> R.string.error_not_positive
    DraftError.TOO_LARGE -> R.string.error_too_large
    DraftError.UNSUPPORTED_CURRENCY -> R.string.error_unsupported_currency
}
