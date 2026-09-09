package com.kmt.healthanalyzer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette « éditorial coloré » : une couleur par domaine de santé.
 *
 * **Ce jeu est un doublon.** L'original vit dans `web/report/report.css`, section
 * « Couleurs de domaine », et reste la référence : Compose ne peut pas lire ses variables
 * CSS. Toute modification de l'un des deux jeux doit être reportée dans l'autre, sinon la
 * coquille native et le rapport WebView divergent visuellement.
 *
 * Trois variantes par domaine, à l'image du CSS :
 * - [base] pour une icône, un filet de titre, une piste de curseur — jamais un texte fin,
 *   son contraste sur fond clair descend parfois sous 4,5:1 (mesuré, voir `ink`).
 * - [soft] pour un fond de bloc ou de puce.
 * - [ink] pour un texte lisible sur fond clair ou sombre : c'est la seule variante à
 *   utiliser derrière un `Text`.
 */
data class DomainColor(val base: Color, val soft: Color, val ink: Color)

data class DomainColors(
    val sleep: DomainColor,
    val heart: DomainColor,
    val activity: DomainColor,
    val body: DomainColor,
    val vitality: DomainColor,
)

internal val LightDomainColors = DomainColors(
    sleep = DomainColor(base = Color(0xFF4A3AA7), soft = Color(0x184A3AA7), ink = Color(0xFF3A2D85)),
    heart = DomainColor(base = Color(0xFFCF3446), soft = Color(0x18CF3446), ink = Color(0xFFA8202F)),
    activity = DomainColor(base = Color(0xFF0F8A68), soft = Color(0x1A0F8A68), ink = Color(0xFF0B6A50)),
    body = DomainColor(base = Color(0xFFB56A0F), soft = Color(0x1AB56A0F), ink = Color(0xFF8D520A)),
    vitality = DomainColor(base = Color(0xFF2A78D6), soft = Color(0x182A78D6), ink = Color(0xFF1F5DA8)),
)

internal val DarkDomainColors = DomainColors(
    sleep = DomainColor(base = Color(0xFF9085E9), soft = Color(0x269085E9), ink = Color(0xFFB3ABF1)),
    heart = DomainColor(base = Color(0xFFF4707F), soft = Color(0x26F4707F), ink = Color(0xFFF9A0AA)),
    activity = DomainColor(base = Color(0xFF2FC79A), soft = Color(0x262FC79A), ink = Color(0xFF6FDCBB)),
    body = DomainColor(base = Color(0xFFEDA13C), soft = Color(0x26EDA13C), ink = Color(0xFFF3BF78)),
    vitality = DomainColor(base = Color(0xFF4F9BF0), soft = Color(0x264F9BF0), ink = Color(0xFF8BBCF6)),
)

internal val LocalDomainColors = staticCompositionLocalOf { LightDomainColors }

/** Accès aux couleurs de domaine depuis n'importe quel composable, à l'image de [HealthAnalyzerTheme.trendColors]. */
val HealthAnalyzerTheme.domainColors: DomainColors
    @Composable get() = LocalDomainColors.current
