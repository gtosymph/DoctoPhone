package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.report.ReportBuilder
import com.kmt.healthanalyzer.domain.report.ReportInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Vérifie la frontière de confidentialité de [HealthPromptBuilder.narrativeUserPrompt].
 *
 * Complète [HealthPromptBuilderTest], qui couvre l'ancien prompt (`userPrompt`) : ce
 * fichier-ci couvre le nouveau (le récit du rapport). Les deux gardent la même règle —
 * seuls des agrégats quittent l'appareil — sur deux prompts différents.
 */
class HealthPromptBuilderNarrativeTest {

    private val zone = ZoneOffset.UTC
    private val promptBuilder = HealthPromptBuilder()
    private val reportBuilder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2025, 1, 1).plusDays(n.toLong())
    private fun at(n: Int, hour: Int): Instant = day(n).atTime(hour, 0).toInstant(zone)

    private fun night(n: Int) = SleepNight(
        id = "n-$n",
        date = day(n),
        bedTime = day(n - 1).atTime(23, 0).toInstant(zone),
        wakeTime = day(n).atTime(7, 0).toInstant(zone),
        durationMinutes = 480,
        localBedTime = LocalTime.of(23, 0),
    )

    private fun sampleInput() = ReportInput(
        range = day(0)..day(30),
        sleepNights = listOf(night(5), night(6), night(7)),
        heartRates = (0..25).map { HeartRateSample("hr-$it", at(5, it % 24), 55 + it % 10) },
        bloodPressure = listOf(BloodPressureReading(id = "bp-1", time = at(10, 8), systolic = 120, diastolic = 80)),
        bodyCompositions = listOf(BodyComposition(id = "w-1", time = at(15, 8), weightKg = 80f)),
    )

    @Test
    fun `never carries a daily series or an individual-measurement list, only aggregates`() {
        val model = reportBuilder.build(sampleInput())

        val prompt = promptBuilder.narrativeUserPrompt(model)

        // Recherche la clé JSON exacte (guillemet-nom-guillemet-deux-points), pas une
        // occurrence en valeur : "key":"bloodPressure" dans une tuile est autorisé,
        // "bloodPressure":[...] comme liste de mesures individuelles ne l'est pas.
        listOf(
            "nightly", "stepsDaily", "stepsRolling7", "restingDaily", "hrvDaily",
            "vitalityDaily", "spo2Daily", "respiratoryDaily", "skinTempDaily",
            "bloodPressure", "ecg", "daily",
        ).forEach { field ->
            assertFalse(
                "Le champ \"$field\" ne doit pas apparaître : c'est une série quotidienne ou une liste de mesures individuelles",
                prompt.contains("\"$field\":"),
            )
        }
    }

    @Test
    fun `never carries the exact date of an individual night, only its month`() {
        val model = reportBuilder.build(sampleInput())

        val prompt = promptBuilder.narrativeUserPrompt(model)

        // Les dates de nuit n'existent que dans sleep.nightly, exclu du prompt.
        assertFalse(prompt.contains("2025-01-06")) // date de réveil de night(5)
        assertFalse(prompt.contains("2025-01-07"))
        assertFalse(prompt.contains("2025-01-08"))
    }

    @Test
    fun `never carries a raw identifier or a precise timestamp`() {
        val model = reportBuilder.build(sampleInput())

        val prompt = promptBuilder.narrativeUserPrompt(model)

        assertFalse(prompt.contains("uuid", ignoreCase = true))
        assertFalse("Un identifiant d'enregistrement Samsung ne doit pas sortir", prompt.contains("\"n-5\""))
        assertFalse("Un identifiant d'enregistrement Samsung ne doit pas sortir", prompt.contains("\"hr-"))
        assertFalse("Un horodatage précis ne doit pas sortir", prompt.contains("T08:00:00"))
    }

    @Test
    fun `still carries the day-level date of a rare measurement, via the kpi and the tiles`() {
        // La date au jour près d'une mesure rare reste autorisée (elle ne révèle rien de
        // plus que la période déjà envoyée) : c'est ce qui permet au récit de dire
        // « aucune pesée depuis deux mois ».
        val model = reportBuilder.build(sampleInput())

        val prompt = promptBuilder.narrativeUserPrompt(model)

        assertTrue(prompt.contains("2025-01-16")) // body.kpi.lastMeasuredOn (pesée du 15)
    }

    @Test
    fun `stays well under the token budget for two years of data`() {
        val twoYears = ReportInput(
            range = day(1)..day(730),
            sleepNights = (1..730).map { night(it) },
            heartRates = (1..730).flatMap { d -> (0 until 25).map { HeartRateSample("hr-$d-$it", at(d, it % 24), 55 + it % 10) } },
        )

        val model = reportBuilder.build(twoYears)
        val prompt = promptBuilder.narrativeUserPrompt(model)

        // La version web tient environ 2 690 jetons sur deux ans de données (~4 caractères
        // par jeton, tableaux mensuels plafonnés à 24 mois) : viser le même ordre de
        // grandeur, avec une marge confortable.
        assertTrue("Le prompt fait ${prompt.length} caractères", prompt.length < 15_000)
    }

    @Test
    fun `caps monthly aggregates to the last 24 months, even over many years of history`() {
        val fiveYears = ReportInput(
            range = day(1)..day(1826), // environ 5 ans
            sleepNights = (1..1826).map { night(it) },
        )
        val model = reportBuilder.build(fiveYears)
        assertTrue(
            "Ce test suppose que le rapport complet, lui, n'est pas plafonné : ${model.sleep.monthly.size} mois",
            model.sleep.monthly.size > 24,
        )

        val input = narrativeInputOf(model)

        assertEquals(24, input.sleep.monthly.size)
    }
}
