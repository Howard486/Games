package com.howardaistudio.subscriptionhub.domain.export

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.subscription
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvExportTest {

    // AT-27
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        value = [
            "plain|plain",
            "a,b|\"a,b\"",
            "say \"hi\"|\"say \"\"hi\"\"\"",
            "=1+1|'=1+1",
            "+cmd|'+cmd",
            "-2|'-2",
            "@SUM(A1)|'@SUM(A1)",
            "＝1+1|'＝1+1",
            "=HYPERLINK(\"x\",\"y\")|\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"",
        ],
    )
    fun `escapes per RFC 4180 and neutralises formulas`(input: String, expected: String) {
        assertEquals(expected, CsvEncoder.escape(input))
    }

    @Test
    fun `escapes line breaks and leading control characters`() {
        assertEquals("\"line1\nline2\"", CsvEncoder.escape("line1\nline2"))
        assertEquals("'\tx", CsvEncoder.escape("\tx"))
        assertEquals("\"'\rx\"", CsvEncoder.escape("\rx"))
        assertEquals("", CsvEncoder.escape(""))
    }

    @Test
    fun `writes BOM, header and exact minor units`() {
        val subs = listOf(
            subscription(id = "id-1", name = "Netflix, \"Premium\"", amount = "9.99", currency = "USD", anchor = "2026-10-15", note = "=1+1"),
            subscription(
                id = "id-2",
                name = "Cloud",
                amount = "1000",
                currency = "JPY",
                cycle = BillingCycle.YEARLY,
                anchor = "2026-02-28",
                billingDay = 29,
                status = SubscriptionStatus.CANCELLED,
            ),
        )
        val out = StringBuilder()
        SubscriptionCsvExporter.write(subs, date("2026-10-10"), out)
        val text = out.toString()

        assertTrue(text.startsWith("﻿"))
        val lines = text.removePrefix("﻿").split("\r\n")
        assertEquals(
            "id,name,category,status,amount_minor,currency_scale,currency,cycle,anchor_date,next_billing_date,note,created_at,updated_at,billing_day",
            lines[0],
        )
        assertEquals(
            "id-1,\"Netflix, \"\"Premium\"\"\",AI,ACTIVE,999,2,USD,MONTHLY,2026-10-15,2026-10-15,'=1+1,2026-10-10T00:00:00Z,2026-10-10T00:00:00Z,15",
            lines[1],
        )
        assertEquals(
            "id-2,Cloud,AI,CANCELLED,1000,0,JPY,YEARLY,2026-02-28,,,2026-10-10T00:00:00Z,2026-10-10T00:00:00Z,29",
            lines[2],
        )
        assertEquals("", lines[3])
        assertEquals(4, lines.size)
    }
}
