package com.howardaistudio.subscriptionhub.feature.subscription

import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.schedule.BillingSchedule
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

object FixedTime : TimeSource {
    override fun now(): Instant = Instant.parse("2026-10-10T02:00:00Z")
    override fun zone(): ZoneId = ZoneId.of("Asia/Taipei")
}

class InMemorySubscriptionRepository(initial: List<Subscription> = emptyList()) : SubscriptionRepository {
    val rows = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Subscription>> = rows.map { it.values.toList() }
    override fun observe(id: String): Flow<Subscription?> = rows.map { it[id] }
    override suspend fun get(id: String): Subscription? = rows.value[id]
    override suspend fun getAll(): List<Subscription> = rows.value.values.toList()
    override suspend fun upsert(subscription: Subscription) = rows.update { it + (subscription.id to subscription) }
    override suspend fun delete(id: String) = rows.update { it - id }
    override suspend fun deleteAll() = rows.update { emptyMap() }
    override suspend fun refreshDerivedState(today: LocalDate, now: Instant) = Unit
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

fun testSubscription(
    id: String,
    name: String = "ChatGPT Plus",
    minor: Long = 2000,
    currency: String = "USD",
    anchor: String = "2026-10-15",
    status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    category: Category = Category.AI,
) = Subscription(
    id = id,
    name = name,
    category = category,
    status = status,
    price = Money(minor, currency, if (currency == "JPY") 0 else 2),
    schedule = BillingSchedule(BillingCycle.MONTHLY, LocalDate.parse(anchor)),
    note = null,
    reminderEnabled = true,
    createdAt = FixedTime.now(),
    updatedAt = FixedTime.now(),
)
