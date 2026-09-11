package com.kmt.healthanalyzer.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie que la palette de Compose et celle de `web/report/report.css` disent la même chose.
 *
 * Les deux moitiés de l'app se partagent le même écran : la coquille est native, le rapport
 * et la conversation vivent dans une WebView. Compose ne sait pas lire une variable CSS, donc
 * la palette existe forcément en double. Le doublon est assumé ; sa **dérive silencieuse**
 * ne l'est pas — une valeur corrigée d'un seul côté donne un fond légèrement différent
 * au-dessus et en dessous de la WebView, ce qui se remarque sans qu'on sache dire pourquoi.
 *
 * Ce test relit la feuille de style et compare jeton par jeton, pour les deux thèmes.
 * `app/build.gradle.kts` déclare `report.css` comme entrée des tâches de test : sans cela,
 * Gradle ne relancerait pas ce test quand seule la feuille change, et la garde serait muette
 * au moment exact où elle sert.
 *
 * Seules les couleurs opaques en hexadécimal sont comparées. Les `rgba()` — les variantes
 * `-soft`, `--ring`, `--band` — portent une transparence que Compose exprime autrement ;
 * les convertir ferait échouer le test sur une erreur d'arrondi, sans rien protéger de plus.
 */
class ThemeTokenParityTest {

    @Test
    fun `les jetons clairs de Compose valent ceux de report css`() {
        val tokens = lightTokens() ?: return

        assertToken(tokens, "--page", PaperLight)
        assertToken(tokens, "--surface", SurfaceLight)
        assertToken(tokens, "--ink", InkLight)
        assertToken(tokens, "--ink-2", InkMutedLight)
        assertToken(tokens, "--grid", GridLight)
        assertToken(tokens, "--axis", AxisLight)
        assertToken(tokens, "--accent", AccentLight)
        assertToken(tokens, "--accent-contrast", OnAccentLight)
        assertToken(tokens, "--critical", CriticalLight)
        assertToken(tokens, "--good-text", ImprovementLight)
        assertToken(tokens, "--muted", NeutralTrendLight)
    }

    @Test
    fun `les jetons sombres de Compose valent ceux de report css`() {
        val tokens = darkTokens() ?: return

        assertToken(tokens, "--page", PaperDark)
        assertToken(tokens, "--surface", SurfaceDark)
        assertToken(tokens, "--ink", InkDark)
        assertToken(tokens, "--ink-2", InkMutedDark)
        assertToken(tokens, "--grid", GridDark)
        assertToken(tokens, "--axis", AxisDark)
        assertToken(tokens, "--accent", AccentDark)
        assertToken(tokens, "--accent-contrast", OnAccentDark)
        assertToken(tokens, "--critical", CriticalDark)
        assertToken(tokens, "--muted", NeutralTrendDark)
    }

    @Test
    fun `les cinq couleurs de domaine valent celles de report css, en clair`() {
        val tokens = lightTokens() ?: return

        assertToken(tokens, "--d-sleep", LightDomainColors.sleep.base)
        assertToken(tokens, "--d-sleep-ink", LightDomainColors.sleep.ink)
        assertToken(tokens, "--d-heart", LightDomainColors.heart.base)
        assertToken(tokens, "--d-heart-ink", LightDomainColors.heart.ink)
        assertToken(tokens, "--d-activity", LightDomainColors.activity.base)
        assertToken(tokens, "--d-activity-ink", LightDomainColors.activity.ink)
        assertToken(tokens, "--d-body", LightDomainColors.body.base)
        assertToken(tokens, "--d-body-ink", LightDomainColors.body.ink)
        assertToken(tokens, "--d-vitality", LightDomainColors.vitality.base)
        assertToken(tokens, "--d-vitality-ink", LightDomainColors.vitality.ink)
    }

    @Test
    fun `les cinq couleurs de domaine valent celles de report css, en sombre`() {
        val tokens = darkTokens() ?: return

        assertToken(tokens, "--d-sleep", DarkDomainColors.sleep.base)
        assertToken(tokens, "--d-sleep-ink", DarkDomainColors.sleep.ink)
        assertToken(tokens, "--d-heart", DarkDomainColors.heart.base)
        assertToken(tokens, "--d-heart-ink", DarkDomainColors.heart.ink)
        assertToken(tokens, "--d-activity", DarkDomainColors.activity.base)
        assertToken(tokens, "--d-activity-ink", DarkDomainColors.activity.ink)
        assertToken(tokens, "--d-body", DarkDomainColors.body.base)
        assertToken(tokens, "--d-body-ink", DarkDomainColors.body.ink)
        assertToken(tokens, "--d-vitality", DarkDomainColors.vitality.base)
        assertToken(tokens, "--d-vitality-ink", DarkDomainColors.vitality.ink)
    }

    @Test
    fun `les deux blocs sombres de la feuille portent les memes valeurs`() {
        val css = readReportCss() ?: return

        // `report.css` décrit trois états de thème : clair, sombre système (via `@media`) et
        // sombre forcé (via `data-theme`). Les deux derniers doivent porter les mêmes
        // couleurs, sinon l'utilisateur qui bascule le thème à la main voit la palette
        // changer sous ses yeux.
        val media = parseTokens(css, "@media (prefers-color-scheme: dark) {")
        val forced = parseTokens(css, ":root[data-theme=\"dark\"] {")
        val shared = media.keys intersect forced.keys

        assertTrue("Aucun jeton commun trouvé : le découpage de la feuille a changé.", shared.size > 8)
        shared.forEach { name ->
            assertEquals(
                "Le jeton $name n'a pas la même valeur en sombre système et en sombre forcé.",
                media[name],
                forced[name],
            )
        }
    }

    @Test
    fun `les bornes que l'export utilise pour remplacer les polices sont bien la`() {
        val css = readReportCss() ?: return

        val start = css.indexOf("/* ha-font-faces:start */")
        val end = css.indexOf("/* ha-font-faces:end */")

        assertTrue(
            "La borne d'ouverture des règles @font-face a disparu de report.css. Les deux " +
                "exportateurs s'en servent pour remplacer les polices par des data-URI. " +
                "Voir ReportExportTemplate.inlineExportFonts.",
            start >= 0,
        )
        assertTrue("La borne de fermeture doit suivre celle d'ouverture.", end > start)
    }

    @Test
    fun `la feuille ne declare que les graisses 400 et 600`() {
        val css = readReportCss() ?: return

        val weights = Regex("""font-weight:\s*(\d{3})\s*;""")
            .findAll(css)
            .map { it.groupValues[1] }
            .toSortedSet()

        assertEquals(
            "Les fontes embarquées n'existent qu'en 400 et 600. Toute autre graisse est " +
                "synthétisée par le moteur de rendu, qui épaissit les contours : le résultat " +
                "est visiblement plus sale que la vraie graisse.",
            sortedSetOf("400", "600"),
            weights,
        )
    }

    // ------------------------------------------------------------------ outils

    private fun lightTokens(): Map<String, Color>? = readReportCss()?.let { parseTokens(it, ":root {") }

    private fun darkTokens(): Map<String, Color>? =
        readReportCss()?.let { parseTokens(it, ":root[data-theme=\"dark\"] {") }

    private fun assertToken(tokens: Map<String, Color>, name: String, expected: Color) {
        val actual = tokens[name]
        assertTrue(
            "Le jeton $name est absent de report.css, ou son nom a changé. Compose le " +
                "recopie dans Color.kt / DomainColors.kt : les deux doivent bouger ensemble.",
            actual != null,
        )
        assertEquals(
            "$name diverge entre report.css et Compose. La feuille de style est la " +
                "référence : reportez sa valeur dans Compose, jamais l'inverse.",
            actual,
            expected,
        )
    }

    /**
     * Lit les déclarations `--jeton: #rrggbb;` du bloc qui commence à [marker].
     *
     * Le bloc s'arrête à la première accolade fermante en début de ligne. C'est suffisant
     * ici : aucun de ces blocs ne contient de règle imbriquée, sauf celui du `@media`, dont
     * la règle interne est indentée et dont la fermeture indentée ne termine donc pas la
     * lecture trop tôt.
     */
    private fun parseTokens(css: String, marker: String): Map<String, Color> {
        val start = css.indexOf(marker)
        if (start < 0) return emptyMap()
        val end = css.indexOf("\n}", start).takeIf { it > start } ?: css.length

        return Regex("""(--[a-z0-9-]+):\s*#([0-9a-fA-F]{6})\s*;""")
            .findAll(css.substring(start, end))
            .associate { match ->
                match.groupValues[1] to Color(match.groupValues[2].toLong(16) or 0xFF000000L)
            }
    }

    private companion object {
        /**
         * Remonte depuis le répertoire courant du test jusqu'à la racine du dépôt.
         *
         * Rend `null` quand la feuille est introuvable — le cas d'un module compilé hors du
         * dépôt. Les tests s'arrêtent alors sans échouer : ils ne peuvent rien vérifier,
         * mais rien ne prouve non plus qu'il y ait un défaut.
         */
        fun readReportCss(): String? {
            var dir: File? = File(".").absoluteFile
            repeat(6) {
                val candidate = File(dir, "web/report/report.css")
                if (candidate.isFile) return candidate.readText()
                dir = dir?.parentFile
            }
            return null
        }
    }
}
