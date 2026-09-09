package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
private const val LOW_SPO2_THRESHOLD = 90.0

internal fun buildBreathingSection(input: ReportInput, zone: ZoneId): BreathingSection {
    val spo2ByDay = input.spO2.groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, samples) -> SeriesStats.mean(samples.map { it.percent.toDouble() }) }
    val respiratoryByDay = input.respiratoryRates.groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, samples) -> SeriesStats.mean(samples.map { it.breathsPerMinute.toDouble() }) }
    val skinTempByDay = input.skinTemperatures.groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, samples) -> SeriesStats.mean(samples.map { it.celsius.toDouble() }) }

    return BreathingSection(
        spo2Monthly = input.spO2.groupBy { it.time.atZone(zone).toLocalDate().format(MONTH_FORMAT) }
            .toSortedMap()
            .map { (month, inMonth) ->
                val percents = inMonth.map { it.percent.toDouble() }
                Spo2Month(month = month, mean = SeriesStats.mean(percents) ?: 0.0, min = percents.min())
            },
        spo2Daily = spo2ByDay.toSortedMap().map { (date, value) -> DayValue(date.toString(), value) },
        respiratoryDaily = respiratoryByDay.toSortedMap().map { (date, value) -> DayValue(date.toString(), value) },
        skinTempDaily = skinTempByDay.toSortedMap().map { (date, value) -> DayValue(date.toString(), value) },
        kpi = BreathingKpi(
            // spo2Mean porte sur les échantillons bruts (spo2 est déjà ponctuelle et peu
            // fréquente) ; respiratoryMean, skinTempMean et skinTempStdDev, eux, portent
            // sur les moyennes journalières, pas les échantillons bruts, pour qu'un jour
            // très échantillonné ne domine pas l'indicateur.
            spo2Mean = SeriesStats.mean(input.spO2.map { it.percent.toDouble() }),
            spo2Measures = input.spO2.size,
            spo2Under90 = input.spO2.count { it.percent < LOW_SPO2_THRESHOLD },
            respiratoryMean = SeriesStats.mean(respiratoryByDay.values.filterNotNull()),
            skinTempMean = SeriesStats.mean(skinTempByDay.values.filterNotNull()),
            skinTempStdDev = SeriesStats.stdDev(skinTempByDay.values.filterNotNull()),
        ),
    )
}
