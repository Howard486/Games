package com.howardaistudio.subscriptionhub.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.howardaistudio.subscriptionhub.data.local.SubscriptionHubDatabase
import com.howardaistudio.subscriptionhub.data.repository.RoomReminderLogRepository
import com.howardaistudio.subscriptionhub.data.repository.RoomSubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderKey
import com.howardaistudio.subscriptionhub.domain.repository.ReminderDeliveryStatus
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomRepositoriesTest {

    private object FixedTime : TimeSource {
        override fun now(): Instant = Instant.parse("2026-10-10T01:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("Asia/Taipei")
    }

    private val created = Instant.parse("2026-10-01T00:00:00Z")
    private val today = LocalDate.of(2026, 10, 10)

    private lateinit var db: SubscriptionHubDatabase
    private lateinit var subscriptions: RoomSubscriptionRepository
    private lateinit var reminderLogs: RoomReminderLogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SubscriptionHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        subscriptions = RoomSubscriptionRepository(db, FixedTime)
        reminderLogs = RoomReminderLogRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sub(
        id: String = "sub-1",
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        price: Money = Money(2000, "USD", 2),
        schedule: BillingSchedule = BillingSchedule(BillingCycle.MONTHLY, LocalDate.of(2026, 10, 15)),
        note: String? = null,
    ) = Subscription(
        id = id,
        name = "ChatGPT Plus",
        category = Category.AI,
        status = status,
        price = price,
        schedule = schedule,
        note = note,
        reminderEnabled = true,
        createdAt = created,
        updatedAt = created,
    )

    private fun key(id: String = "sub-1", date: String = "2026-10-15", days: Int = 3) =
        ReminderKey(id, LocalDate.parse(date), days)

    @Test
    fun `round trip keeps exact amounts, scale and month-end billing day`() = runBlocking {
        val monthEnd = sub(
            id = "jpy",
            price = Money(1000, "JPY", 0),
            schedule = BillingSchedule(BillingCycle.MONTHLY, LocalDate.of(2027, 2, 28), billingDay = 31),
            note = "備註, \"quoted\"",
        )
        subscriptions.upsert(monthEnd)
        subscriptions.upsert(sub())

        assertEquals(monthEnd, subscriptions.get("jpy"))
        assertEquals(setOf("jpy", "sub-1"), subscriptions.getAll().map { it.id }.toSet())
        assertEquals("2027-02-28", db.subscriptionDao().get("jpy")?.nextBillingDate)
    }

    @Test
    fun `observeAll emits the current rows`() = runBlocking {
        subscriptions.upsert(sub())
        assertEquals(listOf("sub-1"), subscriptions.observeAll().first().map { it.id })
        assertEquals("sub-1", subscriptions.observe("sub-1").first()?.id)
        assertNull(subscriptions.observe("missing").first())
    }

    @Test
    fun `updating a subscription keeps its reminder logs`() = runBlocking {
        subscriptions.upsert(sub())
        reminderLogs.record(key(), ReminderDeliveryStatus.SENT, FixedTime.now())

        subscriptions.upsert(sub(price = Money(2500, "USD", 2)))

        assertEquals(Money(2500, "USD", 2), subscriptions.get("sub-1")?.price)
        assertEquals(setOf(key()), reminderLogs.sentKeys(today))
    }

    // SA FR-006 / FR-022: deleting cascades to reminder logs.
    @Test
    fun `deleting a subscription removes its reminder logs`() = runBlocking {
        subscriptions.upsert(sub())
        subscriptions.upsert(sub(id = "sub-2"))
        reminderLogs.record(key(), ReminderDeliveryStatus.SENT, FixedTime.now())
        reminderLogs.record(key(id = "sub-2"), ReminderDeliveryStatus.SENT, FixedTime.now())

        subscriptions.delete("sub-1")

        assertNull(subscriptions.get("sub-1"))
        assertEquals(setOf(key(id = "sub-2")), reminderLogs.sentKeys(today))
    }

    // SA FR-028 / AT-28
    @Test
    fun `deleteAll clears subscriptions and reminder logs`() = runBlocking {
        subscriptions.upsert(sub())
        reminderLogs.record(key(), ReminderDeliveryStatus.SENT, FixedTime.now())

        subscriptions.deleteAll()

        assertEquals(emptyList<Subscription>(), subscriptions.getAll())
        assertEquals(emptySet<ReminderKey>(), reminderLogs.sentKeys(LocalDate.MIN))
    }

    // Dev plan G3: SKIPPED_PERMISSION must not block a later SENT; SENT is final.
    @Test
    fun `reminder log keeps SENT final and lets other statuses be replaced`() = runBlocking {
        subscriptions.upsert(sub())
        val dao = db.reminderLogDao()

        reminderLogs.record(key(), ReminderDeliveryStatus.SKIPPED_PERMISSION, FixedTime.now())
        assertEquals(emptySet<ReminderKey>(), reminderLogs.sentKeys(today))

        reminderLogs.record(key(), ReminderDeliveryStatus.SENT, FixedTime.now())
        assertEquals(setOf(key()), reminderLogs.sentKeys(today))

        reminderLogs.record(key(), ReminderDeliveryStatus.ERROR, FixedTime.now())
        assertEquals("SENT", dao.find("sub-1", "2026-10-15", 3)?.status)
        assertEquals(setOf(key()), reminderLogs.sentKeys(today))
    }

    @Test
    fun `sent keys and pruning are bounded by renewal date`() = runBlocking {
        subscriptions.upsert(sub())
        reminderLogs.record(key(date = "2026-09-15"), ReminderDeliveryStatus.SENT, FixedTime.now())
        reminderLogs.record(key(date = "2026-10-15"), ReminderDeliveryStatus.SENT, FixedTime.now())

        assertEquals(setOf(key(date = "2026-10-15")), reminderLogs.sentKeys(today))

        reminderLogs.pruneBefore(today)
        assertEquals(setOf(key(date = "2026-10-15")), reminderLogs.sentKeys(LocalDate.MIN))
    }

    // Dev plan G1 / G5
    @Test
    fun `refresh resolves finished trials and rewrites the next billing date cache`() = runBlocking {
        val trial = sub(id = "trial", status = SubscriptionStatus.TRIAL, schedule = BillingSchedule(BillingCycle.MONTHLY, LocalDate.of(2026, 9, 20)))
        subscriptions.upsert(trial)
        subscriptions.upsert(sub())

        subscriptions.refreshDerivedState(today, FixedTime.now())

        val refreshed = subscriptions.get("trial")
        assertEquals(SubscriptionStatus.ACTIVE, refreshed?.status)
        assertEquals(FixedTime.now(), refreshed?.updatedAt)
        assertEquals("2026-10-20", db.subscriptionDao().get("trial")?.nextBillingDate)
        // Untouched rows keep their timestamps.
        assertEquals(created, subscriptions.get("sub-1")?.updatedAt)
    }
}
