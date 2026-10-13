package com.howardaistudio.subscriptionhub.data

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs the persistence rules against the device's own SQLite. Robolectric uses a modern SQLite,
 * so only this test proves the SQL also works on minSdk 26 (SQLite 3.18, no UPSERT syntax).
 */
@RunWith(AndroidJUnit4::class)
class RoomOnDeviceTest {
    private object Time : TimeSource {
        override fun now(): Instant = Instant.parse("2026-10-10T02:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("Asia/Taipei")
    }

    private lateinit var db: SubscriptionHubDatabase
    private lateinit var subscriptions: RoomSubscriptionRepository
    private lateinit var logs: RoomReminderLogRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SubscriptionHubDatabase::class.java).build()
        subscriptions = RoomSubscriptionRepository(db, Time)
        logs = RoomReminderLogRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun sub(id: String, minor: Long = 2000) = Subscription(
        id = id,
        name = "Service $id",
        category = Category.AI,
        status = SubscriptionStatus.ACTIVE,
        price = Money(minor, "USD", 2),
        schedule = BillingSchedule(BillingCycle.MONTHLY, LocalDate.of(2027, 1, 31)),
        note = null,
        reminderEnabled = true,
        createdAt = Time.now(),
        updatedAt = Time.now(),
    )

    @Test
    fun persistenceRulesHoldOnPlatformSqlite() = runBlocking {
        db.openHelper.readableDatabase.query("SELECT sqlite_version()").use { cursor ->
            cursor.moveToFirst()
            Log.i("RoomOnDeviceTest", "SQLite ${cursor.getString(0)}")
        }

        // @Upsert: insert, then update in place.
        subscriptions.upsert(sub("a"))
        subscriptions.upsert(sub("b"))
        val key = ReminderKey("a", LocalDate.of(2027, 1, 31), 3)
        logs.record(key, ReminderDeliveryStatus.SKIPPED_PERMISSION, Time.now())
        subscriptions.upsert(sub("a", minor = 2500))
        assertEquals(Money(2500, "USD", 2), subscriptions.get("a")?.price)

        // Insert-or-ignore + guarded update instead of SQL UPSERT (dev plan G3).
        assertEquals(emptySet<ReminderKey>(), logs.sentKeys(LocalDate.of(2026, 10, 10)))
        logs.record(key, ReminderDeliveryStatus.SENT, Time.now())
        logs.record(key, ReminderDeliveryStatus.ERROR, Time.now())
        assertEquals(setOf(key), logs.sentKeys(LocalDate.of(2026, 10, 10)))

        // Foreign key cascade.
        subscriptions.delete("a")
        assertEquals(emptySet<ReminderKey>(), logs.sentKeys(LocalDate.MIN))
        assertEquals(listOf("b"), subscriptions.getAll().map { it.id })

        // Month-end schedule survives a round trip.
        assertEquals(LocalDate.of(2027, 2, 28), subscriptions.get("b")!!.schedule.occurrence(1))
        subscriptions.deleteAll()
        assertTrue(subscriptions.getAll().isEmpty())
    }
}
