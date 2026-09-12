package com.kmt.healthanalyzer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le contraste de la coquille native, mesuré plutôt que supposé.
 *
 * La règle du § 10 des specs — 4,5:1 sur tout texte — a déjà été vérifiée sur
 * `report.css`, où trois valeurs sont tombées sous le seuil et ont été corrigées. La
 * moitié Compose partage la même palette, mais pas les mêmes associations : Material
 * pose ses propres couples `couleur` / `onCouleur`, et une paire juste dans la feuille de
 * style peut être fausse une fois montée dans un `ColorScheme`.
 *
 * Ce test relit les couples que Material garantit et les mesure un par un, dans les deux
 * thèmes. Il ne remplace pas l'œil : il attrape ce que l'œil laisse passer, c'est-à-dire
 * un gris à peine trop clair sur un fond à peine trop clair.
 *
 * Les paires d'aplats colorés — `primaryContainer` et son `onPrimaryContainer` — portent
 * ici du texte, donc elles suivent la même règle que le reste. Les bordures et les
 * séparateurs (`outline`, `outlineVariant`) en sont exclus : ce sont des traits, pas du
 * texte, et le seuil qui les concerne est celui des éléments non textuels, 3:1.
 */
class ThemeContrastTest {

    @Test
    fun `le theme clair porte du texte lisible sur chacun de ses fonds`() {
        assertReadable(LightColors, "clair")
    }

    @Test
    fun `le theme sombre porte du texte lisible sur chacun de ses fonds`() {
        assertReadable(DarkColors, "sombre")
    }

    @Test
    fun `les couleurs de tendance restent lisibles sur le fond de leur theme`() {
        // Une hausse verte et une baisse orangée ne se distinguent pas en niveaux de
        // gris ; elles sont toujours doublées d'un signe ou d'un mot. Encore faut-il que
        // le texte lui-même se lise.
        val clair = listOf(
            "improvement" to ImprovementLight,
            "degradation" to DegradationLight,
            "neutral" to NeutralTrendLight,
        )
        clair.forEach { (nom, couleur) ->
            assertAtLeast(TEXT_RATIO, couleur, PaperLight, "clair : $nom sur le papier")
            assertAtLeast(TEXT_RATIO, couleur, SurfaceLight, "clair : $nom sur une carte")
        }

        val sombre = listOf(
            "improvement" to ImprovementDark,
            "degradation" to DegradationDark,
            "neutral" to NeutralTrendDark,
        )
        sombre.forEach { (nom, couleur) ->
            assertAtLeast(TEXT_RATIO, couleur, PaperDark, "sombre : $nom sur le papier")
            assertAtLeast(TEXT_RATIO, couleur, SurfaceDark, "sombre : $nom sur une carte")
        }
    }

    @Test
    fun `les traits de separation se voient, au seuil des elements non textuels`() {
        assertAtLeast(NON_TEXT_RATIO, AxisLight, PaperLight, "clair : l'axe sur le papier")
        assertAtLeast(NON_TEXT_RATIO, AxisDark, PaperDark, "sombre : l'axe sur le papier")
    }

    @Test
    fun `le calcul de contraste rend les valeurs de reference`() {
        // Sans cette vérification, un test de contraste faux au calcul déclarerait toute
        // la palette conforme, et resterait vert pour toujours.
        assertClose(21.0, contrast(Color.Black, Color.White), "noir sur blanc")
        assertClose(1.0, contrast(Color.White, Color.White), "blanc sur blanc")
        assertClose(4.54, contrast(Color(0xFF767676), Color.White), "le gris canonique de 4,5:1")
    }

    // ------------------------------------------------------------------ outils

    private fun assertReadable(scheme: ColorScheme, theme: String) {
        val paires = listOf(
            "onBackground sur background" to (scheme.onBackground to scheme.background),
            "onSurface sur surface" to (scheme.onSurface to scheme.surface),
            "onSurfaceVariant sur surface" to (scheme.onSurfaceVariant to scheme.surface),
            "onSurfaceVariant sur surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "onPrimary sur primary" to (scheme.onPrimary to scheme.primary),
            "onSecondary sur secondary" to (scheme.onSecondary to scheme.secondary),
            "onTertiary sur tertiary" to (scheme.onTertiary to scheme.tertiary),
            "onError sur error" to (scheme.onError to scheme.error),
            "onSurface sur surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
            "onSurface sur surfaceContainerHighest" to (scheme.onSurface to scheme.surfaceContainerHighest),
            "onSurfaceVariant sur surfaceContainer" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
        )

        paires.forEach { (nom, couple) ->
            val (encre, fond) = couple
            assertAtLeast(TEXT_RATIO, encre, fond, "$theme : $nom")
        }
    }

    /**
     * Les aplats translucides — `primaryContainer` et `errorContainer` sont posés à 8 %
     * d'opacité — se mesurent une fois composés sur le fond qui les porte, jamais seuls :
     * un `#144A3AA7` mesuré tel quel donnerait un résultat qui ne correspond à rien de
     * visible.
     */
    @Test
    fun `les aplats translucides se lisent une fois composes sur leur fond`() {
        val couples = listOf(
            Triple("clair : onPrimaryContainer", AccentLight to AccentSoftLight, PaperLight),
            Triple("sombre : onPrimaryContainer", AccentDark to AccentSoftDark, PaperDark),
            Triple("clair : onErrorContainer", CriticalLight to Color(0x14BE3131), PaperLight),
            Triple("sombre : onErrorContainer", CriticalDark to Color(0x1FE66767), PaperDark),
        )

        couples.forEach { (nom, paire, fond) ->
            val (encre, aplat) = paire
            assertAtLeast(TEXT_RATIO, encre, composite(aplat, fond), nom)
        }
    }

    private fun assertAtLeast(seuil: Double, encre: Color, fond: Color, nom: String) {
        val mesure = contrast(encre, fond)
        assertTrue(
            "$nom : ${format(mesure)}:1, sous le seuil de ${format(seuil)}:1 — " +
                "encre ${hex(encre)} sur fond ${hex(fond)}",
            mesure >= seuil - 0.005,
        )
    }

    private fun assertClose(attendu: Double, mesure: Double, nom: String) {
        assertTrue(
            "$nom : attendu ≈ $attendu, mesuré ${format(mesure)}",
            kotlin.math.abs(mesure - attendu) < 0.02,
        )
    }

    private companion object {
        /** Seuil WCAG AA pour du texte de taille courante. */
        const val TEXT_RATIO = 4.5

        /** Seuil WCAG AA pour un élément non textuel : trait, bordure, icône. */
        const val NON_TEXT_RATIO = 3.0

        /** Compose une couleur translucide sur un fond opaque. */
        fun composite(dessus: Color, dessous: Color): Color {
            val a = dessus.alpha
            return Color(
                red = dessus.red * a + dessous.red * (1 - a),
                green = dessus.green * a + dessous.green * (1 - a),
                blue = dessus.blue * a + dessous.blue * (1 - a),
            )
        }

        /** Luminance relative, telle que WCAG 2.1 la définit. */
        fun luminance(color: Color): Double {
            fun canal(v: Float): Double {
                val c = v.toDouble()
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * canal(color.red) + 0.7152 * canal(color.green) + 0.0722 * canal(color.blue)
        }

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        fun format(v: Double): String = String.format(java.util.Locale.FRANCE, "%.2f", v)

        fun hex(c: Color): String = "#%02X%02X%02X".format(
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt(),
        )
    }
}
