package com.example.localplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Palette (from the design tokens):
 *  - Brand orange #ff9f3d is a FILL colour (play buttons, badges). It is only ~2:1 on white, so it is never used for light-theme text.
 *  - Light text accent is the darkened orange #a34200 (6:1 on bg); dark text accent is #ffa94d.
 *  - In Material terms: `primary` = text/selection/progress accent, `primaryContainer` = brand-orange fill (see PlayFab).
 *  - Neutrals: light = slate on #f8fafc paper with white cards; dark = navy-cast near-black (#090d15 / #141924).
 */
val Accent = Color(0xFFFF9F3D)      // brand orange: fills, and accents on the always-dark Now Playing screen
val OnAccent = Color(0xFF0B1020)    // ink on top of brand orange
val BrandNavy = Color(0xFF112441)
val Indigo = Color(0xFF6366F1)      // "cosmic" secondary; decoration only, never a control

object Dimens {
    val screen = 20.dp       // horizontal screen margin
    val row = 64.dp          // min list-row height
    val thumb = 52.dp        // list artwork
    val radiusS = 8.dp
    val radiusM = 12.dp
    val radiusL = 16.dp
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFA94D), onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFFFF9F3D), onPrimaryContainer = Color(0xFF0B1020),
    secondary = Color(0xFFFFB566), onSecondary = Color(0xFF0B1020),
    tertiary = Color(0xFF64D2FF), onTertiary = Color(0xFF0B1020),
    tertiaryContainer = Color(0xFF142531), onTertiaryContainer = Color(0xFF64D2FF),
    background = Color(0xFF090D15), onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF090D15), onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1B2130), onSurfaceVariant = Color(0xFF9DA0A6),
    surfaceContainerLow = Color(0xFF10141D), surfaceContainer = Color(0xFF141924),
    surfaceContainerHigh = Color(0xFF1B2130), surfaceContainerHighest = Color(0xFF232A3A),
    secondaryContainer = Color(0xFF232A3A), onSecondaryContainer = Color(0xFFF8FAFC),
    outline = Color(0xFF394150), outlineVariant = Color(0xFF252B38), error = Color(0xFFFF7A70)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFFA34200), onPrimary = Color.White,
    primaryContainer = Color(0xFFFF9F3D), onPrimaryContainer = Color(0xFF0B1020),
    secondary = Color(0xFF853400), onSecondary = Color.White,
    tertiary = Color(0xFF006C82), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECF4F6), onTertiaryContainer = Color(0xFF006C82),
    background = Color(0xFFF8FAFC), onBackground = Color(0xFF0B1020),
    surface = Color(0xFFF8FAFC), onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9), surfaceContainerHighest = Color(0xFFE2E8F0),
    secondaryContainer = Color(0xFFF1F5F9), onSecondaryContainer = Color(0xFF151B32),
    outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0), error = Color(0xFFB3261E)
)

private fun t(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    headlineLarge = t(32, 38, FontWeight.ExtraBold, -0.6),   // screen titles
    headlineMedium = t(26, 32, FontWeight.Bold, -0.4),       // album / playlist titles
    headlineSmall = t(22, 28, FontWeight.Bold, -0.3),        // now-playing title
    titleLarge = t(20, 26, FontWeight.SemiBold, -0.2),       // section headers
    titleMedium = t(16, 22, FontWeight.SemiBold),
    titleSmall = t(15, 20, FontWeight.Medium),               // list-row title
    bodyLarge = t(16, 24, FontWeight.Normal),
    bodyMedium = t(14, 20, FontWeight.Normal),
    bodySmall = t(13, 18, FontWeight.Normal),                // list-row subtitle
    labelLarge = t(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = t(12, 16, FontWeight.Medium, 0.4),
    labelSmall = t(11, 14, FontWeight.Medium, 0.5)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        // A Surface provides LocalContentColor. Without it, any Text that doesn't set a colour defaults to BLACK,
        // which is unreadable on the dark background (Home titles, section headers, etc.).
        Surface(color = colors.background, contentColor = colors.onBackground, content = content)
    }
}
