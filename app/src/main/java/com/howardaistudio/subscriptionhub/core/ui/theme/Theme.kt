package com.howardaistudio.subscriptionhub.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Placeholder brand colours until D-04 (visual identity) is decided.
private val Teal40 = Color(0xFF006A6A)
private val Teal80 = Color(0xFF4CDADA)
private val Slate40 = Color(0xFF4A6363)
private val Slate80 = Color(0xFFB0CCCB)

private val LightColors = lightColorScheme(primary = Teal40, secondary = Slate40)
private val DarkColors = darkColorScheme(primary = Teal80, secondary = Slate80)

/** Material 3 theme following the system light/dark setting (SA §4.3, D-04). */
@Composable
fun SubscriptionHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
