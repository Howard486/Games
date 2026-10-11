package com.howardaistudio.subscriptionhub.domain.money

import java.util.Locale

/**
 * A supported ISO 4217 currency.
 *
 * [storageScale] is the number of decimal places encoded in `amount_minor`. It is pinned in this
 * table on purpose (SA BR-09, R12): ICU / `Currency.getDefaultFractionDigits` may change between OS
 * releases and must never decide how persisted amounts are interpreted. Never change the storage
 * scale of an existing code; add a migration instead.
 *
 * [displayDigits] is the default number of decimals shown in the UI (e.g. TWD is stored with
 * scale 2 but normally shown without decimals).
 */
data class CurrencyInfo(
    val code: String,
    val storageScale: Int,
    val displayDigits: Int,
)

object CurrencyCatalog {
    const val DEFAULT_CODE = "TWD"

    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("TWD", storageScale = 2, displayDigits = 0),
        CurrencyInfo("USD", storageScale = 2, displayDigits = 2),
        CurrencyInfo("JPY", storageScale = 0, displayDigits = 0),
        CurrencyInfo("EUR", storageScale = 2, displayDigits = 2),
        CurrencyInfo("GBP", storageScale = 2, displayDigits = 2),
        CurrencyInfo("HKD", storageScale = 2, displayDigits = 2),
        CurrencyInfo("CNY", storageScale = 2, displayDigits = 2),
        CurrencyInfo("KRW", storageScale = 0, displayDigits = 0),
        CurrencyInfo("SGD", storageScale = 2, displayDigits = 2),
        CurrencyInfo("AUD", storageScale = 2, displayDigits = 2),
        CurrencyInfo("CAD", storageScale = 2, displayDigits = 2),
    )

    private val byCode: Map<String, CurrencyInfo> = all.associateBy { it.code }

    fun find(code: String): CurrencyInfo? = byCode[code.trim().uppercase(Locale.ROOT)]

    fun require(code: String): CurrencyInfo =
        find(code) ?: throw IllegalArgumentException("Unsupported currency: $code")
}
