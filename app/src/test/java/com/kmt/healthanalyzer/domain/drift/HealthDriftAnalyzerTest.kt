package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.report.ActivitySection
import com.kmt.healthanalyzer.domain.report.BodyPoint
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthDriftAnalyzerTest {

    private val analyzer = HealthDriftAnalyzer()
    private val today = LocalDate.of(2024, 3, 10)

    @Test
    fun `dette de sommeil detectee et un trou dans la semaine recente ne compte pas comme une nuit`() {
        // 25 nuits de référence à 7 h, largement avant la semaine récente.
        val baselineNights = (1..25).map { day ->
            SleepNightPoint(date = LocalDate.of(2024, 1, day).toString(), hours = 7.0)
        }
        // Semaine récente (4 au 10 mars) à 5 h, MAIS sans nuit le 6 mars : un trou, pas un
        // zéro. Six nuits mesurées sur les sept jours de la fenêtre.
        val recentDates = (4..10).map { day -> LocalDate.of(2024, 3, day) }.filter { it.dayOfMonth != 6 }
        val recentNights = recentDates.map { date -> SleepNightPoint(date = date.toString(), hours = 5.0) }

        val report = fixtureReport(sleep = SleepSection(nightly = baselineNights + recentNights))

        val result = analyzer.analyze(report, today)

        val sleepDrift = result.drifts.single { it.metric == DriftMetric.SLEEP_DURATION }
        assertEquals(DriftKind.MEAN_SHIFT, sleepDrift.kind)
        assertEquals(25, sleepDrift.baselineDays)
        // Six nuits mesurées, pas sept : le trou du 6 mars n'a jamais été compté.
        assertEquals(6, sleepDrift.recentDays)
        assertEquals(7.0, sleepDrift.baselineMean, 1e-9)
        assertEquals(5.0, sleepDrift.recentMean, 1e-9)
    }

    @Test
    fun `le poids se lit sur les pesees reelles, pas sur un report force par jour`() {
        // Trois pesées seulement dans toute la fenêtre : une par semaine, ce qui est
        // réaliste. Sans passer par le report carry-forward de HealthAggregator, seules
        // ces dates comptent — jamais sept lignes recopiées pour une seule pesée.
        val baselineWeights = (1..3).map { week ->
            BodyPoint(date = LocalDate.of(2024, 1, week * 7).toString(), weightKg = 80.0)
        }
        val recentWeight = BodyPoint(date = LocalDate.of(2024, 3, 5).toString(), weightKg = 80.0)

        val report = fixtureReport(body = BodySection(daily = baselineWeights + recentWeight))

        val result = analyzer.analyze(report, today)

        // Une seule pesée récente est très en dessous du minimum de jours mesurés : aucune
        // dérive de poids ne doit être signalée, faute de données suffisantes.
        assertFalse(result.drifts.any { it.metric == DriftMetric.WEIGHT })
    }

    @Test
    fun `aucune section porteuse de donnees ne produit un rapport sans derive`() {
        val report = fixtureReport()

        val result = analyzer.analyze(report, today)

        assertTrue(result.drifts.isEmpty())
        assertFalse(result.hasDrift)
    }

    @Test
    fun `les pas quotidiens utilisent bien les jours reellement mesures`() {
        val baselineSteps = (1..25).map { day ->
            DayValue(date = LocalDate.of(2024, 1, day).toString(), value = 9000.0)
        }
        val recentSteps = (4..10).map { day ->
            DayValue(date = LocalDate.of(2024, 3, day).toString(), value = 3000.0)
        }

        val report = fixtureReport(activity = ActivitySection(stepsDaily = baselineSteps + recentSteps))

        val result = analyzer.analyze(report, today)

        val stepsDrift = result.drifts.single { it.metric == DriftMetric.STEPS }
        assertEquals(9000.0, stepsDrift.baselineMean, 1e-9)
        assertEquals(3000.0, stepsDrift.recentMean, 1e-9)
    }

    @Test
    fun `un DayValue a valeur nulle n'entre ni dans la moyenne ni dans le compte de jours`() {
        // Contrairement aux nuits ou aux pesées (des trous se traduisent par une absence
        // d'entrée), une série DayValue peut porter une valeur explicitement nulle pour un
        // jour présent dans la liste. Ce trou-là ne doit pas plus se lire comme une baisse.
        val baselineWithHole = (1..30).map { day ->
            val date = LocalDate.of(2024, 1, day).toString()
            // Un jour sur cinq n'a aucune mesure (valeur nulle), comme un capteur débranché.
            DayValue(date = date, value = if (day % 5 == 0) null else 54.0)
        }
        val recent = (4..10).map { day ->
            DayValue(date = LocalDate.of(2024, 3, day).toString(), value = 59.0)
        }

        val report = fixtureReport(heart = HeartSection(restingDaily = baselineWithHole + recent))

        val result = analyzer.analyze(report, today)

        val heartDrift = result.drifts.single { it.metric == DriftMetric.RESTING_HEART_RATE }
        // 6 des 30 jours de référence sont des trous : seuls les 24 jours mesurés comptent,
        // et la moyenne reste 54 (pas tirée vers le bas par des trous lus comme des zéros).
        assertEquals(24, heartDrift.baselineDays)
        assertEquals(54.0, heartDrift.baselineMean, 1e-9)
    }

    private fun fixtureReport(
        sleep: SleepSection = SleepSection(),
        heart: HeartSection = HeartSection(),
        activity: ActivitySection = ActivitySection(),
        body: BodySection = BodySection(),
    ): ReportModel = ReportModel(
        meta = ReportMeta(
            generatedAt = "2024-03-10T00:00:00Z",
            from = "2024-01-01",
            to = "2024-03-10",
            days = 70,
            nights = 0,
            heartRateSamples = 0,
            hrvWindows = 0,
            activeDays = 0,
            timeZone = "UTC",
            periodLabel = "1 janv. 2024 - 10 mars 2024",
        ),
        tiles = emptyList(),
        sleep = sleep,
        heart = heart,
        activity = activity,
        body = body,
        stress = StressSection(),
        breathing = BreathingSection(),
        correlations = emptyList(),
    )
}
