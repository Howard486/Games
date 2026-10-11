package com.howardaistudio.subscriptionhub.domain.validation

import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.money.AmountError
import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.money.MoneyParseResult
import com.howardaistudio.subscriptionhub.domain.money.MoneyParser
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import java.time.LocalDate

/** Raw form input for creating or editing a subscription (SA SCR-04). */
data class SubscriptionDraft(
    val name: String,
    val category: Category,
    val amountInput: String,
    val currencyCode: String,
    val cycle: BillingCycle?,
    val nextBillingDate: LocalDate?,
    /** User confirmed "last day of every month"; only honoured for month-end candidates. */
    val monthEnd: Boolean = false,
    val note: String = "",
)

enum class DraftField { NAME, AMOUNT, CURRENCY, CYCLE, NEXT_BILLING_DATE, NOTE }

enum class DraftError {
    REQUIRED,
    TOO_LONG,
    INVALID_FORMAT,
    TOO_MANY_DECIMALS,
    NOT_POSITIVE,
    TOO_LARGE,
    UNSUPPORTED_CURRENCY,
}

sealed interface DraftValidation {
    data class Valid(
        val name: String,
        val category: Category,
        val price: Money,
        val schedule: BillingSchedule,
        val note: String?,
    ) : DraftValidation

    data class Invalid(val errors: Map<DraftField, DraftError>) : DraftValidation
}

/** Field validation for SA FR-001, FR-002, FR-004. Lengths count Unicode code points. */
object SubscriptionDraftValidator {
    const val NAME_MAX_LENGTH = 80
    const val NOTE_MAX_LENGTH = 500

    fun validate(draft: SubscriptionDraft): DraftValidation {
        val errors = LinkedHashMap<DraftField, DraftError>()

        val name = draft.name.trim()
        when {
            name.isEmpty() -> errors[DraftField.NAME] = DraftError.REQUIRED
            name.codePointLength() > NAME_MAX_LENGTH -> errors[DraftField.NAME] = DraftError.TOO_LONG
        }

        val currency = CurrencyCatalog.find(draft.currencyCode)
        var price: Money? = null
        if (currency == null) {
            errors[DraftField.CURRENCY] = DraftError.UNSUPPORTED_CURRENCY
        } else {
            when (val parsed = MoneyParser.parse(draft.amountInput, currency)) {
                is MoneyParseResult.Success -> price = parsed.money
                is MoneyParseResult.Failure -> errors[DraftField.AMOUNT] = parsed.error.toDraftError()
            }
        }

        if (draft.cycle == null) errors[DraftField.CYCLE] = DraftError.REQUIRED
        if (draft.nextBillingDate == null) errors[DraftField.NEXT_BILLING_DATE] = DraftError.REQUIRED

        val note = draft.note.trim()
        if (note.codePointLength() > NOTE_MAX_LENGTH) errors[DraftField.NOTE] = DraftError.TOO_LONG

        if (errors.isNotEmpty()) return DraftValidation.Invalid(errors)
        return DraftValidation.Valid(
            name = name,
            category = draft.category,
            price = checkNotNull(price),
            schedule = BillingSchedule.fromUserInput(checkNotNull(draft.cycle), checkNotNull(draft.nextBillingDate), draft.monthEnd),
            note = note.ifEmpty { null },
        )
    }

    private fun String.codePointLength(): Int = codePointCount(0, length)

    private fun AmountError.toDraftError(): DraftError = when (this) {
        AmountError.EMPTY -> DraftError.REQUIRED
        AmountError.INVALID_FORMAT -> DraftError.INVALID_FORMAT
        AmountError.TOO_MANY_DECIMALS -> DraftError.TOO_MANY_DECIMALS
        AmountError.NOT_POSITIVE -> DraftError.NOT_POSITIVE
        AmountError.TOO_LARGE -> DraftError.TOO_LARGE
    }
}
