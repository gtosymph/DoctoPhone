package com.kmt.healthanalyzer.domain.usecase

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le catalogue de séries existe en deux exemplaires : ici en Kotlin, et dans
 * `web/report/chart-catalog.js`. Ce test empêche les deux de diverger en silence.
 *
 * L'enjeu n'est pas cosmétique. Le catalogue Kotlin sert à écrire la liste des séries
 * dans le prompt système ; le catalogue JavaScript sert à les résoudre au moment de
 * dessiner. Une série annoncée d'un côté mais absente de l'autre produit exactement le
 * défaut le plus déroutant : le modèle demande un graphique parfaitement légitime, et
 * l'application le refuse sans que personne ne comprenne pourquoi.
 *
 * Le test s'ignore si le fichier JavaScript est introuvable, pour ne pas casser une
 * exécution hors du dépôt.
 */
class ChatSeriesCatalogParityTest {

    @Test
    fun `les deux catalogues de series declarent exactement les memes cles`() {
        val jsFile = findChartCatalogFile()
        assumeTrue("web/report/chart-catalog.js est introuvable", jsFile != null)

        val javascriptKeys = KEY_PATTERN.findAll(jsFile!!.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        val kotlinKeys = catalogRefs().toSortedSet()

        assertEquals(
            "Le catalogue JavaScript et le catalogue Kotlin ne déclarent pas les mêmes séries. " +
                "Manquantes côté Kotlin : ${javascriptKeys - kotlinKeys}. " +
                "Manquantes côté JavaScript : ${kotlinKeys - javascriptKeys}.",
            javascriptKeys,
            kotlinKeys,
        )
    }

    private companion object {
        /** Les clés du catalogue JavaScript : une chaîne suivie de deux points, en début d'entrée. */
        val KEY_PATTERN = Regex("""^\s{4}'([a-zA-Z0-9.]+)':""", RegexOption.MULTILINE)

        /** Remonte depuis le répertoire courant du test jusqu'à la racine du dépôt. */
        fun findChartCatalogFile(): File? {
            var dir: File? = File(".").absoluteFile
            repeat(6) {
                val candidate = File(dir, "web/report/chart-catalog.js")
                if (candidate.isFile) return candidate
                dir = dir?.parentFile
            }
            return null
        }
    }
}
