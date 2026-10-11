package com.howardaistudio.subscriptionhub.domain.validation

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubscriptionDraftValidatorTest {
    private val valid = SubscriptionDraft(
        name = "  ChatGPT Plus ",
        category = Category.AI,
        amountInput = "20.00",
        currencyCode = "usd",
        cycle = BillingCycle.MONTHLY,
        nextBillingDate = date("2026-10-15"),
        note = "  ",
    )

    // AT-01
    @Test
    fun `valid draft is normalised`() {
        val result = assertIs<DraftValidation.Valid>(SubscriptionDraftValidator.validate(valid))
        assertEquals("ChatGPT Plus", result.name)
        assertEquals(Money(2000, "USD", 2), result.price)
        assertEquals(date("2026-10-15"), result.schedule.anchorDate)
        assertEquals(null, result.note)
    }

    // AT-02
    @Test
    fun `reports every invalid field`() {
        val draft = valid.copy(name = " ", amountInput = "0", cycle = null, nextBillingDate = null)
        val result = assertIs<DraftValidation.Invalid>(SubscriptionDraftValidator.validate(draft))
        assertEquals(
            mapOf(
                DraftField.NAME to DraftError.REQUIRED,
                DraftField.AMOUNT to DraftError.NOT_POSITIVE,
                DraftField.CYCLE to DraftError.REQUIRED,
                DraftField.NEXT_BILLING_DATE to DraftError.REQUIRED,
            ),
            result.errors,
        )
    }

    @Test
    fun `unsupported currency`() {
        val result = assertIs<DraftValidation.Invalid>(SubscriptionDraftValidator.validate(valid.copy(currencyCode = "XYZ")))
        assertEquals(mapOf(DraftField.CURRENCY to DraftError.UNSUPPORTED_CURRENCY), result.errors)
    }

    @Test
    fun `length limits count code points`() {
        val emoji = "😀" // one code point, two UTF-16 chars
        assertIs<DraftValidation.Valid>(SubscriptionDraftValidator.validate(valid.copy(name = emoji.repeat(80))))
        val tooLong = assertIs<DraftValidation.Invalid>(SubscriptionDraftValidator.validate(valid.copy(name = "a".repeat(81))))
        assertEquals(DraftError.TOO_LONG, tooLong.errors[DraftField.NAME])

        assertIs<DraftValidation.Valid>(SubscriptionDraftValidator.validate(valid.copy(note = "字".repeat(500))))
        val longNote = assertIs<DraftValidation.Invalid>(SubscriptionDraftValidator.validate(valid.copy(note = "字".repeat(501))))
        assertEquals(DraftError.TOO_LONG, longNote.errors[DraftField.NOTE])
    }

    @Test
    fun `month end intent is applied to the schedule`() {
        val draft = valid.copy(nextBillingDate = date("2027-04-30"), monthEnd = true)
        val result = assertIs<DraftValidation.Valid>(SubscriptionDraftValidator.validate(draft))
        assertEquals(31, result.schedule.billingDay)
    }
}
