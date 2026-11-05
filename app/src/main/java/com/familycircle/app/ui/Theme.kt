package com.familycircle.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF5B4FE0)
val IndigoLight = Color(0xFF7A6FF5)
val IndigoSoft = Color(0xFFEEEBFF)
val GreenOk = Color(0xFF22A06B)
val GreenSoft = Color(0xFFDDF7E8)
val Muted = Color(0xFF7A7A90)

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White,
    background = Color(0xFFF6F5FF), onBackground = Color(0xFF1B1B2F),
    surface = Color.White, onSurface = Color(0xFF1B1B2F),
    secondaryContainer = IndigoSoft,
    outlineVariant = Color(0xFFE6E3FA)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8C82FF), onPrimary = Color.White,
    background = Color(0xFF12121C), onBackground = Color(0xFFEDEDF7),
    surface = Color(0xFF1C1C2B), onSurface = Color(0xFFEDEDF7),
    secondaryContainer = Color(0xFF2A2750),
    outlineVariant = Color(0xFF2E2E45)
)

@Composable
fun FamilyCircleTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}