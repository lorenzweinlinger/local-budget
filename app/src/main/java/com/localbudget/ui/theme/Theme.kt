package com.localbudget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EFD2),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357),
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3D6373),
    tertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFF7FAF7),
    surfaceContainer = Color(0xFFEBEFEB),
    surfaceContainerHigh = Color(0xFFE5EAE5),
    surfaceContainerLow = Color(0xFFF1F5F1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD3A6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFFB9EFD2),
    secondary = Color(0xFFB3CCBE),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA5CCDF),
    tertiaryContainer = Color(0xFF244C5B),
    background = Color(0xFF101411),
    surface = Color(0xFF101411),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerLow = Color(0xFF181D1A),
)

@Immutable
data class MoneyColors(val income: Color, val expense: Color, val over: Color)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(income = Color(0xFF1B7F3B), expense = Color.Unspecified, over = Color(0xFFBA1A1A))
}

@Composable
fun BudgetTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val moneyColors = if (dark) {
        MoneyColors(income = Color(0xFF7BD88F), expense = colorScheme.onSurface, over = Color(0xFFFFB4AB))
    } else {
        MoneyColors(income = Color(0xFF1B7F3B), expense = colorScheme.onSurface, over = Color(0xFFBA1A1A))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
