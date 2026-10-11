package com.howardaistudio.subscriptionhub.domain.money

import com.howardaistudio.subscriptionhub.domain.money
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    private val usd = CurrencyCatalog.require("USD")
    private val twd = CurrencyCatalog.require("TWD")
    private val jpy = CurrencyCatalog.require("JPY")

    // AT-04: USD 9.99, JPY 1000, TWD 199 -> 999/2, 1000/0, 19900/2
    @ParameterizedTest
    @CsvSource("9.99,USD,999,2", "1000,JPY,1000,0", "199,TWD,19900,2")
    fun `stores amounts as minor units with explicit scale`(input: String, code: String, minor: Long, scale: Int) {
        val result = MoneyParser.parse(input, CurrencyCatalog.require(code))
        assertEquals(MoneyParseResult.Success(Money(minor, code, scale)), result)
    }

    @ParameterizedTest
    @CsvSource(
        "'',EMPTY",
        "'   ',EMPTY",
        "abc,INVALID_FORMAT",
        "-5,INVALID_FORMAT",
        "1e3,INVALID_FORMAT",
        "'9,99',INVALID_FORMAT",
        "9.999,TOO_MANY_DECIMALS",
        "0,NOT_POSITIVE",
        "0.00,NOT_POSITIVE",
        "99999999999999,TOO_LARGE",
    )
    fun `rejects invalid USD input`(input: String, error: AmountError) {
        assertEquals(MoneyParseResult.Failure(error), MoneyParser.parse(input, usd))
    }

    @Test
    fun `JPY rejects decimals but accepts trailing zeros`() {
        assertEquals(MoneyParseResult.Failure(AmountError.TOO_MANY_DECIMALS), MoneyParser.parse("100.5", jpy))
        assertEquals(MoneyParseResult.Success(Money(100, "JPY", 0)), MoneyParser.parse("100.00", jpy))
    }

    @Test
    fun `accepts grouping commas and full-width digits`() {
        assertEquals(MoneyParseResult.Success(Money(123456700, "TWD", 2)), MoneyParser.parse("1,234,567", twd))
        assertEquals(MoneyParseResult.Success(Money(19950, "TWD", 2)), MoneyParser.parse("１９９．５", twd))
    }

    @Test
    fun `adds same currency and widens scale exactly`() {
        assertEquals(Money(3000, "USD", 2), Money(1000, "USD", 2) + Money(2000, "USD", 2))
        assertEquals(Money(1050, "USD", 2), Money(10, "USD", 0) + Money(50, "USD", 2))
    }

    // AT-14: TWD 299 + USD 20 must never become a single number.
    @Test
    fun `refuses to add different currencies`() {
        assertFailsWith<CurrencyMismatchException> { money("299", "TWD") + money("20", "USD") }
    }

    @Test
    fun `addition overflow throws instead of wrapping`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "USD", 2) + Money(1, "USD", 2) }
    }

    @Test
    fun `totals are grouped per currency`() {
        val totals = listOf(money("299", "TWD"), money("20", "USD"), money("100", "TWD")).totalsByCurrency()
        assertEquals(listOf(money("399", "TWD"), money("20", "USD")), totals)
    }

    @Test
    fun `formats with currency display digits without hiding fractions`() {
        val tw = Locale.TAIWAN
        assertEquals("USD 20.00", MoneyFormatter.format(money("20", "USD"), tw))
        assertEquals("TWD 199", MoneyFormatter.format(money("199", "TWD"), tw))
        assertEquals("TWD 199.50", MoneyFormatter.format(money("199.5", "TWD"), tw))
        assertEquals("JPY 1,000", MoneyFormatter.format(money("1000", "JPY"), tw))
        assertEquals("USD 43.33", MoneyFormatter.formatEstimate("USD", BigDecimal("43.333333"), tw))
    }
}
