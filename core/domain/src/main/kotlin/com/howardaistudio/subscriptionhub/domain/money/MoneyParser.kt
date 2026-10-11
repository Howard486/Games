package com.howardaistudio.subscriptionhub.domain.money

import java.math.BigDecimal

enum class AmountError { EMPTY, INVALID_FORMAT, TOO_MANY_DECIMALS, NOT_POSITIVE, TOO_LARGE }

sealed interface MoneyParseResult {
    data class Success(val money: Money) : MoneyParseResult
    data class Failure(val error: AmountError) : MoneyParseResult
}

/**
 * Parses user-entered amounts such as `9.99`, `1,000` or `１９９` (full-width digits from CJK IMEs).
 *
 * A comma is accepted only as a thousands separator in proper 3-digit groups, so a European style
 * `9,99` is rejected instead of silently becoming 999.
 */
object MoneyParser {
    /** Upper bound per subscription amount, in minor units. Keeps sums far from Long overflow. */
    const val MAX_AMOUNT_MINOR: Long = 999_999_999_999L

    private val PLAIN = Regex("""^\d+(\.\d+)?$""")
    private val GROUPED = Regex("""^\d{1,3}(,\d{3})+(\.\d+)?$""")

    fun parse(input: String, currency: CurrencyInfo): MoneyParseResult {
        val text = normalizeWidth(input).trim()
        if (text.isEmpty()) return MoneyParseResult.Failure(AmountError.EMPTY)
        if (!PLAIN.matches(text) && !GROUPED.matches(text)) {
            return MoneyParseResult.Failure(AmountError.INVALID_FORMAT)
        }
        var value = BigDecimal(text.replace(",", ""))
        if (value.scale() > currency.storageScale) {
            value = value.stripTrailingZeros()
            if (value.scale() > currency.storageScale) {
                return MoneyParseResult.Failure(AmountError.TOO_MANY_DECIMALS)
            }
        }
        if (value.signum() <= 0) return MoneyParseResult.Failure(AmountError.NOT_POSITIVE)
        val minor = value.movePointRight(currency.storageScale)
        if (minor > BigDecimal.valueOf(MAX_AMOUNT_MINOR)) return MoneyParseResult.Failure(AmountError.TOO_LARGE)
        return MoneyParseResult.Success(Money.of(value, currency))
    }

    private fun normalizeWidth(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    in '０'..'９' -> '0' + (ch - '０')
                    '．' -> '.'
                    '，' -> ','
                    '　' -> ' '
                    else -> ch
                },
            )
        }
    }
}
