package com.howardaistudio.subscriptionhub.domain.export

import com.howardaistudio.subscriptionhub.domain.model.Subscription
import java.time.LocalDate

/**
 * Writes subscriptions as UTF-8 CSV (caller supplies a UTF-8 [Appendable]); starts with a BOM so
 * spreadsheet apps detect the encoding (SA FR-027).
 *
 * Columns follow SA §5.4. `amount_minor` + `currency_scale` are the exact values (USD 999 at scale 2
 * = USD 9.99). `billing_day` is appended for a lossless future import (dev plan G2).
 * `next_billing_date` is derived for [today] and left empty when no charge is projected.
 */
object SubscriptionCsvExporter {
    val HEADER: List<String> = listOf(
        "id", "name", "category", "status", "amount_minor", "currency_scale", "currency", "cycle",
        "anchor_date", "next_billing_date", "note", "created_at", "updated_at", "billing_day",
    )

    fun write(subscriptions: List<Subscription>, today: LocalDate, out: Appendable) {
        out.append(CsvEncoder.BOM)
        CsvEncoder.writeRow(HEADER, out)
        for (sub in subscriptions) {
            CsvEncoder.writeRow(
                listOf(
                    sub.id,
                    sub.name,
                    sub.category.name,
                    sub.status.name,
                    sub.price.amountMinor.toString(),
                    sub.price.scale.toString(),
                    sub.price.currencyCode,
                    sub.schedule.cycle.name,
                    sub.schedule.anchorDate.toString(),
                    sub.nextBillingDate(today)?.toString().orEmpty(),
                    sub.note.orEmpty(),
                    sub.createdAt.toString(),
                    sub.updatedAt.toString(),
                    sub.schedule.billingDay.toString(),
                ),
                out,
            )
        }
    }
}
