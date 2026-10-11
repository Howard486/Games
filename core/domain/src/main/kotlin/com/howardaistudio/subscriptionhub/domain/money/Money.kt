package com.howardaistudio.subscriptionhub.domain.money

import java.math.BigDecimal

/**
 * An exact monetary amount: [amountMinor] units of 10^-[scale] [currencyCode].
 * Floating point is never used for stored amounts (SA BR-09).
 */
data class Money(
    val amountMinor: Long,
    val currencyCode: String,
    val scale: Int,
) {
    init {
        require(scale in 0..MAX_SCALE) { "scale out of range: $scale" }
        require(CURRENCY_CODE.matches(currencyCode)) { "invalid ISO 4217 code: $currencyCode" }
    }

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(amountMinor, scale)

    /**
     * Adds two amounts of the same currency. Different currencies are never summed (SA FR-014);
     * differing scales of the same currency are widened exactly. Throws [ArithmeticException] on
     * overflow.
     */
    operator fun plus(other: Money): Money {
        if (currencyCode != other.currencyCode) throw CurrencyMismatchException(currencyCode, other.currencyCode)
        if (scale == other.scale) return copy(amountMinor = Math.addExact(amountMinor, other.amountMinor))
        val target = maxOf(scale, other.scale)
        return Money(Math.addExact(rescaled(target), other.rescaled(target)), currencyCode, target)
    }

    private fun rescaled(target: Int): Long {
        var value = amountMinor
        repeat(target - scale) { value = Math.multiplyExact(value, 10L) }
        return value
    }

    companion object {
        const val MAX_SCALE = 4
        private val CURRENCY_CODE = Regex("^[A-Z]{3}$")

        fun zero(currency: CurrencyInfo): Money = Money(0, currency.code, currency.storageScale)

        /**
         * Converts an exact decimal into minor units of [currency]. Throws [ArithmeticException] when
         * [amount] has more decimals than the storage scale or does not fit in a Long.
         */
        fun of(amount: BigDecimal, currency: CurrencyInfo): Money {
            val minor = amount.setScale(currency.storageScale).unscaledValue().longValueExact()
            return Money(minor, currency.code, currency.storageScale)
        }
    }
}

class CurrencyMismatchException(first: String, second: String) :
    IllegalArgumentException("Cannot combine $first with $second without an exchange rate")

/** Sums amounts per currency; the result is ordered by currency code. Never mixes currencies. */
fun Iterable<Money>.totalsByCurrency(): List<Money> =
    groupBy { it.currencyCode }
        .toSortedMap()
        .map { (_, amounts) -> amounts.reduce(Money::plus) }
