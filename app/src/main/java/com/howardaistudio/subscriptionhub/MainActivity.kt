package com.howardaistudio.subscriptionhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.howardaistudio.subscriptionhub.core.ui.theme.SubscriptionHubTheme
import com.howardaistudio.subscriptionhub.navigation.SubscriptionHubNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubscriptionHubTheme {
                SubscriptionHubNavHost()
            }
        }
    }
}
