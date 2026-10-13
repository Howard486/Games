package com.howardaistudio.subscriptionhub

import android.app.Application
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.notification.AndroidReminderNotifier
import com.howardaistudio.subscriptionhub.notification.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SubscriptionHubApp : Application() {

    @Inject lateinit var notifier: AndroidReminderNotifier
    @Inject lateinit var subscriptions: SubscriptionRepository
    @Inject lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        ReminderScheduler.ensurePeriodic(this)
        rescanOnChanges()
    }

    /**
     * Any change to subscriptions or reminder settings (including the first emission at start-up)
     * triggers an immediate scan (SA FR-018). Debounced so a burst of edits runs one scan.
     */
    @OptIn(FlowPreview::class)
    private fun rescanOnChanges() {
        appScope.launch {
            combine(subscriptions.observeAll(), settings.settings) { all, appSettings -> all to appSettings.reminders }
                .distinctUntilChanged()
                .debounce(RESCAN_DEBOUNCE_MS)
                .collect { ReminderScheduler.runNow(this@SubscriptionHubApp) }
        }
    }

    private companion object {
        const val RESCAN_DEBOUNCE_MS = 1_000L
    }
}
