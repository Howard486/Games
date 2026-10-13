package com.howardaistudio.subscriptionhub.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.domain.money.CurrencyCatalog

/** Picks one of the supported ISO 4217 codes (SA FR-004). */
@Composable
fun CurrencyPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = stringResource(R.string.field_currency))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyCatalog.all.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency.code) },
                    onClick = {
                        onSelect(currency.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
