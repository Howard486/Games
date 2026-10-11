package com.howardaistudio.subscriptionhub.domain.query

import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import java.time.LocalDate
import java.util.Locale

enum class SortOrder { NEXT_BILLING_DATE, AMOUNT, NAME }

/**
 * List filter (SA FR-007). An empty [statuses] means "everything except ARCHIVED", which is the
 * default list view (SA §5.1).
 */
data class SubscriptionFilter(
    val query: String = "",
    val statuses: Set<SubscriptionStatus> = emptySet(),
    val categories: Set<Category> = emptySet(),
    val sort: SortOrder = SortOrder.NEXT_BILLING_DATE,
)

object SubscriptionQuery {

    /**
     * Filters and sorts [subscriptions].
     *
     * - NEXT_BILLING_DATE: soonest first; subscriptions without a projected charge go last.
     * - AMOUNT: grouped by currency code, highest amount first (currencies are never compared).
     * - NAME: by [nameOrder], e.g. a `java.text.Collator` for the user's locale.
     */
    fun apply(
        subscriptions: List<Subscription>,
        filter: SubscriptionFilter,
        today: LocalDate,
        nameOrder: Comparator<String>,
    ): List<Subscription> {
        val needle = DuplicateNames.normalize(filter.query)
        val byName = compareBy(nameOrder) { sub: Subscription -> sub.name }
        val comparator: Comparator<Subscription> = when (filter.sort) {
            SortOrder.NEXT_BILLING_DATE ->
                compareBy(nullsLast(Comparator<LocalDate> { a, b -> a.compareTo(b) })) { sub: Subscription ->
                    sub.nextBillingDate(today)
                }.then(byName)
            SortOrder.AMOUNT ->
                compareBy<Subscription> { it.price.currencyCode }
                    .thenByDescending { it.price.toBigDecimal() }
                    .then(byName)
            SortOrder.NAME -> byName
        }
        return subscriptions
            .asSequence()
            .filter { sub ->
                if (filter.statuses.isEmpty()) sub.status != SubscriptionStatus.ARCHIVED else sub.status in filter.statuses
            }
            .filter { filter.categories.isEmpty() || it.category in filter.categories }
            .filter { needle.isEmpty() || DuplicateNames.normalize(it.name).contains(needle) }
            .sortedWith(comparator.thenBy { it.id })
            .toList()
    }
}

/**
 * Same-name detection (SA FR-008): duplicates are allowed and only surfaced as a hint.
 * Names are compared trimmed, case-insensitively and with collapsed whitespace.
 */
object DuplicateNames {
    private val WHITESPACE = Regex("\\s+")

    fun normalize(name: String): String = name.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

    /** Other subscriptions sharing [name], excluding [excludeId] (the one being edited). */
    fun matches(name: String, subscriptions: List<Subscription>, excludeId: String? = null): List<Subscription> {
        val key = normalize(name)
        if (key.isEmpty()) return emptyList()
        return subscriptions.filter { it.id != excludeId && normalize(it.name) == key }
    }

    /** IDs of every subscription whose name appears more than once. */
    fun duplicateIds(subscriptions: List<Subscription>): Set<String> =
        subscriptions.groupBy { normalize(it.name) }
            .values
            .filter { it.size > 1 }
            .flatMapTo(HashSet()) { group -> group.map { it.id } }
}
