package com.kreativekoala.cleanup.ui.theme

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

private val CleanupBlue = Color(0xFF4A90D9)
private val CleanupPurple = Color(0xFF7B61FF)
private val CleanupCyan = Color(0xFF00BCD4)

private val LightColorScheme = lightColorScheme(
    primary = CleanupBlue,
    secondary = CleanupPurple,
    tertiary = CleanupCyan,
)

private val DarkColorScheme = darkColorScheme(
    primary = CleanupBlue,
    secondary = CleanupPurple,
    tertiary = CleanupCyan,
)

@Composable
fun CleanupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
