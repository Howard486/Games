package com.howardaistudio.subscriptionhub.di

import com.howardaistudio.subscriptionhub.notification.AndroidReminderNotifier
import com.howardaistudio.subscriptionhub.notification.ReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}
