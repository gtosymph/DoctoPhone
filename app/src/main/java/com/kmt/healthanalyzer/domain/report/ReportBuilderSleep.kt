package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.CircularStats
import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import com.kmt.healthanalyzer.domain.model.SleepApneaResult
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SleepStage
import com.kmt.healthanalyzer.domain.model.SleepStageSegment
import com.kmt.healthanalyzer.domain.model.SnoringEpisode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

private val DURATION_BUCKETS = listOf(
    SeriesStats.Bucket("< 5 h", Double.NEGATIVE_INFINITY, 5.0),
    SeriesStats.Bucket("5-6 h", 5.0, 6.0),
    SeriesStats.Bucket("6-7 h", 6.0, 7.0),
    SeriesStats.Bucket("7-8 h", 7.0, 8.0),
    SeriesStats.Bucket("> 8 h", 8.0, Double.POSITIVE_INFINITY),
)

internal fun buildSleepSection(input: ReportInput, mergedNights: List<SleepNight>, zone: ZoneId): SleepSection {
    if (mergedNights.isEmpty()) return SleepSection()

    val sessionsCountByDate = input.sleepNights.groupingBy { it.date }.eachCount()
    val hours = mergedNights.map { it.durationMinutes / 60.0 }
    // localBedTime manque pour les nuits venues de Health Connect (seul l'export Samsung
    // le renseigne) : bedTime, lui, est toujours présent, donc on y retombe plutôt que de
    // laisser le coucher — et tout ce qui en dépend — disparaître.
    val bedRelByNight = mergedNights.map { relativeHours(localBedTimeOf(it, zone)) }
    val wakeRelByNight = mergedNights.map { relativeHours(it.wakeTime.atZone(zone).toLocalTime()) }

    return SleepSection(
        nightly = mergedNights.mapIndexed { index, night ->
            SleepNightPoint(
                date = night.date.toString(),
                hours = hours[index],
                score = night.score,
                bedRel = bedRelByNight[index],
                wakeRel = wakeRelByNight[index],
                efficiencyPercent = night.efficiencyPercent?.toDouble(),
                sessions = sessionsCountByDate[night.date] ?: 1,
            )
        },
        monthly = monthlySleepStats(mergedNights),
        dayOfWeek = SeriesStats.byDayOfWeek(mergedNights.associate { it.date to it.durationMinutes / 60.0 }),
        distribution = SeriesStats.distribution(hours, DURATION_BUCKETS),
        stagesMonthly = monthlyStages(mergedNights, input.sleepNights, input.sleepStages),
        kpi = sleepKpi(input, mergedNights, hours, bedRelByNight, wakeRelByNight, zone),
    )
}

private fun monthlySleepStats(nights: List<SleepNight>): List<SleepMonthStat> =
    nights.groupBy { it.date.format(MONTH_FORMAT) }
        .toSortedMap()
        .map { (month, nightsInMonth) ->
            val hours = nightsInMonth.map { it.durationMinutes / 60.0 }
            SleepMonthStat(
                month = month,
                meanHours = SeriesStats.mean(hours) ?: 0.0,
                medianHours = SeriesStats.median(hours) ?: 0.0,
                meanScore = SeriesStats.mean(nightsInMonth.mapNotNull { it.score?.toDouble() }),
                nights = nightsInMonth.size,
            )
        }

/**
 * Répartition des stades par mois.
 *
 * Le dénominateur est la **somme des quatre stades** du mois, pas la durée de sommeil :
 * les quatre parts totalisent ainsi exactement 100, et la barre empilée du rapport se
 * remplit sans laisser de vide inexpliqué (diviser par la durée laisserait un écart
 * quand une partie de la nuit n'a pas été classée). Un mois sans aucun stade connu est
 * omis plutôt que montré à zéro partout.
 */
private fun monthlyStages(
    nights: List<SleepNight>,
    sessions: List<SleepNight>,
    segments: List<SleepStageSegment>,
): List<SleepStageMonth> {
    val byNight = stageMinutesByNight(nights, sessions, segments)
    return nights.groupBy { it.date.format(MONTH_FORMAT) }
        .toSortedMap()
        .mapNotNull { (month, nightsInMonth) ->
            val totals = nightsInMonth.map { byNight[it.date] ?: StageMinutes() }
            val deep = totals.sumOf { it.deep }
            val light = totals.sumOf { it.light }
            val rem = totals.sumOf { it.rem }
            val awake = totals.sumOf { it.awake }
            val total = deep + light + rem + awake
            if (total <= 0) return@mapNotNull null
            SleepStageMonth(
                month = month,
                deep = deep / total.toDouble() * 100.0,
                light = light / total.toDouble() * 100.0,
                rem = rem / total.toDouble() * 100.0,
                awake = awake / total.toDouble() * 100.0,
            )
        }
}

/** Minutes passées dans chaque stade au cours d'une nuit. */
internal data class StageMinutes(
    val deep: Int = 0,
    val light: Int = 0,
    val rem: Int = 0,
    val awake: Int = 0,
) {
    val total: Int get() = deep + light + rem + awake
}

/**
 * Minutes de chaque stade, par jour de réveil.
 *
 * Les segments détaillés priment sur les champs de la nuit, parce qu'eux seuls portent le
 * sommeil profond et l'éveil : le fichier de nuits Samsung ne donne que
 * `total_rem_duration` et `total_light_duration`. Sans eux, la répartition ramenait le
 * léger et le paradoxal à 100 % et montrait de fausses proportions.
 *
 * Les champs de la nuit servent de repli, pour les nuits venues de Health Connect ou d'un
 * export sans segments.
 */
internal fun stageMinutesByNight(
    nights: List<SleepNight>,
    sessions: List<SleepNight>,
    segments: List<SleepStageSegment>,
): Map<LocalDate, StageMinutes> {
    // Un segment porte l'identifiant de la session d'origine, pas celui de la nuit
    // fusionnée : la table doit donc se construire sur les sessions brutes, sinon les
    // segments des nuits fractionnées seraient tous ignorés.
    val dateBySleepId = sessions.associate { it.id to it.date }
    val fromSegments = mutableMapOf<LocalDate, StageMinutes>()
    for (segment in segments) {
        val date = dateBySleepId[segment.sleepId] ?: continue
        val current = fromSegments[date] ?: StageMinutes()
        fromSegments[date] = when (segment.stage) {
            SleepStage.DEEP -> current.copy(deep = current.deep + segment.durationMinutes)
            SleepStage.LIGHT -> current.copy(light = current.light + segment.durationMinutes)
            SleepStage.REM -> current.copy(rem = current.rem + segment.durationMinutes)
            SleepStage.AWAKE -> current.copy(awake = current.awake + segment.durationMinutes)
            SleepStage.UNKNOWN -> current
        }
    }

    return nights.associate { night ->
        val measured = fromSegments[night.date]
        night.date to if (measured != null && measured.total > 0) {
            measured
        } else {
            StageMinutes(
                deep = night.deepMinutes ?: 0,
                light = night.lightMinutes ?: 0,
                rem = night.remMinutes ?: 0,
                awake = night.awakeMinutes ?: 0,
            )
        }
    }
}

/**
 * Nombre de nuits distinctes portant au moins un épisode de ronflement.
 *
 * Chaque épisode est rattaché à la nuit dont il tombe dans la fenêtre coucher-réveil, et
 * non à la date calendaire de son début : un ronflement à deux heures du matin appartient
 * à la nuit commencée la veille. Un épisode qui ne tombe dans aucune nuit mesurée ne
 * compte pas, sinon le rapport pourrait annoncer « 30 nuits sur 26 ».
 */
private fun snoringNightCount(nights: List<SleepNight>, episodes: List<SnoringEpisode>): Int =
    nights.count { night ->
        episodes.any { episode ->
            episode.durationMinutes > 0 &&
                !episode.start.isBefore(night.bedTime) &&
                !episode.start.isAfter(night.wakeTime)
        }
    }

private fun sleepKpi(
    input: ReportInput,
    nights: List<SleepNight>,
    hours: List<Double>,
    bedRelByNight: List<Double>,
    wakeRelByNight: List<Double>,
    zone: ZoneId,
): SleepKpi {
    // Toutes les nuits comptent, y compris celles venues de Health Connect sans
    // localBedTime : voir localBedTimeOf.
    val bedHours = nights.map { localBedTimeOf(it, zone).toSecondOfDay() / SECONDS_PER_HOUR }
    val bedRel = bedRelByNight
    val snoring = input.snoringEpisodes

    return SleepKpi(
        nights = nights.size,
        meanHours = SeriesStats.mean(hours),
        medianHours = SeriesStats.median(hours),
        bedMedian = CircularStats.circularMedian(bedHours)?.let { relativeHours(it) },
        wakeMedian = SeriesStats.median(wakeRelByNight),
        bedSpreadHours = CircularStats.stdDevHours(bedHours),
        pctAfterMidnight = bedRel.percentOf { it > 0.0 },
        pctAfter2h = bedRel.percentOf { it > 2.0 },
        pctUnder6h = hours.percentOf { it < 6.0 },
        pctOver7h = hours.percentOf { it >= 7.0 },
        weekendCatchupHours = weekendCatchup(nights.map { it.date to it.durationMinutes / 60.0 }),
        debtHours = hours.sumOf { (input.sleepTargetHours - it).coerceAtLeast(0.0) },
        targetHours = input.sleepTargetHours,
        efficiencyPercent = SeriesStats.mean(nights.mapNotNull { it.efficiencyPercent?.toDouble() }),
        latencyMinutes = SeriesStats.mean(nights.mapNotNull { it.latencyMinutes?.toDouble() }),
        snoringNights = snoringNightCount(nights, snoring),
        snoringMeasuredNights = nights.size,
        snoringMedianMinutes = SeriesStats.median(snoring.filter { it.durationMinutes > 0 }.map { it.durationMinutes.toDouble() }),
        apneaResult = latestApneaResultLabel(input.sleepApneaResults),
    )
}

/**
 * Rattrapage de sommeil le week-end : la différence entre la moyenne des moyennes des
 * jours de week-end et celle des jours de semaine (pas la moyenne brute des nuits de
 * chaque groupe), pour qu'un week-end avec peu de nuits mesurées ne pèse pas plus que
 * les cinq jours de semaine dans la comparaison.
 */
private fun weekendCatchup(hoursByDate: List<Pair<LocalDate, Double>>): Double? {
    // Ordre lun.=0 ... dim.=6 (voir SeriesStats.byDayOfWeek) : le week-end est sam. (5) et dim. (6).
    val byDay = SeriesStats.byDayOfWeek(hoursByDate.associate { it.first to it.second })
    val weekdayMean = SeriesStats.mean(byDay.take(5).mapNotNull { it.value })
    val weekendMean = SeriesStats.mean(byDay.drop(5).mapNotNull { it.value })
    return if (weekdayMean != null && weekendMean != null) weekendMean - weekdayMean else null
}

private fun List<Double>.percentOf(predicate: (Double) -> Boolean): Double? {
    if (isEmpty()) return null
    return count(predicate).toDouble() / size * 100.0
}

/**
 * Samsung ne documente pas publiquement le sens de ses codes de résultat d'apnée : on
 * affiche le code brut plutôt que d'inventer une interprétation clinique incertaine, et
 * on renvoie vers Samsung Health Monitor pour le libellé officiel.
 */
private fun latestApneaResultLabel(results: List<SleepApneaResult>): String? =
    results.maxByOrNull { it.time }?.let { "Résultat ${it.result} — à lire dans Samsung Health Monitor" }

private const val SECONDS_PER_HOUR = 3600.0

/**
 * Heure locale de coucher d'une nuit.
 *
 * `localBedTime` ne vient que de l'export Samsung ; les nuits importées depuis Health
 * Connect ne le portent pas. `bedTime` (un [Instant]), lui, est toujours renseigné : on
 * y retombe pour ne jamais laisser le coucher — et tout ce qui en dépend (le graphique
 * nuit par nuit, `bedMedian`, `bedSpreadHours`, les tuiles, la corrélation avec la durée
 * de sommeil) — disparaître pour ces nuits-là.
 */
internal fun localBedTimeOf(night: SleepNight, zone: ZoneId): LocalTime =
    night.localBedTime ?: night.bedTime.atZone(zone).toLocalTime()

/** Heure relative à minuit : un coucher du soir (>= 12 h) devient négatif. */
internal fun relativeHours(time: LocalTime): Double {
    val hours = time.toSecondOfDay() / SECONDS_PER_HOUR
    return if (hours >= 12.0) hours - 24.0 else hours
}

internal fun relativeHours(hoursOfDay: Double): Double = if (hoursOfDay >= 12.0) hoursOfDay - 24.0 else hoursOfDay
