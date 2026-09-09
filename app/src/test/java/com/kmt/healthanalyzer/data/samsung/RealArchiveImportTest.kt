package com.kmt.healthanalyzer.data.samsung

import com.kmt.healthanalyzer.domain.analysis.HealthAggregator
import com.kmt.healthanalyzer.domain.analysis.HealthPromptBuilder
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Vérifie l'importateur sur une archive Samsung Health réelle.
 *
 * Le test ne s'exécute que si la variable d'environnement `SAMSUNG_EXPORT_ZIP` désigne
 * une archive existante. Sans elle, JUnit l'ignore. Aucune donnée personnelle n'entre
 * donc dans le dépôt.
 *
 * Lancement : `SAMSUNG_EXPORT_ZIP=/chemin/export.zip ./gradlew testDebugUnitTest`
 */
class RealArchiveImportTest {

    private val archive: File? = System.getenv("SAMSUNG_EXPORT_ZIP")?.let(::File)

    @Test
    fun `imports a real export without losing records`() = runTest {
        assumeTrue("SAMSUNG_EXPORT_ZIP n'est pas défini", archive?.isFile == true)
        val file = archive!!
        val sink = RecordingSink()

        val finished = SamsungHealthImporter(sink, ZipExportSource(file))
            .import()
            .last() as ImportProgress.Finished

        println("--- Bilan de l'import ---")
        finished.summary.recordsByType.entries
            .sortedByDescending { it.value }
            .forEach { println("%-55s %6d".format(it.key, it.value)) }
        println("Période : ${finished.summary.firstDay} -> ${finished.summary.lastDay}")
        println("Avertissements : ${finished.summary.warnings}")

        assertTrue("Aucun enregistrement importé", finished.summary.totalRecords > 0)
        assertTrue("L'import signale des erreurs : ${finished.summary.warnings}", finished.summary.warnings.isEmpty())
        assertTrue("Les nuits de sommeil manquent", sink.sleepNights.isNotEmpty())
        assertTrue("Les mesures cardiaques manquent", sink.heartRates.isNotEmpty())
        assertTrue("Les pas quotidiens manquent", sink.dailySteps.isNotEmpty())
        assertTrue("La HRV manque", sink.hrv.isNotEmpty())

        // Aucun doublon : chaque identifiant Samsung n'apparaît qu'une fois.
        assertTrue(sink.sleepNights.map { it.id }.toSet().size == sink.sleepNights.size)
        assertTrue(sink.heartRates.map { it.id }.toSet().size == sink.heartRates.size)
        assertTrue(sink.dailySteps.map { it.date }.toSet().size == sink.dailySteps.size)
    }

    @Test
    fun `shows exactly what would leave the device for the llm`() = runTest {
        assumeTrue("SAMSUNG_EXPORT_ZIP n'est pas défini", archive?.isFile == true)
        val file = archive!!
        val sink = RecordingSink()

        SamsungHealthImporter(sink, ZipExportSource(file)).import().last()

        val zone = ZoneId.of("Europe/Paris")
        val days = sink.dailySteps.map { it.date } + sink.sleepNights.map { it.date }
        val snapshot = HealthAggregator(zone).aggregate(
            range = days.min()..days.max(),
            dailySteps = sink.dailySteps,
            dailyActivities = sink.dailyActivities,
            sleepNights = sink.sleepNights,
            heartRates = sink.heartRates,
            stress = sink.stress,
            hrv = sink.hrv,
            spO2 = sink.spO2,
            bodyCompositions = sink.bodyCompositions,
            energyScores = sink.energyScores,
        )

        val builder = HealthPromptBuilder()
        val prompt = builder.userPrompt(snapshot, question = null)
        System.getenv("PROMPT_OUT")?.let { path ->
            java.io.File(path).writeText(prompt)
            java.io.File("$path.system").writeText(builder.systemPrompt())
        }
        println("=== Contenu envoyé au LLM (${prompt.length} caractères, ~${prompt.length / 4} jetons) ===")
        println(prompt.lines().take(45).joinToString("\n"))
        println("... (${prompt.lines().size} lignes au total)")

        assertTrue("Le prompt est trop lourd", prompt.length < 40_000)
        assertFalse("Un identifiant fuit", prompt.contains("-4", ignoreCase = false) && prompt.contains("uuid"))
    }
}
