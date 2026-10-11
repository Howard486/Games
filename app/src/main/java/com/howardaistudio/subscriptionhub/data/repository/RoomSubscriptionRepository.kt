package com.howardaistudio.subscriptionhub.data.repository

import androidx.room.withTransaction
import com.howardaistudio.subscriptionhub.data.local.SubscriptionDao
import com.howardaistudio.subscriptionhub.data.local.SubscriptionHubDatabase
import com.howardaistudio.subscriptionhub.data.local.toDomain
import com.howardaistudio.subscriptionhub.data.local.toEntity
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSubscriptionRepository @Inject constructor(
    private val database: SubscriptionHubDatabase,
    private val timeSource: TimeSource,
) : SubscriptionRepository {

    private val dao: SubscriptionDao get() = database.subscriptionDao()

    override fun observeAll(): Flow<List<Subscription>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: String): Flow<Subscription?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Subscription? = dao.get(id)?.toDomain()

    override suspend fun getAll(): List<Subscription> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(subscription: Subscription) {
        dao.upsert(subscription.toEntity(timeSource.today()))
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun deleteAll() {
        database.withTransaction {
            database.reminderLogDao().deleteAll()
            dao.deleteAll()
        }
    }

    override suspend fun refreshDerivedState(today: LocalDate, now: Instant) {
        database.withTransaction {
            val rows = dao.getAll()
            val changed = rows.mapNotNull { row ->
                val updated = row.toDomain().withTrialResolved(today, now).toEntity(today)
                updated.takeIf { it != row }
            }
            if (changed.isNotEmpty()) dao.upsertAll(changed)
        }
    }
}
