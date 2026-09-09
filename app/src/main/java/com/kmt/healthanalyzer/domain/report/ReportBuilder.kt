package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.mergeFragmentedSleepNights
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Construit le [ReportModel] complet à partir des mesures brutes.
 *
 * Classe pure : aucune dépendance à Room ni à Android, pour rester testable en JVM. Le
 * calcul est découpé en un fichier par section (`ReportBuilderSleep.kt`,
 * `ReportBuilderHeart.kt`, ...) pour rester sous 400 lignes chacun ; ce fichier ne fait
 * qu'orchestrer.
 *
 * @param zone la zone horaire qui découpe les journées et convertit les horodatages en
 *   heures locales.
 */
class ReportBuilder(private val zone: ZoneId) {

    fun build(input: ReportInput): ReportModel {
        val mergedNights = mergeFragmentedSleepNights(input.sleepNights)
            .filter { it.date in input.range }
            .sortedBy { it.date }

        val sleep = buildSleepSection(input, mergedNights, zone)
        val heart = buildHeartSection(input, zone)
        val activity = buildActivitySection(input, zone)
        val body = buildBodySection(input, zone)
        val stress = buildStressSection(input, zone)
        val breathing = buildBreathingSection(input, zone)
        val correlations = buildCorrelations(sleep, heart, activity, stress)
        val tiles = buildTiles(input, sleep, heart, activity, body, stress)

        return ReportModel(
            meta = buildMeta(input, mergedNights),
            tiles = tiles,
            sleep = sleep,
            heart = heart,
            activity = activity,
            body = body,
            stress = stress,
            breathing = breathing,
            correlations = correlations,
        )
    }

    private fun buildMeta(input: ReportInput, mergedNights: List<com.kmt.healthanalyzer.domain.model.SleepNight>): ReportMeta {
        val days = (java.time.temporal.ChronoUnit.DAYS.between(input.range.start, input.range.endInclusive) + 1)
            .toInt()
            .coerceAtLeast(0)
        val activeDaySet = buildSet {
            addAll(input.dailySteps.map { it.date })
            addAll(input.dailyActivities.map { it.date })
        }

        return ReportMeta(
            generatedAt = Instant.now().toString(),
            from = input.range.start.toString(),
            to = input.range.endInclusive.toString(),
            days = days,
            nights = mergedNights.size,
            heartRateSamples = input.heartRates.size,
            hrvWindows = input.hrv.size,
            activeDays = activeDaySet.count { it in input.range },
            timeZone = zone.id,
            periodLabel = formatPeriodLabel(input.range.start, input.range.endInclusive),
            profile = input.profile,
        )
    }

    /**
     * « 23 juin 2025 », ou « 1 janv. 2025 – 29 juin 2025 » sur deux dates distinctes —
     * mois abrégés français, jamais de zéro devant le quantième. Doit rester identique
     * au calcul JavaScript (`formatPeriodLabel`) : le test de parité les compare.
     */
    private fun formatPeriodLabel(from: LocalDate, to: LocalDate): String {
        fun format(date: LocalDate) = "${date.dayOfMonth} ${SHORT_MONTHS_FR[date.monthValue - 1]} ${date.year}"
        return if (from == to) format(from) else "${format(from)} – ${format(to)}"
    }

    private companion object {
        val SHORT_MONTHS_FR = listOf(
            "janv.", "févr.", "mars", "avr.", "mai", "juin",
            "juil.", "août", "sept.", "oct.", "nov.", "déc.",
        )
    }
}
