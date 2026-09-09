package com.kmt.healthanalyzer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Couleurs sémantiques pour les tendances de santé, distinctes des rôles Material. */
data class HealthTrendColors(
    val improvement: Color,
    val degradation: Color,
    val neutral: Color,
)

private val LightTrendColors = HealthTrendColors(
    improvement = ImprovementLight,
    degradation = DegradationLight,
    neutral = NeutralTrendLight,
)

private val DarkTrendColors = HealthTrendColors(
    improvement = ImprovementDark,
    degradation = DegradationDark,
    neutral = NeutralTrendDark,
)

private val LocalHealthTrendColors = staticCompositionLocalOf { LightTrendColors }

/** Accès aux couleurs de tendance depuis n'importe quel composable, à l'image de [MaterialTheme]. */
object HealthAnalyzerTheme {
    val trendColors: HealthTrendColors
        @Composable get() = LocalHealthTrendColors.current
}

private val LightColors = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = OnGreenPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    onPrimaryContainer = OnGreenPrimaryContainerLight,
    secondary = SandSecondaryLight,
    onSecondary = OnSandSecondaryLight,
    secondaryContainer = SandSecondaryContainerLight,
    onSecondaryContainer = OnSandSecondaryContainerLight,
    tertiary = SlateTertiaryLight,
    onTertiary = OnSlateTertiaryLight,
    tertiaryContainer = SlateTertiaryContainerLight,
    onTertiaryContainer = OnSlateTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnGreenPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = OnGreenPrimaryContainerDark,
    secondary = SandSecondaryDark,
    onSecondary = OnSandSecondaryDark,
    secondaryContainer = SandSecondaryContainerDark,
    onSecondaryContainer = OnSandSecondaryContainerDark,
    tertiary = SlateTertiaryDark,
    onTertiary = OnSlateTertiaryDark,
    tertiaryContainer = SlateTertiaryContainerDark,
    onTertiaryContainer = OnSlateTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

/**
 * Thème Material 3 de l'app.
 *
 * Utilise la couleur dynamique du système (Android 12+, Material You) quand elle est
 * disponible, sinon la palette de repli verte/sable/ardoise ci-dessus. Les deux modes,
 * clair et sombre, sont pris en charge.
 */
@Composable
fun HealthAnalyzerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val trendColors = if (darkTheme) DarkTrendColors else LightTrendColors
    val domainColors = if (darkTheme) DarkDomainColors else LightDomainColors

    CompositionLocalProvider(
        LocalHealthTrendColors provides trendColors,
        LocalDomainColors provides domainColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HealthAnalyzerTypography,
            content = content,
        )
    }
}
