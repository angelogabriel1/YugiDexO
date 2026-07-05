package com.yugidex.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PharaohGold = Color(0xFFFFD54F)
val MysticGold = Color(0xFFD4AF37)
val GoldGlow = Color(0xFFFFF6B3)
val DarkObsidian = Color(0xFF0A0814)
val CardViolet = Color(0xFF241442)
val SpellPurple = Color(0xFF1B0D2D)
val TextGray = Color(0xFFA0A0C0)

private val colors = darkColorScheme(
    primary = PharaohGold, onPrimary = DarkObsidian, secondary = MysticGold,
    background = DarkObsidian, surface = SpellPurple, surfaceVariant = CardViolet,
    onBackground = Color(0xFFF9F5FF), onSurface = Color(0xFFF9F5FF), onSurfaceVariant = TextGray
)

@Composable fun YugidexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

