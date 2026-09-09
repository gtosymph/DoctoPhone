package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HIGH_STRESS_THRESHOLD = 60.0
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

internal fun buildStressSection(input: ReportInput, zone: ZoneId): StressSection {
    val byDay = input.stress.groupBy { it.start.atZone(zone).toLocalDate() }
    val dailyMean = byDay.mapValues { (_, samples) -> SeriesStats.mean(samples.map { it.score.toDouble() }) }
    val hourly = SeriesStats.byHour(input.stress.map { it.start.atZone(zone).hour to it.score.toDouble() })
    val energyByDay = input.energyScores.associate { it.date to it.total.toDouble() }

    return StressSection(
        monthly = monthlyStress(input, zone),
        hourly = hourly,
        dayOfWeek = SeriesStats.byDayOfWeek(dailyMean),
        daily = dailyMean.toSortedMap().map { (date, value) -> DayValue(date.toString(), value) },
        vitalityDaily = energyByDay.toSortedMap().map { (date, value) -> DayValue(date.toString(), value) },
        kpi = StressKpi(
            // Moyenne des moyennes journalières, pas des échantillons bruts : un jour très
            // échantillonné ne doit pas peser plus qu'un autre.
            mean = SeriesStats.mean(dailyMean.values.filterNotNull()),
            // percentAbove60, lui, porte sur les échantillons bruts : c'est la proportion du
            // temps mesuré au-dessus du seuil, pas la proportion de jours dont la moyenne
            // dépasse le seuil.
            percentAbove60 = input.stress.map { it.score.toDouble() }.percentOf { it > HIGH_STRESS_THRESHOLD },
            alerts = input.stressAlerts.size,
            peakHour = hourly.withIndex().filter { it.value.value != null }.maxByOrNull { it.value.value!! }?.index,
            vitalityMean = SeriesStats.mean(energyByDay.values.toList()),
            measuredDays = byDay.keys.size,
        ),
    )
}

private fun monthlyStress(input: ReportInput, zone: ZoneId): List<StressMonth> =
    input.stress.groupBy { it.start.atZone(zone).toLocalDate().format(MONTH_FORMAT) }
        .toSortedMap()
        .map { (month, inMonth) ->
            val scores = inMonth.map { it.score.toDouble() }
            StressMonth(
                month = month,
                mean = SeriesStats.mean(scores) ?: 0.0,
                percentAbove60 = scores.percentOf { it > HIGH_STRESS_THRESHOLD } ?: 0.0,
            )
        }

private fun List<Double>.percentOf(predicate: (Double) -> Boolean): Double? {
    if (isEmpty()) return null
    return count(predicate).toDouble() / size * 100.0
}
