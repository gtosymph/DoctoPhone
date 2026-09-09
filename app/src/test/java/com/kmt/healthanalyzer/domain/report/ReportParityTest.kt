package com.kmt.healthanalyzer.domain.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Vérifie que [ReportBuilder] produit un modèle correct sur le jeu d'essai partagé.
 *
 * Ce test écrit sa sortie dans `build/parity/android.json`. La comparaison caractère
 * pour caractère avec `web/test/out/web.json` (produit par `web/test/parity.js` à partir
 * du même fichier `shared-fixtures/parity-input.json`) se fait hors de ce test : les deux
 * runtimes (JVM et Node) ne cohabitent pas dans une seule commande Gradle. Ce test-ci
 * couvre la partie qui lui revient : le fichier se lit, `ReportBuilder` ne plante pas, et
 * les invariants documentés dans `shared-fixtures/README.md` tiennent (36 nuits
 * fractionnées fusionnées, coucher toujours renseigné même sans `localBedTime`, etc.).
 *
 * S'ignore silencieusement si `shared-fixtures/parity-input.json` est introuvable — ne
 * doit arriver qu'en cas de mauvaise disposition du dépôt.
 */
class ReportParityTest {

    private val fixtureFile = findParityFixtureFile()
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    @Test
    fun `builds a report from the shared fixture and writes it for the JavaScript comparison`() {
        assumeTrue("shared-fixtures/parity-input.json introuvable", fixtureFile != null)
        val fixture = loadParityFixture(fixtureFile!!)

        val model = ReportBuilder(fixture.zone).build(fixture.input)

        val outputFile = File("build/parity/android.json")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json.encodeToString(model))
        println("Rapport de parité écrit dans ${outputFile.absolutePath}")

        // --- Les 36 nuits fractionnées (166 nuits distinctes sur 202 sessions) sont fusionnées. ---
        assertEquals(166, model.sleep.kpi.nights)
        assertEquals(166, model.sleep.nightly.size)

        // --- Une nuit sur deux n'a pas localBedTime (simulant Health Connect) : le repli sur ---
        // --- bedTime doit s'appliquer partout, aucun bedRel ne doit manquer. ---
        assertTrue("bedRel manque pour au moins une nuit", model.sleep.nightly.all { it.bedRel != null })
        assertTrue(model.sleep.kpi.bedMedian != null)
        assertTrue(model.sleep.kpi.bedSpreadHours != null)

        // --- Les huit tuiles, dans leur ordre stable. ---
        assertEquals(
            listOf("sleep", "bedtime", "restingHeartRate", "hrv", "bodyMassIndex", "steps30", "bloodPressure", "stress"),
            model.tiles.map { it.key },
        )

        // --- Les six corrélations, sur 180 jours de données synthétiques : toutes doivent ---
        // --- dépasser le seuil minimal de paires (30) et donc apparaître. ---
        assertEquals(
            setOf(
                "Durée de sommeil et FC de repos, le même jour",
                "Durée de sommeil et variabilité cardiaque",
                "Pas de la veille vers la durée de sommeil",
                "Pas de la veille vers le score de sommeil",
                "Heure de coucher et durée de sommeil",
                "Durée de sommeil vers le stress du lendemain",
            ),
            model.correlations.map { it.label }.toSet(),
        )
        assertEquals(6, model.correlations.size)

        // --- Les sections aux mesures rares ne doivent pas planter, ni inventer une tendance. ---
        assertEquals(2, model.heart.bloodPressure.size)
        assertEquals(2, model.heart.ecg.size)
        assertTrue(model.sleep.kpi.apneaResult != null)

        // --- Les 32 mesures cardiaques/jour dépassent le seuil de 20 : la FC de repos existe. ---
        assertTrue(model.heart.kpi.restingMean != null)

        // --- Confidentialité : le modèle ne doit jamais fuiter vers HealthPromptBuilder. ---
        // (Couvert par HealthPromptBuilderTest ; rappel ici que ReportModel n'est pas son entrée.)
    }
}
