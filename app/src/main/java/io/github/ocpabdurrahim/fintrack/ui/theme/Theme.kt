package io.github.ocpabdurrahim.fintrack.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

// A Material 3 scheme built around an emerald seed (money green, not traffic-light green).
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F2CE),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4D6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6473),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E9FB),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFECF2EC),
    inversePrimary = Color(0xFF88D6B3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D6B3),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFA4F2CE),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA5CDDE),
    onTertiary = Color(0xFF063543),
    tertiaryContainer = Color(0xFF244C5A),
    onTertiaryContainer = Color(0xFFC1E9FB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF1B6B4F),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

/** Colours Material does not have a slot for: money in, money out, money moved. */
@Immutable
data class FinColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightFinColors = FinColors(
    income = Color(0xFF1E8E4E),
    expense = Color(0xFFD23F31),
    transfer = Color(0xFF1A73E8),
    warning = Color(0xFFE09A12),
    heroStart = Color(0xFF22946B),
    heroEnd = Color(0xFF0B5D45),
)

private val DarkFinColors = FinColors(
    income = Color(0xFF6DD58C),
    expense = Color(0xFFFF8A80),
    transfer = Color(0xFF8AB4F8),
    warning = Color(0xFFFFCC66),
    heroStart = Color(0xFF1C7457),
    heroEnd = Color(0xFF0D3B2E),
)

val LocalFinColors = staticCompositionLocalOf { LightFinColors }

object FinTheme {
    val colors: FinColors
        @Composable @ReadOnlyComposable get() = LocalFinColors.current
}

private val FinTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun FinTheme(dark: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val base = if (dark) DarkFinColors else LightFinColors
    // With wallpaper colours the balance card follows the wallpaper too.
    val finColors = when {
        useDynamic && dark -> base.copy(heroStart = scheme.primaryContainer, heroEnd = scheme.secondaryContainer)
        useDynamic -> base.copy(heroStart = scheme.primary, heroEnd = scheme.tertiary)
        else -> base
    }
    CompositionLocalProvider(LocalFinColors provides finColors) {
        MaterialTheme(colorScheme = scheme, typography = FinTypography, content = content)
    }
}
