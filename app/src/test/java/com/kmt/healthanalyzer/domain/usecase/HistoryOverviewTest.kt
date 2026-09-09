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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [historyOverviewOf] et [describeHistoryOverview] alimentent `{{OVERVIEW}}` du prompt
 * système — voir `report/chat-prompt.txt`. Ces tests vérifient l'union des jours actifs
 * (pas de double compte), le regroupement par mois, et que le texte produit ne porte
 * jamais qu'un compte, jamais une valeur.
 */
class HistoryOverviewTest {

    @Test
    fun `un jour porteur de plusieurs mesures ne compte qu'une fois`() {
        val report = minimalReport(
            sleep = SleepSection(nightly = listOf(SleepNightPoint(date = "2025-01-10", hours = 7.0))),
            activity = ActivitySection(stepsDaily = listOf(DayValue(date = "2025-01-10", value = 8000.0))),
        )

        val overview = historyOverviewOf(report)

        assertEquals(1, overview.months.single().daysWithData)
    }

    @Test
    fun `regroupe les jours actifs par mois, toutes sections confondues`() {
        val report = minimalReport(
            sleep = SleepSection(
                nightly = listOf(
                    SleepNightPoint(date = "2025-01-05", hours = 7.0),
                    SleepNightPoint(date = "2025-01-06", hours = 6.5),
                ),
            ),
            heart = HeartSection(restingDaily = listOf(DayValue(date = "2025-02-01", value = 54.0))),
        )

        val overview = historyOverviewOf(report)

        assertEquals(
            listOf(MonthCoverage("2025-01", 2), MonthCoverage("2025-02", 1)),
            overview.months,
        )
    }

    @Test
    fun `earliest et latest portent le premier et le dernier jour actif, pas la periode interrogee`() {
        val report = minimalReport(
            heart = HeartSection(
                restingDaily = listOf(
                    DayValue(date = "2024-03-15", value = 54.0),
                    DayValue(date = "2025-06-02", value = 58.0),
                ),
            ),
        )

        val overview = historyOverviewOf(report)

        assertEquals("2024-03-15", overview.earliest)
        assertEquals("2025-06-02", overview.latest)
    }

    @Test
    fun `un rapport vide rend un apercu vide`() {
        val overview = historyOverviewOf(minimalReport())

        assertTrue(overview.months.isEmpty())
        assertNull(overview.earliest)
        assertNull(overview.latest)
    }

    @Test
    fun `describeHistoryOverview ne porte qu'un compte de jours, jamais une valeur`() {
        val report = minimalReport(
            sleep = SleepSection(
                nightly = listOf(
                    SleepNightPoint(date = "2025-01-05", hours = 7.0, score = 88),
                ),
            ),
        )

        val text = describeHistoryOverview(historyOverviewOf(report))

        assertTrue(text.contains("Historique disponible : du 2025-01-05 au 2025-01-05."))
        assertTrue(text.contains("- 2025-01 : 1 jours"))
        assertFalse("le score ne doit jamais sortir dans l'aperçu", text.contains("88"))
    }

    @Test
    fun `describeHistoryOverview dit explicitement l'absence de donnees`() {
        val text = describeHistoryOverview(historyOverviewOf(minimalReport()))

        assertTrue(text.contains("Aucune donnée"))
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
            from = "2020-01-01",
            to = "2026-09-08",
            days = 1,
            nights = 0,
            heartRateSamples = 0,
            hrvWindows = 0,
            activeDays = 0,
            timeZone = "Europe/Paris",
            periodLabel = "tout l'historique",
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
