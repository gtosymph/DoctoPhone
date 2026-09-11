package com.kmt.healthanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

/*
 * Les rôles Material, remplis avec les jetons de `report.css`.
 *
 * `surfaceContainer*` porte `--surface` : c'est la couleur d'une carte, un ton au-dessus
 * du papier. `surface` et `background` portent `--page`, donc une carte se détache du fond
 * sans avoir besoin d'ombre — ce que la direction éditoriale demande.
 *
 * `outlineVariant` porte `--grid` (le filet fin d'un séparateur) et `outline` porte
 * `--axis` (le trait plus affirmé d'une bordure). Les intervertir donne des séparateurs
 * trop lourds et des bordures trop timides.
 */
private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
    secondary = InkMutedLight,
    onSecondary = PaperLight,
    secondaryContainer = GridLight,
    onSecondaryContainer = InkLight,
    tertiary = AccentLight,
    onTertiary = OnAccentLight,
    tertiaryContainer = AccentSoftLight,
    onTertiaryContainer = AccentLight,
    error = CriticalLight,
    onError = Color.White,
    errorContainer = Color(0x14D03B3B),
    onErrorContainer = CriticalLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PaperLight,
    onSurface = InkLight,
    surfaceVariant = GridLight,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = PaperLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = GridLight,
    outline = AxisLight,
    outlineVariant = GridLight,
    /*
     * `surfaceTint` vaut `primary` par défaut, et Material 3 le mélange dans certaines
     * surfaces selon leur élévation — barre d'application défilée, feuille modale. Le
     * mettre à la couleur de fond rend ce mélange sans effet et garantit que la palette
     * déclarée ici est bien celle qui s'affiche.
     *
     * Mesuré sur émulateur : sur les écrans actuels, l'enlever ne change rien, Material 3
     * étant passé aux rôles `surfaceContainer*` plutôt qu'à la teinte d'élévation. La ligne
     * reste comme garde-fou pour les composants qui s'en servent encore.
     *
     * N'écrivez pas `Color.Transparent` : `surfaceColorAtElevation` fait
     * `surfaceTint.copy(alpha = …)`, et le transparent est un NOIR d'alpha zéro — la copie
     * rétablit son alpha et salirait les surfaces au lieu de les laisser intactes.
     */
    surfaceTint = PaperLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    secondary = InkMutedDark,
    onSecondary = PaperDark,
    secondaryContainer = GridDark,
    onSecondaryContainer = InkDark,
    tertiary = AccentDark,
    onTertiary = OnAccentDark,
    tertiaryContainer = AccentSoftDark,
    onTertiaryContainer = AccentDark,
    error = CriticalDark,
    onError = PaperDark,
    errorContainer = Color(0x1FE66767),
    onErrorContainer = CriticalDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = GridDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerHighest = GridDark,
    outline = AxisDark,
    outlineVariant = GridDark,
    /** Même raison qu'en clair : voir le commentaire de [LightColors]. */
    surfaceTint = PaperDark,
)

/**
 * Thème Material 3 de l'app.
 *
 * **La couleur dynamique d'Android 12 n'est pas utilisée, et c'est délibéré.** Material You
 * habille l'app avec le fond d'écran de l'appareil. Ici, la moitié de l'interface est un
 * rapport dessiné par une WebView, qui ne peut pas lire cette couleur : l'app prenait donc
 * l'apparence du fond d'écran d'un côté et gardait sa palette éditoriale de l'autre, dans
 * le même écran. Une identité tenue de bout en bout vaut mieux qu'une adaptation que seule
 * une moitié sait suivre.
 *
 * Les deux modes, clair et sombre, restent pris en charge et suivent le réglage du système.
 */
@Composable
fun HealthAnalyzerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
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
