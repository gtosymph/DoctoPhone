package com.kmt.healthanalyzer.domain.usecase

import com.kmt.healthanalyzer.domain.report.ActivitySection
import com.kmt.healthanalyzer.domain.report.BodySection
import com.kmt.healthanalyzer.domain.report.BreathingSection
import com.kmt.healthanalyzer.domain.report.DayValue
import com.kmt.healthanalyzer.domain.report.HeartSection
import com.kmt.healthanalyzer.domain.report.ReportMeta
import com.kmt.healthanalyzer.domain.report.ReportModel
import com.kmt.healthanalyzer.domain.report.SleepNightPoint
import com.kmt.healthanalyzer.domain.report.SleepSection
import com.kmt.healthanalyzer.domain.report.StressSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [describeAvailableSeries] duplique, côté Kotlin, le seuil de disponibilité de
 * `web/report/chart-catalog.js` (au moins deux points). Ces tests vérifient que la
 * disponibilité suit bien ce seuil, et que le texte produit ne porte jamais de valeur —
 * seulement la clé, le libellé et l'unité de chaque série.
 */
class ChatSeriesCatalogTest {

    @Test
    fun `une serie avec au moins deux points est annoncee avec sa cle, son libelle et son unite`() {
        val report = minimalReport(
            sleep = SleepSection(
                nightly = listOf(
                    SleepNightPoint(date = "2026-09-01", hours = 7.0),
                    SleepNightPoint(date = "2026-09-02", hours = 6.5),
                ),
            ),
        )

        val description = describeAvailableSeries(report)

        assertTrue(description.contains("- sleep.nightly.hours — Durée de sommeil par nuit (heures)"))
    }

    @Test
    fun `une serie avec moins de deux points n'est pas annoncee`() {
        val report = minimalReport(
            heart = HeartSection(restingDaily = listOf(DayValue(date = "2026-09-01", value = 54.0))),
        )

        val description = describeAvailableSeries(report)

        assertFalse(description.contains("heart.restingDaily"))
    }

    @Test
    fun `un champ facultatif ne compte que ses points non nuls`() {
        val report = minimalReport(
            sleep = SleepSection(
                nightly = listOf(
                    SleepNightPoint(date = "2026-09-01", hours = 7.0, score = 80),
                    SleepNightPoint(date = "2026-09-02", hours = 6.5, score = null),
                ),
            ),
        )

        val description = describeAvailableSeries(report)

        assertTrue(description.contains("sleep.nightly.hours"))
        assertFalse(description.contains("sleep.nightly.score"))
    }

    @Test
    fun `sans aucune serie disponible, le texte le dit explicitement`() {
        val description = describeAvailableSeries(minimalReport())

        assertTrue(description.contains("Aucune série n'est disponible"))
    }

    private fun minimalReport(
        sleep: SleepSection = SleepSection(),
        heart: HeartSection = HeartSection(),
        activity: ActivitySection = ActivitySection(),
        body: BodySection = BodySection(),
        stress: StressSection = StressSection(),
        breathing: BreathingSection = BreathingSection(),
    ): ReportModel = ReportModel(
        meta = ReportMeta(
            generatedAt = "2026-09-08T00:00:00Z",
            from = "2026-09-01",
            to = "2026-09-08",
            days = 7,
            nights = 7,
            heartRateSamples = 0,
            hrvWindows = 0,
            activeDays = 0,
            timeZone = "Europe/Paris",
            periodLabel = "7 jours",
        ),
        tiles = emptyList(),
        sleep = sleep,
        heart = heart,
        activity = activity,
        body = body,
        stress = stress,
        breathing = breathing,
        correlations = emptyList(),
    )
}
