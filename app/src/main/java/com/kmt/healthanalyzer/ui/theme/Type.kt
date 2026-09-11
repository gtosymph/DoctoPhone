package com.kmt.healthanalyzer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kmt.healthanalyzer.R

/**
 * Typographie de la coquille native.
 *
 * **Ces deux familles sont les mêmes que celles du rapport.** `web/report/report.css`
 * déclare « Source Serif 4 » et « Public Sans » par `@font-face` ; les fichiers de
 * `res/font` ci-dessous sont les mêmes fontes, dans le même sous-ensemble latin, en
 * `.ttf` plutôt qu'en `.woff2` — Android ne lit pas le woff2. Sans cette duplication,
 * l'utilisateur passerait d'un écran natif dans une police à un rapport WebView dans une
 * autre, à l'intérieur du même écran.
 *
 * Deux familles, deux rôles, jamais mélangés :
 * - la **serif** porte les titres, et rien d'autre. C'est elle qui donne le grain
 *   « document » de la direction éditoriale.
 * - la **sans** porte le texte courant, les libellés et surtout les chiffres. Public Sans
 *   a de vrais chiffres tabulaires, ce qui est la raison de son choix : dans cette app,
 *   des nombres s'alignent en colonne à chaque écran.
 *
 * Chaque famille n'existe qu'en deux graisses, 400 et 600. Ne demandez pas
 * [FontWeight.Medium] ni [FontWeight.Bold] : Android synthétiserait la graisse absente en
 * épaississant les contours, et le résultat est visiblement plus sale que la vraie
 * graisse. Les mêmes deux graisses, et la même règle, valent côté CSS.
 */
private val SourceSerif4 = FontFamily(
    Font(R.font.source_serif_4_regular, FontWeight.Normal),
    Font(R.font.source_serif_4_semibold, FontWeight.SemiBold),
)

private val PublicSans = FontFamily(
    Font(R.font.public_sans_regular, FontWeight.Normal),
    Font(R.font.public_sans_semibold, FontWeight.SemiBold),
)

/**
 * Chiffres tabulaires, demandés à la fonte par son propre jeu de caractères.
 *
 * `tnum` donne à chaque chiffre la même chasse. Sans lui, un 1 est plus étroit qu'un 8 et
 * une colonne de mesures se décale d'une ligne à l'autre — le défaut se voit surtout quand
 * une valeur se rafraîchit en place, où le texte autour bouge à chaque mise à jour.
 */
private const val TABULAR_FIGURES = "tnum"

/**
 * Échelle typographique, identique à celle de `report.css`.
 *
 * Les interlignes suivent le rapport corps/interligne des specs : 1,15 pour un grand
 * titre, 1,55 pour du texte courant. Un titre serré et un texte aéré, pas l'inverse.
 */
val HealthAnalyzerTypography = Typography(
    // Un chiffre de tête, seul objet de son écran : la valeur d'un réglage, un total
    // d'import. En sans, parce que c'est un nombre, jamais en serif.
    displaySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        // 44 et non 40 : un interligne égal au corps ne laisse pas la place des jambages
        // ni des accents, et Compose rogne le bas des glyphes au lieu d'agrandir la ligne.
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    headlineLarge = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    // À partir d'ici, tout est en sans : ce ne sont plus des titres de document mais des
    // libellés d'interface, et la serif y perdrait en lisibilité ce qu'elle gagnerait en
    // caractère.
    titleMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    bodyLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    bodyMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    bodySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    labelLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    // Étiquette en petites capitales (voir `SectionLabel`). L'interlettrage n'est pas une
    // coquetterie : des capitales serrées se lisent nettement moins bien que des bas de
    // casse, et l'espacement compense.
    labelSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.9.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)
