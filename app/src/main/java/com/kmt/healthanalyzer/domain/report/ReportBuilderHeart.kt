package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import com.kmt.healthanalyzer.domain.model.EcgRecord
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Sous [MIN_SAMPLES_FOR_RESTING] mesures, un jour n'est pas assez couvert pour une estimation au repos. */
private const val MIN_SAMPLES_FOR_RESTING = 20
private const val RESTING_PERCENTILE = 0.05
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

internal fun buildHeartSection(input: ReportInput, zone: ZoneId): HeartSection {
    val byDay = input.heartRates.groupBy { it.time.atZone(zone).toLocalDate() }
    val restingByDay = byDay.mapValues { (_, samples) -> restingHeartRate(samples.map { it.beatsPerMinute }) }
    val averageByDay = byDay.mapValues { (_, samples) -> SeriesStats.mean(samples.map { it.beatsPerMinute.toDouble() }) }
    val hrvByDay = input.hrv.groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, samples) -> SeriesStats.mean(samples.mapNotNull { it.rmssdMillis?.toDouble() }) }

    return HeartSection(
        monthly = monthlyHeart(restingByDay, averageByDay),
        restingDaily = restingByDay.toSortedMap().map { (date, value) -> DayValue(date.toString(), value?.toDouble()) },
        hourly = SeriesStats.byHour(input.heartRates.map { it.time.atZone(zone).hour to it.beatsPerMinute.toDouble() }),
        hrvMonthly = SeriesStats.byMonth(hrvByDay),
        hrvDaily = hrvByDay.toSortedMap().map { (date, value) -> DayValue(date.toString(), value) },
        bloodPressure = input.bloodPressure.sortedBy { it.time }.map {
            BloodPressurePoint(
                date = it.time.atZone(zone).toLocalDate().toString(),
                systolic = it.systolic,
                diastolic = it.diastolic,
                pulse = it.pulse,
            )
        },
        ecg = input.ecgRecords.sortedBy { it.time }.map {
            EcgPoint(
                date = it.time.atZone(zone).toLocalDate().toString(),
                meanHeartRate = it.meanHeartRate,
                classification = it.classification,
                classificationLabel = classificationLabel(it),
            )
        },
        kpi = heartKpi(restingByDay, averageByDay, hrvByDay, input.heartRates.map { it.beatsPerMinute }),
    )
}

/**
 * [HeartKpi.maxObserved] est la fréquence la plus haute réellement mesurée, prise sur
 * les échantillons bruts. Un maximum calculé sur des moyennes journalières n'est plus un
 * maximum : il ne dépasse jamais la moyenne du jour le plus intense.
 *
 * [HeartKpi.hrvMedian] est la médiane des moyennes *journalières* de HRV, pas des
 * échantillons bruts : un jour très échantillonné ne doit pas peser plus qu'un autre.
 */
private fun heartKpi(
    restingByDay: Map<LocalDate, Int?>,
    averageByDay: Map<LocalDate, Double?>,
    hrvByDay: Map<LocalDate, Double?>,
    beats: List<Int>,
): HeartKpi {
    val resting = restingByDay.values.filterNotNull().map { it.toDouble() }
    val average = averageByDay.values.filterNotNull()

    return HeartKpi(
        restingMean = SeriesStats.mean(resting),
        restingP10 = SeriesStats.percentile(resting, 0.10),
        restingP90 = SeriesStats.percentile(resting, 0.90),
        averageMean = SeriesStats.mean(average),
        maxObserved = beats.maxOrNull(),
        hrvMedian = SeriesStats.median(hrvByDay.values.filterNotNull()),
        measuredDays = restingByDay.values.count { it != null },
    )
}

/**
 * Estime la fréquence cardiaque au repos par le 5e centile des mesures du jour — la même
 * règle que [com.kmt.healthanalyzer.domain.analysis.HealthAggregator], pour que l'écran
 * d'accueil et le rapport racontent le même chiffre.
 */
private fun restingHeartRate(beats: List<Int>): Int? {
    if (beats.size < MIN_SAMPLES_FOR_RESTING) return null
    return SeriesStats.percentile(beats.map { it.toDouble() }, RESTING_PERCENTILE)?.roundToInt()
}

private fun monthlyHeart(restingByDay: Map<LocalDate, Int?>, averageByDay: Map<LocalDate, Double?>): List<HeartMonth> {
    val months = (restingByDay.keys + averageByDay.keys).map { it.format(MONTH_FORMAT) }.toSortedSet()
    return months.map { month ->
        val resting = restingByDay.filterKeys { it.format(MONTH_FORMAT) == month }.values.filterNotNull().map { it.toDouble() }
        val average = averageByDay.filterKeys { it.format(MONTH_FORMAT) == month }.values.filterNotNull()
        HeartMonth(month = month, resting = SeriesStats.mean(resting), average = SeriesStats.mean(average))
    }
}

/**
 * Samsung ne documente pas publiquement le sens de ses codes de classification ECG : on
 * n'y devine jamais un diagnostic (« rythme sinusal », « fibrillation »...), et on
 * renvoie vers Samsung Health Monitor pour le libellé officiel du fabricant.
 */
private fun classificationLabel(record: EcgRecord): String =
    record.classification?.let { "Résultat $it — à lire dans Samsung Health Monitor" } ?: "Résultat non enregistré"
