package com.kmt.healthanalyzer.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HealthPromptBuilderTest {

    private val builder = HealthPromptBuilder()

    private fun day(n: Int, steps: Int? = 5000, sleep: Int? = 400) = DailySnapshot(
        date = LocalDate.of(2026, 3, 1).plusDays(n.toLong()),
        steps = steps,
        sleepMinutes = sleep,
        sleepScore = 62,
        restingHeartRate = 56,
        averageStress = 34,
        hrvRmssd = 72f,
        weightKg = 83.9f,
        energyScore = 80,
    )

    private fun snapshot(days: List<DailySnapshot>) = HealthSnapshot(
        from = days.first().date,
        to = days.last().date,
        days = days,
        trends = listOf(
            MetricTrend(HealthMetric.STEPS, 7028.0, 5002.0, TrendDirection.UP, 40.5),
            MetricTrend(HealthMetric.SLEEP_DURATION, 242.8, 296.0, TrendDirection.DOWN, -18.0),
        ),
        sleepRegularity = SleepRegularity(
            averageBedtime = LocalTime.of(2, 0),
            bedtimeSpreadHours = 3.24,
            averageDurationMinutes = 309,
            sleepDebtMinutes = 12_000,
            targetMinutes = 450,
            nightsMeasured = 361,
        ),
    )

    @Test
    fun `states that the assistant is not a doctor in the system prompt`() {
        val prompt = builder.systemPrompt()

        assertTrue(prompt.contains("diagnostic", ignoreCase = true))
        assertTrue(prompt.contains("médecin", ignoreCase = true))
    }

    @Test
    fun `asks the model to answer in french`() {
        assertTrue(builder.systemPrompt().contains("français", ignoreCase = true))
    }

    @Test
    fun `summarises weeks instead of listing every day`() {
        val days = (0..89).map { day(it) }

        val prompt = builder.userPrompt(snapshot(days), question = null)

        // 90 jours ne doivent pas produire 90 lignes : le résumé est hebdomadaire.
        assertTrue(prompt.lines().size < 60)
        assertTrue(prompt.contains("Semaine", ignoreCase = true))
    }

    @Test
    fun `carries the trends with their direction`() {
        val prompt = builder.userPrompt(snapshot((0..27).map { day(it) }), question = null)

        assertTrue(prompt.contains("Pas"))
        assertTrue(prompt.contains("7028") || prompt.contains("7 028"))
        assertTrue(prompt.contains("hausse", ignoreCase = true))
        assertTrue(prompt.contains("baisse", ignoreCase = true))
    }

    @Test
    fun `carries the sleep regularity summary`() {
        val prompt = builder.userPrompt(snapshot((0..27).map { day(it) }), question = null)

        assertTrue(prompt.contains("02:00"))
        assertTrue(prompt.contains("3,2") || prompt.contains("3.2"))
    }

    @Test
    fun `never carries a raw identifier or a timestamp`() {
        val prompt = builder.userPrompt(snapshot((0..27).map { day(it) }), question = null)

        assertFalse("Aucun identifiant Samsung ne doit sortir", prompt.contains("uuid", ignoreCase = true))
        assertFalse("Aucun horodatage à la milliseconde ne doit sortir", prompt.contains(".000"))
    }

    @Test
    fun `appends the user question when there is one`() {
        val prompt = builder.userPrompt(snapshot((0..6).map { day(it) }), question = "Pourquoi je dors mal ?")

        assertTrue(prompt.contains("Pourquoi je dors mal ?"))
    }

    @Test
    fun `skips a metric that carries no data at all`() {
        val days = (0..13).map { day(it, steps = null) }

        val prompt = builder.userPrompt(
            snapshot(days).copy(trends = emptyList()),
            question = null,
        )

        assertFalse(prompt.contains("Pas :"))
    }

    @Test
    fun `stays well under the token budget for two years of data`() {
        val days = (0..729).map { day(it) }

        val prompt = builder.userPrompt(snapshot(days), question = null)

        // Environ 4 caractères par jeton : 40 000 caractères font environ 10 000 jetons.
        assertTrue("Le prompt fait ${prompt.length} caractères", prompt.length < 40_000)
    }

    @Test
    fun `reports the observed period and the data coverage`() {
        val days = (0..13).map { day(it) }

        val prompt = builder.userPrompt(snapshot(days), question = null)

        assertTrue(prompt.contains("2026-03-01"))
        assertTrue(prompt.contains("14"))
    }

    @Test
    fun `formats a duration in hours and minutes`() {
        assertEquals("5 h 09", formatMinutes(309))
        assertEquals("0 h 45", formatMinutes(45))
    }
}
