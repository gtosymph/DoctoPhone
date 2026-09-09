package com.kmt.healthanalyzer.domain.analysis

import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyActivity
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import com.kmt.healthanalyzer.domain.model.StressSample
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Construit la vue consolidée d'une période à partir des mesures brutes.
 *
 * L'agrégateur est une fonction pure : il ne connaît ni la base ni Android. Toutes les
 * règles de calcul de l'app vivent ici, donc elles sont vérifiables par des tests simples.
 *
 * @param zone la zone horaire qui découpe les journées. Les mesures sont horodatées en
 *   temps absolu ; c'est la zone qui décide à quel jour chacune appartient.
 */
class HealthAggregator(private val zone: ZoneId) {

    fun aggregate(
        range: ClosedRange<LocalDate>,
        dailySteps: List<DailySteps> = emptyList(),
        dailyActivities: List<DailyActivity> = emptyList(),
        sleepNights: List<SleepNight> = emptyList(),
        heartRates: List<HeartRateSample> = emptyList(),
        stress: List<StressSample> = emptyList(),
        hrv: List<HrvSample> = emptyList(),
        spO2: List<SpO2Sample> = emptyList(),
        bodyCompositions: List<BodyComposition> = emptyList(),
        energyScores: List<EnergyScore> = emptyList(),
        sleepTargetMinutes: Int = DEFAULT_SLEEP_TARGET_MINUTES,
    ): HealthSnapshot {
        val mergedNights = mergeFragmentedSleepNights(sleepNights)

        val stepsByDay = dailySteps.associateBy { it.date }
        val activityByDay = dailyActivities.associateBy { it.date }
        val sleepByDay = mergedNights.associateBy { it.date }
        val energyByDay = energyScores.associateBy { it.date }
        val heartRateByDay = heartRates.groupBy { it.time.atZone(zone).toLocalDate() }
        val stressByDay = stress.groupBy { it.start.atZone(zone).toLocalDate() }
        val hrvByDay = hrv.groupBy { it.time.atZone(zone).toLocalDate() }
        val spO2ByDay = spO2.groupBy { it.time.atZone(zone).toLocalDate() }
        val weightByDay = lastKnownWeightPerDay(bodyCompositions, range)

        val days = daysOf(range).map { date ->
            val beats = heartRateByDay[date].orEmpty().map { it.beatsPerMinute }
            val night = sleepByDay[date]
            DailySnapshot(
                date = date,
                steps = stepsByDay[date]?.steps ?: activityByDay[date]?.steps,
                activeMinutes = activityByDay[date]?.activeMinutes,
                activeCalories = activityByDay[date]?.activeCalories,
                restingHeartRate = restingHeartRate(beats),
                averageHeartRate = beats.averageOrNull()?.roundToInt(),
                sleepMinutes = night?.durationMinutes,
                sleepScore = night?.score,
                // localBedTime manque pour les nuits Health Connect (seul l'export Samsung
                // le renseigne) ; bedTime, un Instant, est lui toujours présent.
                bedTime = night?.let { it.localBedTime ?: it.bedTime.atZone(zone).toLocalTime() },
                averageStress = stressByDay[date].orEmpty().map { it.score }.averageOrNull()?.roundToInt(),
                hrvRmssd = hrvByDay[date].orEmpty().mapNotNull { it.rmssdMillis }.averageOrNull()?.toFloat(),
                spO2 = spO2ByDay[date].orEmpty().map { it.percent }.averageOrNull()?.toFloat(),
                weightKg = weightByDay[date],
                energyScore = energyByDay[date]?.total,
            )
        }

        return HealthSnapshot(
            from = range.start,
            to = range.endInclusive,
            days = days,
            trends = buildTrends(days),
            sleepRegularity = buildSleepRegularity(mergedNights, sleepTargetMinutes),
        )
    }

    /**
     * Estime la fréquence cardiaque au repos par le 5e centile des mesures du jour.
     *
     * La montre ne marque pas les mesures « au repos ». Le bas de la distribution
     * journalière en est le meilleur proxy. En dessous de [MIN_SAMPLES_FOR_RESTING]
     * mesures, la journée n'est pas assez couverte pour que l'estimation ait un sens.
     */
    private fun restingHeartRate(beats: List<Int>): Int? {
        if (beats.size < MIN_SAMPLES_FOR_RESTING) return null
        val sorted = beats.sorted()
        return sorted[((sorted.size - 1) * RESTING_PERCENTILE).toInt().coerceIn(0, sorted.lastIndex)]
    }

    /**
     * Reporte la dernière pesée connue sur les jours sans mesure.
     *
     * L'utilisateur ne se pèse pas tous les jours, mais son poids de la veille reste
     * la meilleure estimation du jour. Les jours antérieurs à la première pesée
     * restent vides : rien ne permet de les remplir.
     */
    private fun lastKnownWeightPerDay(
        measurements: List<BodyComposition>,
        range: ClosedRange<LocalDate>,
    ): Map<LocalDate, Float> {
        if (measurements.isEmpty()) return emptyMap()
        val byDay = measurements
            .sortedBy { it.time }
            .associate { it.time.atZone(zone).toLocalDate() to it.weightKg }

        val result = LinkedHashMap<LocalDate, Float>()
        var carried: Float? = null
        for (date in daysOf(range)) {
            carried = byDay[date] ?: carried
            carried?.let { result[date] = it }
        }
        return result
    }

    private fun buildTrends(days: List<DailySnapshot>): List<MetricTrend> {
        val windowSize = (days.size / 2).coerceAtMost(TREND_WINDOW_DAYS).coerceAtLeast(1)
        val recent = days.takeLast(windowSize)
        val previous = days.dropLast(windowSize).takeLast(windowSize)

        return HealthMetric.entries.mapNotNull { metric ->
            val recentAverage = recent.mapNotNull { metric.valueOf(it) }.averageOrNull()
                ?: return@mapNotNull null
            val previousAverage = previous.mapNotNull { metric.valueOf(it) }.averageOrNull()
            val change = previousAverage
                ?.takeIf { abs(it) > 0.0 }
                ?.let { (recentAverage - it) / abs(it) * 100.0 }

            MetricTrend(
                metric = metric,
                recentAverage = recentAverage,
                previousAverage = previousAverage,
                direction = directionOf(change),
                changePercent = change,
            )
        }
    }

    private fun directionOf(changePercent: Double?): TrendDirection = when {
        changePercent == null -> TrendDirection.STABLE
        changePercent > NOISE_BAND_PERCENT -> TrendDirection.UP
        changePercent < -NOISE_BAND_PERCENT -> TrendDirection.DOWN
        else -> TrendDirection.STABLE
    }

    /**
     * Résume la régularité du sommeil.
     *
     * Les heures de coucher forment un cercle, pas une droite : 23 h et 1 h sont
     * distants de 2 heures. Le calcul passe donc par [CircularStats], sinon une personne
     * qui se couche autour de minuit paraîtrait très irrégulière. [nights] doit déjà
     * être passé par [mergeFragmentedSleepNights] : une nuit fractionnée en plusieurs
     * sessions ne doit fournir qu'une seule heure de coucher, sinon l'écart-type se
     * retrouve gonflé par des sessions qui décrivent la même nuit.
     */
    private fun buildSleepRegularity(nights: List<SleepNight>, targetMinutes: Int): SleepRegularity? {
        if (nights.isEmpty()) return null
        val durations = nights.map { it.durationMinutes }
        // localBedTime manque pour les nuits Health Connect : on retombe sur bedTime plutôt
        // que de les exclure, sinon la régularité ne porte que sur une partie des nuits.
        val hours = nights.map { (it.localBedTime ?: it.bedTime.atZone(zone).toLocalTime()).toSecondOfDay() / SECONDS_PER_HOUR }

        return SleepRegularity(
            averageBedtime = CircularStats.mean(hours)?.let { hoursToLocalTime(it) },
            bedtimeSpreadHours = CircularStats.stdDevHours(hours) ?: (HOURS_PER_DAY_FALLBACK / 2.0),
            averageDurationMinutes = durations.average().roundToInt(),
            sleepDebtMinutes = durations.sumOf { (targetMinutes - it).coerceAtLeast(0) },
            targetMinutes = targetMinutes,
            nightsMeasured = nights.size,
        )
    }

    private fun hoursToLocalTime(hours: Double): LocalTime {
        val secondsOfDay = (hours * SECONDS_PER_HOUR).roundToLong().mod(SECONDS_PER_DAY_LONG)
        return LocalTime.ofSecondOfDay(secondsOfDay)
    }

    private fun daysOf(range: ClosedRange<LocalDate>): List<LocalDate> {
        val count = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt()
        return (0..count).map { range.start.plusDays(it.toLong()) }
    }

    private companion object {
        const val DEFAULT_SLEEP_TARGET_MINUTES = 450 // 7 h 30
        const val MIN_SAMPLES_FOR_RESTING = 20
        const val RESTING_PERCENTILE = 0.05
        const val TREND_WINDOW_DAYS = 14
        const val NOISE_BAND_PERCENT = 5.0
        const val SECONDS_PER_HOUR = 3600.0
        const val SECONDS_PER_DAY_LONG = 86_400L
        const val HOURS_PER_DAY_FALLBACK = 24.0
    }
}

/**
 * Réunit les sessions de sommeil d'une même nuit (même [SleepNight.date], le jour du
 * réveil) en une seule.
 *
 * Samsung écrit parfois plusieurs sessions pour une même nuit : un coucher interrompu,
 * repris plus tard. Room garde une ligne par session, fidèle à l'export ; c'est ici, à
 * la lecture, que les sessions d'une même nuit se réunissent. [ReportBuilder] doit
 * passer par la même fonction, pour que le rapport et l'écran d'accueil s'accordent.
 */
fun mergeFragmentedSleepNights(nights: List<SleepNight>): List<SleepNight> =
    nights.groupBy { it.date }.map { (_, sessions) -> mergeSessionsOfOneNight(sessions) }

private fun mergeSessionsOfOneNight(sessions: List<SleepNight>): SleepNight {
    if (sessions.size == 1) return sessions.single()
    val orderedByBedTime = sessions.sortedBy { it.bedTime }
    val longest = sessions.maxBy { it.durationMinutes }

    // Le score, l'efficacité, la latence et les indices de récupération viennent de la
    // session la plus longue : Samsung ne calcule pas de score agrégé pour une nuit
    // fractionnée, et cette session est la plus représentative de la nuit.
    return longest.copy(
        bedTime = orderedByBedTime.first().bedTime,
        wakeTime = orderedByBedTime.last().wakeTime,
        localBedTime = orderedByBedTime.first().localBedTime,
        durationMinutes = sessions.sumOf { it.durationMinutes },
        remMinutes = sessions.sumIgnoringNulls { it.remMinutes },
        lightMinutes = sessions.sumIgnoringNulls { it.lightMinutes },
        deepMinutes = sessions.sumIgnoringNulls { it.deepMinutes },
        awakeMinutes = sessions.sumIgnoringNulls { it.awakeMinutes },
    )
}

/** Somme les valeurs non nulles, ou `null` si aucune session ne porte la mesure. */
private fun List<SleepNight>.sumIgnoringNulls(selector: (SleepNight) -> Int?): Int? {
    val present = mapNotNull(selector)
    return if (present.isEmpty()) null else present.sum()
}

/** Rend la valeur numérique d'une mesure pour un jour, ou `null` si elle manque. */
private fun HealthMetric.valueOf(day: DailySnapshot): Double? = when (this) {
    HealthMetric.STEPS -> day.steps?.toDouble()
    HealthMetric.ACTIVE_MINUTES -> day.activeMinutes?.toDouble()
    HealthMetric.SLEEP_DURATION -> day.sleepMinutes?.toDouble()
    HealthMetric.SLEEP_SCORE -> day.sleepScore?.toDouble()
    HealthMetric.RESTING_HEART_RATE -> day.restingHeartRate?.toDouble()
    HealthMetric.HRV -> day.hrvRmssd?.toDouble()
    HealthMetric.STRESS -> day.averageStress?.toDouble()
    HealthMetric.WEIGHT -> day.weightKg?.toDouble()
    HealthMetric.ENERGY_SCORE -> day.energyScore?.toDouble()
    HealthMetric.SPO2 -> day.spO2?.toDouble()
}

private fun <T : Number> List<T>.averageOrNull(): Double? =
    if (isEmpty()) null else sumOf { it.toDouble() } / size

private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
