package com.howardaistudio.subscriptionhub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema history is exported to `app/schemas/` and committed; CI fails if it drifts. Every future
 * version needs a real migration plus a migration test (SA §13.1 DoD #3): never
 * `fallbackToDestructiveMigration` in release builds.
 */
@Database(
    entities = [SubscriptionEntity::class, ReminderLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SubscriptionHubDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun reminderLogDao(): ReminderLogDao

    companion object {
        const val NAME = "subscription_hub.db"
    }
}
