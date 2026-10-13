package com.howardaistudio.subscriptionhub.domain.usecase

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import com.howardaistudio.subscriptionhub.domain.subscription
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import com.howardaistudio.subscriptionhub.domain.validation.DraftField
import com.howardaistudio.subscriptionhub.domain.validation.SubscriptionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemorySubscriptionRepository(initial: List<Subscription> = emptyList()) : SubscriptionRepository {
    val rows = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Subscription>> = rows.map { it.values.toList() }
    override fun observe(id: String): Flow<Subscription?> = rows.map { it[id] }
    override suspend fun get(id: String): Subscription? = rows.value[id]
    override suspend fun getAll(): List<Subscription> = rows.value.values.toList()
    override suspend fun upsert(subscription: Subscription) {
        rows.value = rows.value + (subscription.id to subscription)
    }
    override suspend fun delete(id: String) {
        rows.value = rows.value - id
    }
    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
    override suspend fun refreshDerivedState(today: LocalDate, now: Instant) = Unit
}

class ManageSubscriptionsTest {
    private val now = Instant.parse("2026-10-10T02:00:00Z")
    private val time = object : TimeSource {
        override fun now(): Instant = now
        override fun zone(): ZoneId = ZoneId.of("Asia/Taipei")
    }

    private val draft = SubscriptionDraft(
        name = "ChatGPT Plus",
        category = Category.AI,
        amountInput = "20",
        currencyCode = "USD",
        cycle = BillingCycle.MONTHLY,
        nextBillingDate = date("2026-10-15"),
    )

    private fun manage(repo: InMemorySubscriptionRepository) = ManageSubscriptions(repo, time) { "new-id" }

    // AT-01
    @Test
    fun `creates an active subscription`() = runBlocking {
        val repo = InMemorySubscriptionRepository()
        assertEquals(SaveResult.Saved("new-id"), manage(repo).save(draft, existingId = null))

        val saved = repo.get("new-id")!!
        assertEquals(SubscriptionStatus.ACTIVE, saved.status)
        assertEquals(Money(2000, "USD", 2), saved.price)
        assertEquals(BillingSchedule(BillingCycle.MONTHLY, date("2026-10-15")), saved.schedule)
        assertEquals(now, saved.createdAt)
        assertTrue(saved.reminderEnabled)
    }

    @Test
    fun `trial flag creates a TRIAL subscription`() = runBlocking {
        val repo = InMemorySubscriptionRepository()
        manage(repo).save(draft.copy(trial = true, reminderEnabled = false), existingId = null)
        val saved = repo.get("new-id")!!
        assertEquals(SubscriptionStatus.TRIAL, saved.status)
        assertFalse(saved.reminderEnabled)
    }

    // AT-02
    @Test
    fun `invalid draft is not saved`() = runBlocking {
        val repo = InMemorySubscriptionRepository()
        val result = manage(repo).save(draft.copy(name = "", amountInput = "0"), existingId = null)
        assertEquals(
            SaveResult.Invalid(mapOf(DraftField.NAME to DraftError.REQUIRED, DraftField.AMOUNT to DraftError.NOT_POSITIVE)),
            result,
        )
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `editing a missing subscription reports not found`() = runBlocking {
        assertEquals(SaveResult.NotFound, manage(InMemorySubscriptionRepository()).save(draft, existingId = "gone"))
    }

    // FR-011: editing other fields must not move a month-end anchor.
    @Test
    fun `edit keeps the original schedule when the date is unchanged`() = runBlocking {
        val original = subscription(id = "s", anchor = "2026-01-30", status = SubscriptionStatus.PAUSED)
        val repo = InMemorySubscriptionRepository(listOf(original))
        val manage = manage(repo)

        val form = manage.draftFor(original, time.today())
        assertEquals(date("2026-10-30"), form.nextBillingDate)
        assertEquals("20", form.amountInput)

        manage.save(form.copy(name = "Renamed", amountInput = "25.5"), existingId = "s")

        val saved = repo.get("s")!!
        assertEquals("Renamed", saved.name)
        assertEquals(Money(2550, "USD", 2), saved.price)
        assertEquals(original.schedule, saved.schedule)
        assertEquals(SubscriptionStatus.PAUSED, saved.status)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals(now, saved.updatedAt)
    }

    @Test
    fun `month-end schedule round-trips through the form`() = runBlocking {
        val original = subscription(id = "s", anchor = "2027-01-31")
        val repo = InMemorySubscriptionRepository(listOf(original))
        val manage = manage(repo)
        val atFeb = manage.draftFor(original, date("2027-02-10"))
        assertEquals(date("2027-02-28"), atFeb.nextBillingDate)
        assertTrue(atFeb.monthEnd)
    }

    // SA §6.1: a changed date becomes the new anchor.
    @Test
    fun `edit with a new date re-anchors the schedule`() = runBlocking {
        val original = subscription(id = "s", anchor = "2026-10-15")
        val repo = InMemorySubscriptionRepository(listOf(original))
        manage(repo).save(manage(repo).draftFor(original, time.today()).copy(nextBillingDate = date("2026-10-20")), existingId = "s")
        assertEquals(BillingSchedule(BillingCycle.MONTHLY, date("2026-10-20")), repo.get("s")!!.schedule)
    }

    // AT-06
    @Test
    fun `stop changes status and rejects active statuses`() = runBlocking {
        val repo = InMemorySubscriptionRepository(listOf(subscription(id = "s")))
        val manage = manage(repo)

        assertTrue(manage.stop("s", SubscriptionStatus.CANCELLED))
        assertEquals(SubscriptionStatus.CANCELLED, repo.get("s")!!.status)
        assertFalse(manage.stop("missing", SubscriptionStatus.PAUSED))
        assertFailsWith<IllegalArgumentException> { manage.stop("s", SubscriptionStatus.ACTIVE) }
    }

    @Test
    fun `resume keeps the schedule when the confirmed date matches it`() = runBlocking {
        val original = subscription(id = "s", anchor = "2026-01-31", status = SubscriptionStatus.PAUSED)
        val repo = InMemorySubscriptionRepository(listOf(original))

        assertTrue(manage(repo).resume("s", date("2026-10-31")))

        val resumed = repo.get("s")!!
        assertEquals(SubscriptionStatus.ACTIVE, resumed.status)
        assertEquals(original.schedule, resumed.schedule)
    }

    @Test
    fun `resume on a clamped month-end date keeps the 31st anchor`() = runBlocking {
        val original = subscription(id = "s", anchor = "2026-01-31", status = SubscriptionStatus.CANCELLED)
        val repo = InMemorySubscriptionRepository(listOf(original))

        manage(repo).resume("s", date("2027-02-28"))

        val schedule = repo.get("s")!!.schedule
        assertEquals(original.schedule, schedule)
        assertEquals(date("2027-03-31"), schedule.firstOnOrAfter(date("2027-03-01")))
    }

    @Test
    fun `resume with an off-schedule date re-anchors`() = runBlocking {
        val original = subscription(id = "s", anchor = "2026-01-31", status = SubscriptionStatus.PAUSED)
        val repo = InMemorySubscriptionRepository(listOf(original))

        manage(repo).resume("s", date("2026-11-05"))

        assertEquals(BillingSchedule(BillingCycle.MONTHLY, date("2026-11-05")), repo.get("s")!!.schedule)
    }

    @Test
    fun `delete removes the row`() = runBlocking {
        val repo = InMemorySubscriptionRepository(listOf(subscription(id = "s")))
        manage(repo).delete("s")
        assertNull(repo.get("s"))
    }

    @Test
    fun `saved result type`() = runBlocking {
        assertIs<SaveResult.Saved>(manage(InMemorySubscriptionRepository()).save(draft, null))
        Unit
    }
}
