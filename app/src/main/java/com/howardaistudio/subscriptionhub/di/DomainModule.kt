package com.howardaistudio.subscriptionhub.di

import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.domain.usecase.ManageSubscriptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Domain use cases are plain Kotlin (no javax.inject), so they are provided here. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun provideManageSubscriptions(repository: SubscriptionRepository, timeSource: TimeSource): ManageSubscriptions =
        ManageSubscriptions(repository, timeSource)
}

