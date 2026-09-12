package com.kmt.healthanalyzer.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette « éditorial imprimé » de la coquille native.
 *
 * **Ce fichier est un doublon, comme `DomainColors.kt`.** L'original vit dans
 * `web/report/report.css` et reste la référence : Compose ne sait pas lire une variable
 * CSS. Chaque valeur ci-dessous porte en commentaire le jeton dont elle vient.
 * `ThemeTokenParityTest` relit la feuille de style et compare : une valeur changée d'un
 * seul côté fait échouer la suite, plutôt que de laisser les deux moitiés de l'app diverger
 * en silence.
 *
 * Avant, cette palette était un vert santé, et la coquille suivait en plus la couleur
 * dynamique d'Android 12. L'utilisateur passait donc d'une barre verte — ou de son fond
 * d'écran — à un rapport violet sur papier, dans le même écran. Un seul jeu de couleurs
 * pour les deux moitiés vaut mieux qu'une adaptation au système qu'aucune des deux ne
 * partage.
 *
 * Le fond est un papier cassé, pas un blanc d'écran : c'est le premier signe de la
 * direction, et il ne coûte rien.
 */

// --- Clair ---
val PaperLight = Color(0xFFF9F9F7) // --page
val SurfaceLight = Color(0xFFFCFCFB) // --surface
val InkLight = Color(0xFF0B0B0B) // --ink
val InkMutedLight = Color(0xFF52514E) // --ink-2
val GridLight = Color(0xFFE1E0D9) // --grid
/**
 * Le trait d'axe, et la bordure `outline` de Material.
 *
 * Mesuré à 1,70:1 sur le papier, il était sous le seuil de 3:1 des éléments non
 * textuels : la bordure d'un champ de saisie qu'on ne voit pas est un défaut
 * d'accessibilité, et la ligne de base d'un graphique disparaissait à l'impression.
 * Voir `ThemeContrastTest`.
 */
val AxisLight = Color(0xFF8E8D82) // --axis
val AccentLight = Color(0xFF4A3AA7) // --accent
val AccentSoftLight = Color(0x144A3AA7) // --accent-soft
/**
 * Le rouge d'alerte. Assombri : posé sur son propre aplat d'erreur, il ne tenait que
 * 4,08:1 — et un message d'erreur est le dernier texte qu'on veut rendre difficile à
 * lire. Il vaut maintenant 4,81:1 sur ce fond, 5,42:1 sur le papier.
 */
val CriticalLight = Color(0xFFBE3131) // --critical

/**
 * Encre lisible sur un aplat [AccentLight] / [AccentDark] — jeton `--accent-contrast`.
 *
 * Ce n'est pas simplement « blanc en clair, noir en sombre ». En thème sombre l'accent
 * devient un lavande clair sur lequel du blanc tombe à 3,13:1, sous le seuil de 4,5:1 ;
 * l'encre `#14130f` y remonte à 5,95:1. Les deux moitiés de l'app posent du texte sur ce
 * même violet et doivent y lire la même encre.
 */
val OnAccentLight = Color(0xFFFFFFFF) // --accent-contrast
val OnAccentDark = Color(0xFF14130F) // --accent-contrast (sombre)

// --- Sombre ---
val PaperDark = Color(0xFF0D0D0D) // --page
val SurfaceDark = Color(0xFF1A1A19) // --surface
val InkDark = Color(0xFFFFFFFF) // --ink
val InkMutedDark = Color(0xFFC3C2B7) // --ink-2
val GridDark = Color(0xFF2C2C2A) // --grid
val AxisDark = Color(0xFF64645D) // --axis
val AccentDark = Color(0xFF9085E9) // --accent
val AccentSoftDark = Color(0x1F9085E9) // --accent-soft
val CriticalDark = Color(0xFFE66767) // --critical

// --- Palette sémantique (tendances de santé) ---
// Une hausse n'est pas toujours une amélioration : ces couleurs suivent MetricTrend.isImprovement,
// jamais le sens brut de la variation. Elles restent distinctes des couleurs de domaine, qui
// disent « de quoi on parle » et non « est-ce que ça va ».
val ImprovementLight = Color(0xFF006300) // --good-text
val ImprovementDark = Color(0xFF0CA30C) // --good-text (sombre)
val DegradationLight = Color(0xFFB04A14) // --serious
val DegradationDark = Color(0xFFF0A07A) // --serious (sombre)
val NeutralTrendLight = Color(0xFF726F68) // --muted
val NeutralTrendDark = Color(0xFF94928B) // --muted (sombre)
