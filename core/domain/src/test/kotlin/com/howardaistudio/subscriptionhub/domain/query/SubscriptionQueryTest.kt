package com.howardaistudio.subscriptionhub.domain.query

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.subscription
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SubscriptionQueryTest {
    private val today = date("2026-10-10")
    private val nameOrder: Comparator<String> = Comparator { a, b -> a.compareTo(b) }

    private val subs = listOf(
        subscription(id = "1", name = "Netflix", amount = "390", currency = "TWD", anchor = "2026-10-25", category = Category.VIDEO),
        subscription(id = "2", name = "ChatGPT Plus", amount = "20", currency = "USD", anchor = "2026-10-15"),
        subscription(id = "3", name = "Claude Pro", amount = "17", currency = "USD", anchor = "2026-10-12"),
        subscription(id = "4", name = "iCloud", amount = "30", currency = "TWD", anchor = "2026-10-20", status = SubscriptionStatus.PAUSED, category = Category.CLOUD_STORAGE),
        subscription(id = "5", name = "Old Service", amount = "100", currency = "TWD", anchor = "2026-10-11", status = SubscriptionStatus.ARCHIVED),
    )

    private fun ids(filter: SubscriptionFilter) = SubscriptionQuery.apply(subs, filter, today, nameOrder).map { it.id }

    // AT-07
    @Test
    fun `default hides archived and sorts by next date with inactive last`() {
        assertEquals(listOf("3", "2", "1", "4"), ids(SubscriptionFilter()))
    }

    @Test
    fun `sort by amount groups by currency`() {
        assertEquals(listOf("1", "4", "2", "3"), ids(SubscriptionFilter(sort = SortOrder.AMOUNT)))
    }

    @Test
    fun `sort by name`() {
        assertEquals(listOf("2", "3", "1", "4"), ids(SubscriptionFilter(sort = SortOrder.NAME)))
    }

    @Test
    fun `search is case insensitive and trims`() {
        assertEquals(listOf("2"), ids(SubscriptionFilter(query = "  chatgpt ")))
        assertEquals(emptyList(), ids(SubscriptionFilter(query = "spotify")))
    }

    @Test
    fun `filters by status and category`() {
        assertEquals(listOf("5"), ids(SubscriptionFilter(statuses = setOf(SubscriptionStatus.ARCHIVED))))
        assertEquals(listOf("4"), ids(SubscriptionFilter(categories = setOf(Category.CLOUD_STORAGE))))
    }

    // AT-08: duplicates are allowed and flagged.
    @Test
    fun `duplicate names are detected but not blocked`() {
        val list = subs + subscription(id = "6", name = "  netflix ", amount = "270", currency = "TWD")
        assertEquals(setOf("1", "6"), DuplicateNames.duplicateIds(list))
        assertEquals(listOf("6"), DuplicateNames.matches("NETFLIX", list, excludeId = "1").map { it.id })
        assertEquals(emptyList(), DuplicateNames.matches(" ", list))
    }
}
