package com.kmt.healthanalyzer.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifie que le gabarit d'export ne peut jamais etre coupe en deux par une balise
 * `</script>` cachee dans les donnees, et que les trois marqueurs sont bien remplaces.
 */
class ReportExportTemplateTest {

    @Test
    fun `chaque inferieur devient la sequence unicode`() {
        val json = "{\"label\":\"</script><b>\"}"
        val expected = "{\"label\":\"\\u003c/script>\\u003cb>\"}"

        val escaped = ReportExportTemplate.escapeForInlineScript(json)

        assertFalse("le JSON echappe ne doit plus contenir de caractere '<' brut", escaped.contains("<"))
        assertEquals(expected, escaped)
    }

    @Test
    fun `un JSON sans inferieur traverse sans changement`() {
        val json = "{\"a\":1,\"b\":\"texte simple\"}"

        assertEquals(json, ReportExportTemplate.escapeForInlineScript(json))
    }

    @Test
    fun `fill remplace les trois marqueurs`() {
        val template = "<style>{{STYLES}}</style><script>{{SCRIPTS}}</script>{{MODEL}}"

        val filled = ReportExportTemplate.fill(
            template = template,
            styles = "body{margin:0}",
            scripts = "var HA = {};",
            reportJson = "{\"ok\":true}",
        )

        assertEquals(
            "<style>body{margin:0}</style><script>var HA = {};</script>{\"ok\":true}",
            filled,
        )
    }

    @Test
    fun `un JSON contenant le texte d'un marqueur ne perturbe pas les remplacements precedents`() {
        val template = "STYLES={{STYLES}} SCRIPTS={{SCRIPTS}} MODEL={{MODEL}}"

        val filled = ReportExportTemplate.fill(
            template = template,
            styles = "s",
            scripts = "j",
            reportJson = "{\"piege\":\"{{STYLES}} {{SCRIPTS}}\"}",
        )

        assertTrue(filled.endsWith("MODEL={\"piege\":\"{{STYLES}} {{SCRIPTS}}\"}"))
    }
}
