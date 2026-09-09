package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.analysis.SeriesStats
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val ROLLING_WINDOW_DAYS = 7
private const val ROLLING_MIN_SAMPLES = 4
private const val LOW_STEPS_THRESHOLD = 3000.0
private const val HIGH_STEPS_THRESHOLD = 8000.0
private const val LAST_N_DAYS_FOR_STEPS_30 = 30
private const val LAST_N_DAYS_FOR_STEPS_90 = 90
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

internal fun buildActivitySection(input: ReportInput, zone: ZoneId): ActivitySection {
    val stepsDaily = stepsByDate(input).toSortedMap().map { (date, steps) -> DayValue(date.toString(), steps) }

    return ActivitySection(
        stepsDaily = stepsDaily,
        stepsRolling7 = SeriesStats.rollingMean(stepsDaily.map { it.value }, ROLLING_WINDOW_DAYS, ROLLING_MIN_SAMPLES)
            .mapIndexed { i, value -> DayValue(stepsDaily[i].date, value) },
        stepsMonthly = SeriesStats.byMonth(stepsDaily.associate { LocalDate.parse(it.date) to it.value }),
        stepsDayOfWeek = SeriesStats.byDayOfWeek(stepsDaily.associate { LocalDate.parse(it.date) to it.value }),
        exerciseMonthly = monthlyExercise(input.exerciseSessions, zone),
        exerciseByKind = exerciseByKind(input.exerciseSessions),
        floorsMonthly = monthlyFloors(input),
        kpi = activityKpi(input, stepsDaily),
    )
}

/**
 * Le total de pas quotidien, tel quel : contrairement à l'écran d'accueil (voir
 * `HealthAggregator`), le rapport ne retombe pas sur `DailyActivity.steps` — seul le
 * magasin `dailySteps` alimente cette série, comme la version web.
 */
internal fun stepsByDate(input: ReportInput): Map<LocalDate, Double?> =
    input.dailySteps.associate { it.date to it.steps.toDouble() }

private fun monthlyExercise(sessions: List<ExerciseSession>, zone: ZoneId): List<ExerciseMonth> =
    sessions.groupBy { it.start.atZone(zone).toLocalDate().format(MONTH_FORMAT) }
        .toSortedMap()
        .map { (month, inMonth) ->
            val calories = inMonth.mapNotNull { it.calories?.toDouble() }
            ExerciseMonth(
                month = month,
                sessions = inMonth.size,
                minutes = inMonth.sumOf { it.durationMinutes }.toDouble(),
                calories = if (calories.isEmpty()) null else calories.sum(),
            )
        }

/**
 * Minutes d'exercice par type d'activité.
 *
 * L'étiquette est le libellé français, pas le nom de l'énumération. Le moteur de dessin
 * ne traduit rien : il pose l'étiquette telle quelle. Rendre `"WALKING"` ferait donc
 * lire `"WALKING"` à l'utilisateur.
 */
private val EXERCISE_LABELS: Map<ExerciseKind, String> = mapOf(
    ExerciseKind.WALKING to "Marche",
    ExerciseKind.RUNNING to "Course",
    ExerciseKind.CYCLING to "Vélo",
    ExerciseKind.HIKING to "Randonnée",
    ExerciseKind.SWIMMING to "Natation",
    ExerciseKind.STRENGTH to "Musculation",
    ExerciseKind.ELLIPTICAL to "Elliptique",
    ExerciseKind.ROWING to "Aviron",
    ExerciseKind.YOGA to "Yoga",
    ExerciseKind.OTHER to "Autre",
)

private fun exerciseByKind(sessions: List<ExerciseSession>): List<LabelValue> =
    sessions.groupBy { EXERCISE_LABELS[it.kind] ?: "Autre" }
        .toSortedMap()
        .map { (kind, inKind) -> LabelValue(label = kind, value = inKind.sumOf { it.durationMinutes }.toDouble(), count = inKind.size) }

/**
 * Moyenne quotidienne d'étages montés par mois — une moyenne, pas un total, comme
 * [ActivityKpi.meanSteps30] : elle se compare d'un mois à l'autre même quand les mois
 * n'ont pas la même longueur. Utilise `dailyFloors` s'il porte des mesures, sinon
 * retombe sur le champ `floors` de `dailyActivity`.
 */
private fun monthlyFloors(input: ReportInput): List<MonthValue> {
    val byDate = if (input.dailyFloors.isNotEmpty()) {
        input.dailyFloors.associate { it.date to it.floors.toDouble() }
    } else {
        input.dailyActivities.mapNotNull { activity -> activity.floors?.let { activity.date to it.toDouble() } }.toMap()
    }
    return SeriesStats.byMonth(byDate)
}

private fun activityKpi(input: ReportInput, stepsDaily: List<DayValue>): ActivityKpi {
    val defined = stepsDaily.filter { it.value != null }
    val values = defined.mapNotNull { it.value }
    val last30 = defined.takeLast(LAST_N_DAYS_FOR_STEPS_30).mapNotNull { it.value }
    val last90 = defined.takeLast(LAST_N_DAYS_FOR_STEPS_90).mapNotNull { it.value }
    val best = defined.maxByOrNull { it.value!! }

    return ActivityKpi(
        meanSteps = SeriesStats.mean(values),
        meanSteps30 = SeriesStats.mean(last30),
        meanSteps90 = SeriesStats.mean(last90),
        bestSteps = best?.value?.toInt(),
        bestStepsDate = best?.date,
        pctDaysUnder3000 = values.percentOf { it < LOW_STEPS_THRESHOLD },
        pctDaysOver8000 = values.percentOf { it >= HIGH_STEPS_THRESHOLD },
        totalExerciseMinutes = input.exerciseSessions.sumOf { it.durationMinutes }.toDouble()
            .takeIf { input.exerciseSessions.isNotEmpty() },
        exerciseSessions = input.exerciseSessions.size,
        measuredDays = defined.size,
    )
}

private fun List<Double>.percentOf(predicate: (Double) -> Boolean): Double? {
    if (isEmpty()) return null
    return count(predicate).toDouble() / size * 100.0
}
