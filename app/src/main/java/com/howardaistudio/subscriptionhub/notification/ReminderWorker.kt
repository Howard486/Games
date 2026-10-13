package com.howardaistudio.subscriptionhub.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/**
 * Runs [ReminderRunner]. Dependencies come from a Hilt entry point, which avoids a custom
 * WorkerFactory and the manifest changes it would require.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reminderRunner(): ReminderRunner
    }

    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).reminderRunner().run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Never log subscription data (SA NFR-08); the exception type is enough to diagnose.
        Log.w(TAG, "Reminder scan failed: ${e.javaClass.simpleName}")
        Result.retry()
    }

    private companion object {
        const val TAG = "ReminderWorker"
    }
}
