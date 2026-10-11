package com.howardaistudio.subscriptionhub.data.repository

import com.howardaistudio.subscriptionhub.data.local.ReminderLogEntity
import com.howardaistudio.subscriptionhub.data.local.SubscriptionHubDatabase
import com.howardaistudio.subscriptionhub.data.local.toDomain
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderKey
import com.howardaistudio.subscriptionhub.domain.repository.ReminderDeliveryStatus
import com.howardaistudio.subscriptionhub.domain.repository.ReminderLogRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReminderLogRepository @Inject constructor(
    private val database: SubscriptionHubDatabase,
) : ReminderLogRepository {

    private val dao get() = database.reminderLogDao()

    override suspend fun sentKeys(fromDate: LocalDate): Set<ReminderKey> =
        dao.sentKeys(fromDate.toString()).mapTo(HashSet()) { it.toDomain() }

    override suspend fun record(key: ReminderKey, status: ReminderDeliveryStatus, at: Instant) {
        dao.record(
            ReminderLogEntity(
                id = UUID.randomUUID().toString(),
                subscriptionId = key.subscriptionId,
                renewalDate = key.renewalDate.toString(),
                daysBefore = key.daysBefore,
                sentAtEpochMs = at.toEpochMilli(),
                status = status.name,
            ),
        )
    }

    override suspend fun pruneBefore(date: LocalDate) {
        dao.deleteBefore(date.toString())
    }
}
