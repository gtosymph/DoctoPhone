package com.kmt.healthanalyzer.domain.drift

import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import com.kmt.healthanalyzer.domain.report.DayValue
import com.kmt.healthanalyzer.domain.report.ReportModel
import java.time.Instant
import java.time.LocalDate

/**
 * Construit un [WeeklyReview] — et le [DriftReport] qu'on en extrait — à partir d'un
 * [ReportModel] déjà calculé.
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
 *
 * ## Un seul passage, deux lectures
 *
 * [review] fait tout le travail ; [analyze] n'en garde que les dérives. L'écran d'accueil
 * et `WeeklyDriftCheckWorker` lisent donc le même calcul, et ne peuvent pas se contredire.
 */
class HealthDriftAnalyzer {

    /** Le bilan complet de la semaine : les six mesures, mesurées ou non, et ce qui a bougé. */
    fun review(report: ReportModel, today: LocalDate): WeeklyReview {
        val recentStart = today.minusDays((DriftDetector.RECENT_WINDOW_DAYS - 1).toLong())

        val sleepHours = report.sleep.nightly.map { DayValue(it.date, it.hours) }
        val bedRel = report.sleep.nightly.mapNotNull { night -> night.bedRel?.let { DayValue(night.date, it) } }
        val weight = report.body.daily.map { DayValue(it.date, it.weightKg) }

        val metrics = listOf(
            summarize(DriftMetric.SLEEP_DURATION, sleepHours, recentStart, today),
            summarize(DriftMetric.SLEEP_REGULARITY, bedRel, recentStart, today),
            summarize(DriftMetric.RESTING_HEART_RATE, report.heart.restingDaily, recentStart, today),
            summarize(DriftMetric.HRV, report.heart.hrvDaily, recentStart, today),
            summarize(DriftMetric.STEPS, report.activity.stepsDaily, recentStart, today),
            summarize(DriftMetric.WEIGHT, weight, recentStart, today),
        )

        return WeeklyReview(
            generatedAt = Instant.now(),
            from = recentStart,
            to = today,
            metrics = metrics,
        )
    }

    /** Les seules dérives, pour l'écran de rapport et le bilan hebdomadaire en tâche de fond. */
    fun analyze(report: ReportModel, today: LocalDate): DriftReport {
        val review = review(report, today)
        return DriftReport(generatedAt = review.generatedAt, drifts = review.drifts)
    }

    /**
     * Résume une série pour une mesure : les deux fenêtres, la semaine jour par jour, et la
     * dérive s'il y en a une.
     *
     * La détection est déléguée à [DriftDetector], qui garde ses propres minimums et ses
     * propres seuils. Cette fonction ne décide de rien : elle découpe et elle compte.
     */
    private fun summarize(
        metric: DriftMetric,
        points: List<DayValue>,
        recentStart: LocalDate,
        today: LocalDate,
    ): MetricReview {
        val (baseline, recent) = splitByDate(points, recentStart)

        val drift = if (metric.measuresSpread) {
            DriftDetector.detectSpreadIncrease(metric, baseline, recent)
        } else {
            DriftDetector.detectShift(metric, baseline, recent)
        }

        return MetricReview(
            metric = metric,
            recentValue = representativeValue(metric, recent, metric.minRecentDaysToShow),
            baselineValue = representativeValue(metric, baseline, metric.minBaselineDaysToShow),
            recentDays = recent.size,
            baselineDays = baseline.size,
            lastMeasuredOn = lastMeasuredDate(points),
            week = weekOf(points, recentStart, today),
            drift = drift,
        )
    }

    /**
     * Le nombre à montrer pour une fenêtre : sa dispersion pour la régularité du coucher,
     * sa moyenne pour tout le reste.
     *
     * Rend `null` sous [minimumDays] — [DriftMetric.minRecentDaysToShow] ou
     * [DriftMetric.minBaselineDaysToShow] selon la fenêtre. Ces seuils sont plus bas que
     * ceux de [DriftDetector] : montrer une moyenne accompagnée de son nombre de jours
     * demande moins de preuves qu'affirmer un changement. En dessous, l'app se tait plutôt
     * que de donner à deux points l'autorité apparente de sept.
     */
    private fun representativeValue(metric: DriftMetric, values: List<Double>, minimumDays: Int): Double? {
        if (values.size < minimumDays) return null
        return if (metric.measuresSpread) SeriesStats.stdDev(values) else SeriesStats.mean(values)
    }

    /**
     * Les sept derniers jours, un point par jour, trous compris.
     *
     * Construit depuis le calendrier plutôt que depuis les mesures : une série qui ne porte
     * que trois nuits doit quand même rendre sept points, sinon la courbe miniature de
     * l'écran écraserait trois mesures éparses sur toute sa largeur et laisserait croire à
     * une semaine complète.
     */
    private fun weekOf(points: List<DayValue>, recentStart: LocalDate, today: LocalDate): List<DayPoint> {
        val measured = points.mapNotNull { point ->
            val value = point.value ?: return@mapNotNull null
            parseDate(point.date)?.let { it to value }
        }.toMap()

        return generateSequence(recentStart) { day -> day.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { day -> DayPoint(day, measured[day]) }
            .toList()
    }

    /** La date de la dernière mesure connue, toutes fenêtres confondues — « aucune pesée depuis 34 jours ». */
    private fun lastMeasuredDate(points: List<DayValue>): LocalDate? =
        points.asSequence()
            .filter { it.value != null }
            .mapNotNull { parseDate(it.date) }
            .maxOrNull()

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
            val date = parseDate(point.date) ?: continue
            if (date < recentStart) baseline.add(value) else recent.add(value)
        }
        return baseline to recent
    }

    /**
     * Une date du modèle, ou `null` si elle est illisible.
     *
     * Le modèle vient de l'appareil et ses dates sont toujours en ISO, mais il traverse une
     * sérialisation JSON et le contrat `ReportModel` existe en deux exemplaires. Une date
     * mal formée doit faire disparaître un point, pas fermer l'écran d'accueil.
     */
    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()
}
