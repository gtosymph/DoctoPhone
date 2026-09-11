package com.kmt.healthanalyzer.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste la préparation des polices pour le rapport exporté.
 *
 * `report.css` déclare ses quatre fontes par chemin relatif (`fonts/…woff2`). C'est juste
 * pour la version web et pour la WebView Android, qui servent toutes deux un dossier. Ce
 * n'est plus vrai de l'export : c'est un fichier unique, souvent recopié ailleurs, sans
 * aucun dossier à côté. Un chemin relatif y échoue en silence et le rapport retombe sur la
 * police du système — sans le moindre message.
 *
 * [ReportExportTemplate.inlineExportFonts] coupe donc la zone bornée de la feuille et la
 * remplace par la seule fonte dont l'export a besoin, en `data:`. Une seule, car seuls les
 * titres sont en serif et ils sont tous en 600 : embarquer les quatre tripleraiterait le poids
 * du fichier pour rien.
 */
class ReportExportFontsTest {

    private val base64 = "T1RUTwAKgAADAFBDRkY"

    private val styles = """
        /* ha-font-faces:start */
        @font-face {
          font-family: "Source Serif 4";
          src: url("fonts/source-serif-4-400.woff2") format("woff2");
          font-weight: 400;
        }
        @font-face {
          font-family: "Source Serif 4";
          src: url("fonts/source-serif-4-600.woff2") format("woff2");
          font-weight: 600;
        }
        @font-face {
          font-family: "Public Sans";
          src: url("fonts/public-sans-400.woff2") format("woff2");
          font-weight: 400;
        }
        /* ha-font-faces:end */
        :root { --sans: "Public Sans", sans-serif; --page: #f9f9f7; }
        .ha-report { font: 400 16px/1.55 var(--sans); }
    """.trimIndent()

    @Test
    fun `la serif part dans le fichier, en data-uri`() {
        val result = ReportExportTemplate.inlineExportFonts(styles, base64)

        assertTrue(
            "La fonte serif doit voyager dans le fichier : sans elle, les titres de " +
                "l'export retombent sur la police du système, et l'identité du document " +
                "disparaît là où elle compte le plus, sur le papier remis au médecin.",
            result.contains("url(data:font/woff2;base64,$base64)"),
        )
        assertTrue(result.contains("font-family: \"Source Serif 4\""))
        assertTrue("La seule graisse embarquée est la 600.", result.contains("font-weight: 600"))
    }

    @Test
    fun `plus aucun chemin relatif ne subsiste`() {
        val result = ReportExportTemplate.inlineExportFonts(styles, base64)

        assertFalse(
            "Un `url(\"fonts/…\")` restant dans l'export pointe vers un dossier qui " +
                "n'existe pas à côté du fichier. La requête échoue sans message et la " +
                "fonte manquante passe inaperçue jusqu'à l'impression.",
            result.contains("fonts/"),
        )
    }

    @Test
    fun `le texte courant bascule sur une pile systeme`() {
        val result = ReportExportTemplate.inlineExportFonts(styles, base64)

        val lastSansDeclaration = Regex("--sans:\\s*([^;]+);")
            .findAll(result)
            .last()
            .groupValues[1]

        assertFalse(
            "La sans n'est pas embarquée — c'est le compromis de poids assumé. La dernière " +
                "définition de `--sans` doit donc être la pile système, sinon le navigateur " +
                "cherche « Public Sans » dans le vide avant de se rabattre.",
            lastSansDeclaration.contains("Public Sans"),
        )
        assertTrue(lastSansDeclaration.contains("-apple-system"))
    }

    @Test
    fun `le reste de la feuille passe intact`() {
        val result = ReportExportTemplate.inlineExportFonts(styles, base64)

        assertTrue(result.contains("--page: #f9f9f7;"))
        assertTrue(result.contains(".ha-report { font: 400 16px/1.55 var(--sans); }"))
    }

    @Test
    fun `une borne manquante arrete l'export au lieu de le publier sans polices`() {
        val withoutSentinels = styles.replace("/* ha-font-faces:start */", "")

        val failure = assertThrows(IllegalStateException::class.java) {
            ReportExportTemplate.inlineExportFonts(withoutSentinels, base64)
        }

        assertTrue(
            "Le message doit nommer la borne, sinon la cause est introuvable : l'export " +
                "produit se lit très bien, il a seulement perdu ses polices.",
            failure.message.orEmpty().contains("ha-font-faces"),
        )
    }

    @Test
    fun `les bornes inversees sont refusees aussi`() {
        val inverted = "/* ha-font-faces:end */\n@font-face {}\n/* ha-font-faces:start */"

        assertThrows(IllegalStateException::class.java) {
            ReportExportTemplate.inlineExportFonts(inverted, base64)
        }
    }

    @Test
    fun `le base64 vide est refuse plutot que d'ecrire une data-uri creuse`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ReportExportTemplate.inlineExportFonts(styles, "")
        }

        assertEquals(true, failure.message.orEmpty().isNotBlank())
    }
}
