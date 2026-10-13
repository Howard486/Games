package com.howardaistudio.subscriptionhub.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.howardaistudio.subscriptionhub.R

/**
 * Shown while system notifications are unavailable (SA FR-020, AT-20). The app keeps working;
 * reminders stay visible in the in-app upcoming list. The permission is requested only from
 * this explicit action, never on launch (SA SCR-01).
 */
@Composable
fun NotificationPermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(AndroidReminderNotifier.notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = AndroidReminderNotifier.notificationsAllowed(context)
        onPauseOrDispose { }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = AndroidReminderNotifier.notificationsAllowed(context)
        if (allowed) ReminderScheduler.runNow(context)
    }

    if (!allowed) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.notifications_off_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.notifications_off_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(onClick = { requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(stringResource(R.string.action_allow_notifications))
                        }
                    }
                    TextButton(onClick = { openNotificationSettings(context) }) {
                        Text(stringResource(R.string.action_open_notification_settings))
                    }
                }
            }
        }
    }
}

/** Fallback when the permission was permanently denied or notifications are blocked per app. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
