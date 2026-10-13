package com.howardaistudio.subscriptionhub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.howardaistudio.subscriptionhub.core.ui.theme.SubscriptionHubTheme
import com.howardaistudio.subscriptionhub.navigation.SubscriptionHubNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Subscription to open from a reminder notification (SA FR-021). */
    private val pendingSubscriptionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation the back stack is restored; re-handling the intent would push a duplicate.
        if (savedInstanceState == null) consumeDeepLink(intent)
        setContent {
            SubscriptionHubTheme {
                SubscriptionHubNavHost(
                    deepLinkSubscriptionId = pendingSubscriptionId.value,
                    onDeepLinkHandled = { pendingSubscriptionId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_SUBSCRIPTION) return
        pendingSubscriptionId.value = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID)
        intent.removeExtra(EXTRA_SUBSCRIPTION_ID)
    }

    companion object {
        private const val ACTION_OPEN_SUBSCRIPTION = "com.howardaistudio.subscriptionhub.action.OPEN_SUBSCRIPTION"
        private const val EXTRA_SUBSCRIPTION_ID = "subscription_id"

        fun openSubscriptionIntent(context: Context, subscriptionId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_SUBSCRIPTION)
                .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
