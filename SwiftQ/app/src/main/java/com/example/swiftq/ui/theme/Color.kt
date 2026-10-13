package com.example.swiftq.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// SwiftQ design tokens (Figma variables)
val SwiftQBlue = Color(0xFF007AFF)
val SwiftQBlueText = Color(0xFF0062CC)
val SwiftQBlue50 = Color(0xFFF0F7FF)
val SwiftQLabel = Color(0xFF1C1C1E)
val SwiftQSecondary = Color(0xFF6C6C70)
val SwiftQTertiary = Color(0xFF8E8E93)
val SwiftQSeparator = Color(0xFFE5E5EA)
val SwiftQGrouped = Color(0xFFF2F2F7)

/** The SwiftQ palette for one appearance. Screens read it through [SwiftQTheme.colors]. */
@Immutable
data class SwiftQColors(
    val background: Color,
    /** Tiles that sit on top of a [grouped] row. */
    val surface: Color,
    val grouped: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val blue: Color,
    val blueText: Color,
    val blue50: Color,
)

val LightSwiftQColors = SwiftQColors(
    background = Color.White,
    surface = Color.White,
    grouped = SwiftQGrouped,
    label = SwiftQLabel,
    secondary = SwiftQSecondary,
    tertiary = SwiftQTertiary,
    separator = SwiftQSeparator,
    blue = SwiftQBlue,
    blueText = SwiftQBlueText,
    blue50 = SwiftQBlue50,
)

// The Figma file only defines the light palette; these are its dark counterparts.
val DarkSwiftQColors = SwiftQColors(
    background = Color(0xFF000000),
    surface = Color(0xFF2C2C2E),
    grouped = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondary = Color(0xFFAEAEB2),
    tertiary = Color(0xFF8E8E93),
    separator = Color(0xFF38383A),
    blue = Color(0xFF0A84FF),
    blueText = Color(0xFF5EB0FF),
    blue50 = Color(0xFF0A2A4D),
)

val LocalSwiftQColors = staticCompositionLocalOf { LightSwiftQColors }
