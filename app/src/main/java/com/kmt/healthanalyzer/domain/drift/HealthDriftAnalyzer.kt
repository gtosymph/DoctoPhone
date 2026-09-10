package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.report.DayValue
import com.kmt.healthanalyzer.domain.report.ReportModel
import java.time.Instant
import java.time.LocalDate

/**
 * Construit un [DriftReport] à partir d'un [ReportModel] déjà calculé.
 *
 * Classe pure : aucune dépendance à Room ni à Android, à l'image de
 * [com.kmt.healthanalyzer.domain.report.ReportBuilder]. Elle ne recalcule rien — elle
 * relit les séries déjà produites par `ReportBuilder*` (sommeil, cœur, activité, corps) et
 * les partage en deux fenêtres avant de les passer à [DriftDetector].
 *
 * Réutiliser [ReportModel] plutôt que d'interroger Room directement a un avantage précis :
 * ses séries quotidiennes ne portent que des jours **réellement mesurés**. Le poids, par
 * exemple, ne serait pas utilisable via `HealthAggregator` — qui reporte la dernière pesée
 * connue sur les jours sans mesure (voir sa documentation) — car une seule pesée en début
 * de semaine récente se retrouverait comptée sept fois, faussant la moyenne et l'écart-type
 * de la fenêtre récente. [ReportModel.body] ne porte, lui, que les jours de pesée réels.
 */
class HealthDriftAnalyzer {

    fun analyze(report: ReportModel, today: LocalDate): DriftReport {
        val recentStart = today.minusDays((DriftDetector.RECENT_WINDOW_DAYS - 1).toLong())

        val drifts = buildList {
            val sleepHours = report.sleep.nightly.map { DayValue(it.date, it.hours) }
            splitByDate(sleepHours, recentStart)
                .let { (baseline, recent) -> DriftDetector.detectShift(DriftMetric.SLEEP_DURATION, baseline, recent) }
                ?.let(::add)

            val bedRel = report.sleep.nightly.mapNotNull { night -> night.bedRel?.let { DayValue(night.date, it) } }
            splitByDate(bedRel, recentStart)
                .let { (baseline, recent) ->
                    DriftDetector.detectSpreadIncrease(DriftMetric.SLEEP_REGULARITY, baseline, recent)
                }
                ?.let(::add)

            splitByDate(report.heart.restingDaily, recentStart)
                .let { (baseline, recent) -> DriftDetector.detectShift(DriftMetric.RESTING_HEART_RATE, baseline, recent) }
                ?.let(::add)

            splitByDate(report.heart.hrvDaily, recentStart)
                .let { (baseline, recent) -> DriftDetector.detectShift(DriftMetric.HRV, baseline, recent) }
                ?.let(::add)

            splitByDate(report.activity.stepsDaily, recentStart)
                .let { (baseline, recent) -> DriftDetector.detectShift(DriftMetric.STEPS, baseline, recent) }
                ?.let(::add)

            val weight = report.body.daily.map { DayValue(it.date, it.weightKg) }
            splitByDate(weight, recentStart)
                .let { (baseline, recent) -> DriftDetector.detectShift(DriftMetric.WEIGHT, baseline, recent) }
                ?.let(::add)
        }

        return DriftReport(generatedAt = Instant.now(), drifts = drifts)
    }

    /**
     * Sépare une série `date -> valeur` en deux listes de valeurs non nulles : avant
     * [recentStart] (la référence) et à partir de [recentStart] (la semaine récente). Une
     * valeur `null` — un trou — n'entre dans aucune des deux, ce qui est tout le point : un
     * trou de données ne doit jamais se lire comme une baisse.
     */
    private fun splitByDate(points: List<DayValue>, recentStart: LocalDate): Pair<List<Double>, List<Double>> {
        val baseline = ArrayList<Double>()
        val recent = ArrayList<Double>()
        for (point in points) {
            val value = point.value ?: continue
            val date = LocalDate.parse(point.date)
            if (date < recentStart) baseline.add(value) else recent.add(value)
        }
        return baseline to recent
    }
}
