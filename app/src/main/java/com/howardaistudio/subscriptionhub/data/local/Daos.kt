package com.howardaistudio.subscriptionhub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscription WHERE id = :id")
    fun observe(id: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscription WHERE id = :id")
    suspend fun get(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscription")
    suspend fun getAll(): List<SubscriptionEntity>

    /**
     * Room's @Upsert is insert-then-update, not `INSERT OR REPLACE`. REPLACE deletes the row
     * first, which would cascade-delete its reminder logs and re-send reminders.
     */
    @Upsert
    suspend fun upsert(entity: SubscriptionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SubscriptionEntity>)

    @Query("DELETE FROM subscription WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM subscription")
    suspend fun deleteAll()
}

@Dao
abstract class ReminderLogDao {
    @Query(
        "SELECT subscription_id, renewal_date, days_before FROM reminder_log " +
            "WHERE status = 'SENT' AND renewal_date >= :fromDate",
    )
    abstract suspend fun sentKeys(fromDate: String): List<ReminderKeyRow>

    @Query(
        "SELECT * FROM reminder_log WHERE subscription_id = :subscriptionId " +
            "AND renewal_date = :renewalDate AND days_before = :daysBefore",
    )
    abstract suspend fun find(subscriptionId: String, renewalDate: String, daysBefore: Int): ReminderLogEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnoringConflict(entity: ReminderLogEntity): Long

    @Query(
        "UPDATE reminder_log SET status = :status, sent_at_epoch_ms = :sentAtEpochMs " +
            "WHERE subscription_id = :subscriptionId AND renewal_date = :renewalDate " +
            "AND days_before = :daysBefore AND status <> 'SENT'",
    )
    abstract suspend fun updateUnlessSent(
        subscriptionId: String,
        renewalDate: String,
        daysBefore: Int,
        status: String,
        sentAtEpochMs: Long,
    ): Int

    /**
     * Insert, or overwrite an existing non-SENT row (dev plan G3). Implemented as insert-or-ignore
     * plus a guarded update instead of SQL `ON CONFLICT DO UPDATE`, which needs SQLite 3.24
     * (Android 11) while minSdk is 26.
     */
    @Transaction
    open suspend fun record(entity: ReminderLogEntity) {
        if (insertIgnoringConflict(entity) == -1L) {
            updateUnlessSent(entity.subscriptionId, entity.renewalDate, entity.daysBefore, entity.status, entity.sentAtEpochMs)
        }
    }

    @Query("DELETE FROM reminder_log WHERE renewal_date < :date")
    abstract suspend fun deleteBefore(date: String)

    @Query("DELETE FROM reminder_log")
    abstract suspend fun deleteAll()
}
