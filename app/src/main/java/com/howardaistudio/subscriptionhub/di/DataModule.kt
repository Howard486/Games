package com.howardaistudio.subscriptionhub.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.howardaistudio.subscriptionhub.data.local.SubscriptionHubDatabase
import com.howardaistudio.subscriptionhub.data.preferences.DataStoreSettingsRepository
import com.howardaistudio.subscriptionhub.data.repository.RoomReminderLogRepository
import com.howardaistudio.subscriptionhub.data.repository.RoomSubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.repository.ReminderLogRepository
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.SystemTimeSource
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SubscriptionHubDatabase =
        // No fallbackToDestructiveMigration: schema upgrades must ship real migrations (SA DoD #3).
        Room.databaseBuilder(context, SubscriptionHubDatabase::class.java, SubscriptionHubDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

    @Provides
    fun provideTimeSource(): TimeSource = SystemTimeSource
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    abstract fun bindSubscriptionRepository(impl: RoomSubscriptionRepository): SubscriptionRepository

    @Binds
    abstract fun bindReminderLogRepository(impl: RoomReminderLogRepository): ReminderLogRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
