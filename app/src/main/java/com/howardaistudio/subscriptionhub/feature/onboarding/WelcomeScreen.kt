package com.howardaistudio.subscriptionhub.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.CurrencyPicker

/**
 * First launch (SA UC-01, SCR-01): explains the product and that data stays on the device, and
 * picks a default currency. Notification permission is deliberately not requested here.
 */
@Composable
fun WelcomeScreen(initialCurrency: String, onStart: (String) -> Unit) {
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            listOf(R.string.welcome_point_manage, R.string.welcome_point_forecast, R.string.welcome_point_remind).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.welcome_local_only), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.estimate_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.welcome_currency), modifier = Modifier.weight(1f))
                CurrencyPicker(currency, { currency = it })
            }
            Button(onClick = { onStart(currency) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_get_started))
            }
        }
    }
}
