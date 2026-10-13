package com.howardaistudio.subscriptionhub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.core.ui.theme.SubscriptionHubTheme
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.settings.ThemeMode
import com.howardaistudio.subscriptionhub.feature.onboarding.WelcomeScreen
import com.howardaistudio.subscriptionhub.navigation.SubscriptionHubNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    /** Subscription to open from a reminder notification (SA FR-021). */
    private val pendingSubscriptionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation the back stack is restored; re-handling the intent would push a duplicate.
        if (savedInstanceState == null) consumeDeepLink(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
            val scope = rememberCoroutineScope()
            // Until preferences load, draw nothing rather than flash the wrong theme or screen.
            settings?.let { current ->
                val dark = when (current.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                SubscriptionHubTheme(darkTheme = dark) {
                    if (!current.onboardingDone) {
                        WelcomeScreen(initialCurrency = current.defaultCurrency) { currency ->
                            scope.launch { settingsRepository.update { it.copy(defaultCurrency = currency, onboardingDone = true) } }
                        }
                    } else {
                        SubscriptionHubNavHost(
                            deepLinkSubscriptionId = pendingSubscriptionId.value,
                            onDeepLinkHandled = { pendingSubscriptionId.value = null },
                        )
                    }
                }
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
