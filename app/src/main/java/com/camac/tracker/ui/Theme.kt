package com.camac.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camac.tracker.R

// ---------- Paleta: borova šuma, trska, mirna voda ----------

val Pine = Color(0xFF2E5E4E)
val PineDeep = Color(0xFF1F4337)
val Sage = Color(0xFF5F7D6B)
val Reed = Color(0xFFE8D9B0)
val FloatOrange = Color(0xFFD98C4A)

/** Statusne boje — prigušene, da ne "vrište". */
val OkGreen = Color(0xFF2E7D4F)
val WarnAmber = Color(0xFFA1672A)
val AlarmRed = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8DC),
    onPrimaryContainer = Color(0xFF0A2018),
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8DF),
    onSecondaryContainer = Color(0xFF1A2B21),
    tertiary = Color(0xFF8A6D3B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E3C3),
    onTertiaryContainer = Color(0xFF2B1F07),
    background = Color(0xFFF5F7F2),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5F7F2),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF414942),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF2EC),
    surfaceContainer = Color(0xFFE9EDE6),
    surfaceContainerHigh = Color(0xFFE3E8E0),
    surfaceContainerHighest = Color(0xFFDDE3DA),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),
    error = AlarmRed,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF94D3B8),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF164C3B),
    onPrimaryContainer = Color(0xFFB0EFD3),
    secondary = Color(0xFFB4CCBB),
    onSecondary = Color(0xFF203528),
    secondaryContainer = Color(0xFF374B3F),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFE0C38E),
    onTertiary = Color(0xFF402D04),
    tertiaryContainer = Color(0xFF5B4517),
    onTertiaryContainer = Color(0xFFFDDFA8),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DD),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404943),
)

// ---------- Font: Manrope (čist, moderan, ima č ć š ž đ) ----------

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private fun TextStyle.m(weight: FontWeight? = null, size: Int? = null, line: Int? = null) = copy(
    fontFamily = Manrope,
    fontWeight = weight ?: fontWeight,
    fontSize = size?.sp ?: fontSize,
    lineHeight = line?.sp ?: lineHeight,
)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.m(FontWeight.SemiBold),
        displayMedium = t.displayMedium.m(FontWeight.SemiBold),
        displaySmall = t.displaySmall.m(FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.m(FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.m(FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.m(FontWeight.SemiBold),
        titleLarge = t.titleLarge.m(FontWeight.SemiBold),
        titleMedium = t.titleMedium.m(FontWeight.SemiBold, 17, 24),
        titleSmall = t.titleSmall.m(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.m(size = 17, line = 25),
        bodyMedium = t.bodyMedium.m(size = 15, line = 21),
        bodySmall = t.bodySmall.m(size = 13, line = 18),
        labelLarge = t.labelLarge.m(FontWeight.SemiBold, 15),
        labelMedium = t.labelMedium.m(FontWeight.Medium),
        labelSmall = t.labelSmall.m(FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CamacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
