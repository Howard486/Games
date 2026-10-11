package com.howardaistudio.subscriptionhub.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats amounts as `<ISO code> <number>`, e.g. `USD 20.00`, `TWD 199`, `JPY 1,000`.
 * Number symbols follow [locale]; the stored value is never derived from these strings.
 */
object MoneyFormatter {

    /**
     * Formats an exact amount. Uses the currency's display digits, but never hides a non-zero
     * fraction: TWD 199.50 is shown as `TWD 199.50`, not rounded to `TWD 200`.
     */
    fun format(money: Money, locale: Locale): String {
        val value = money.toBigDecimal()
        val displayDigits = CurrencyCatalog.find(money.currencyCode)?.displayDigits ?: money.scale
        val significant = value.stripTrailingZeros().scale().coerceIn(0, money.scale)
        val digits = if (significant > displayDigits) money.scale else displayDigits
        return formatDecimal(money.currencyCode, value, digits, locale)
    }

    /** Formats an estimate (e.g. a monthly equivalent), rounding half-even to [fractionDigits]. */
    fun formatDecimal(currencyCode: String, value: BigDecimal, fractionDigits: Int, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
            isGroupingUsed = true
            roundingMode = RoundingMode.HALF_EVEN
        }
        return "$currencyCode ${format.format(value)}"
    }

    /** Formats an estimate using the currency's default display digits. */
    fun formatEstimate(currencyCode: String, value: BigDecimal, locale: Locale): String {
        val digits = CurrencyCatalog.find(currencyCode)?.displayDigits ?: value.scale().coerceAtLeast(0)
        return formatDecimal(currencyCode, value, digits, locale)
    }
}
