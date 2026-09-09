package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.BodyComposition
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal fun buildBodySection(input: ReportInput, zone: ZoneId): BodySection {
    val ordered = input.bodyCompositions.sortedBy { it.time }
    if (ordered.isEmpty()) return BodySection()

    val daily = ordered.map {
        BodyPoint(
            date = it.time.atZone(zone).toLocalDate().toString(),
            weightKg = it.weightKg.toDouble(),
            bodyFatPercent = it.bodyFatPercent?.toDouble(),
            skeletalMuscleKg = it.skeletalMuscleMassKg?.toDouble(),
            bodyMassIndex = it.bodyMassIndex?.toDouble(),
            basalMetabolicRate = it.basalMetabolicRate,
        )
    }

    val first = ordered.first()
    val last = ordered.last()
    val lastDate = last.time.atZone(zone).toLocalDate()

    return BodySection(
        daily = daily,
        kpi = BodyKpi(
            firstWeightKg = first.weightKg.toDouble(),
            lastWeightKg = last.weightKg.toDouble(),
            deltaKg = (last.weightKg - first.weightKg).toDouble(),
            deltaMuscleKg = deltaOf(first, last) { it.skeletalMuscleMassKg },
            deltaFatKg = deltaOf(first, last, ::fatMassKgOf),
            bodyMassIndex = last.bodyMassIndex?.toDouble(),
            bodyFatPercent = last.bodyFatPercent?.toDouble(),
            basalMetabolicRate = last.basalMetabolicRate,
            lastMeasuredOn = lastDate.toString(),
            daysSinceLastMeasure = ChronoUnit.DAYS.between(lastDate, input.range.endInclusive).toInt(),
            measures = ordered.size,
        ),
    )
}

private fun deltaOf(first: BodyComposition, last: BodyComposition, selector: (BodyComposition) -> Float?): Double? {
    val a = selector(first) ?: return null
    val b = selector(last) ?: return null
    return (b - a).toDouble()
}

/**
 * Masse grasse lue sur `bodyFatMassKg`, le champ que la balance fournit.
 *
 * La reconstituer depuis le pourcentage et le poids introduit une erreur d'arrondi que
 * la mesure directe n'a pas. On retombe sur le calcul seulement quand la balance n'a pas
 * donné la masse.
 */
private fun fatMassKgOf(measurement: BodyComposition): Float? {
    measurement.bodyFatMassKg?.let { return it }
    val percent = measurement.bodyFatPercent ?: return null
    return percent / 100f * measurement.weightKg
}
