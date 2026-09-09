package com.kmt.healthanalyzer.ui.theme

import androidx.compose.ui.graphics.Color

// Palette de repli (utilisée quand la couleur dynamique n'est pas disponible, Android < 12).
// Ton principal : vert profond santé. Tons secondaires : sable et ardoise.

// --- Clair ---
val GreenPrimaryLight = Color(0xFF1B6B4A)
val OnGreenPrimaryLight = Color(0xFFFFFFFF)
val GreenPrimaryContainerLight = Color(0xFFA8F2C9)
val OnGreenPrimaryContainerLight = Color(0xFF00210F)

val SandSecondaryLight = Color(0xFF6B5D4F)
val OnSandSecondaryLight = Color(0xFFFFFFFF)
val SandSecondaryContainerLight = Color(0xFFF3E0CB)
val OnSandSecondaryContainerLight = Color(0xFF241A0C)

val SlateTertiaryLight = Color(0xFF3E5D66)
val OnSlateTertiaryLight = Color(0xFFFFFFFF)
val SlateTertiaryContainerLight = Color(0xFFC1E8F2)
val OnSlateTertiaryContainerLight = Color(0xFF001F24)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFBFDF8)
val OnBackgroundLight = Color(0xFF191C1A)
val SurfaceVariantLight = Color(0xFFDCE5DA)
val OnSurfaceVariantLight = Color(0xFF414942)
val OutlineLight = Color(0xFF717972)

// --- Sombre ---
val GreenPrimaryDark = Color(0xFF8CD5AB)
val OnGreenPrimaryDark = Color(0xFF00391D)
val GreenPrimaryContainerDark = Color(0xFF00522D)
val OnGreenPrimaryContainerDark = Color(0xFFA8F2C9)

val SandSecondaryDark = Color(0xFFD7C3AB)
val OnSandSecondaryDark = Color(0xFF3B2F20)
val SandSecondaryContainerDark = Color(0xFF534635)
val OnSandSecondaryContainerDark = Color(0xFFF3E0CB)

val SlateTertiaryDark = Color(0xFFA5CCD6)
val OnSlateTertiaryDark = Color(0xFF06333C)
val SlateTertiaryContainerDark = Color(0xFF254A53)
val OnSlateTertiaryContainerDark = Color(0xFFC1E8F2)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF10140F)
val OnBackgroundDark = Color(0xFFE1E3DE)
val SurfaceVariantDark = Color(0xFF414942)
val OnSurfaceVariantDark = Color(0xFFC0C9BF)
val OutlineDark = Color(0xFF8B938C)

// --- Palette sémantique (tendances de santé) ---
// Une hausse n'est pas toujours une amélioration : ces couleurs suivent MetricTrend.isImprovement,
// jamais le sens brut de la variation.
val ImprovementLight = Color(0xFF1E7D4A)
val ImprovementDark = Color(0xFF80D9A8)
val DegradationLight = Color(0xFFB3590A)
val DegradationDark = Color(0xFFFFB870)
val NeutralTrendLight = Color(0xFF5C6360)
val NeutralTrendDark = Color(0xFFB9C0BB)
