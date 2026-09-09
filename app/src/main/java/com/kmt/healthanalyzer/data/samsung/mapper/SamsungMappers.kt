package com.kmt.healthanalyzer.data.samsung.mapper

import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvRow
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyActivity
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SleepStage
import com.kmt.healthanalyzer.domain.model.SleepStageSegment
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import com.kmt.healthanalyzer.domain.model.StressSample
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Convertit les lignes brutes d'un export Samsung Health en modèles du domaine.
 *
 * Chaque mappeur rend `null` quand la ligne ne porte pas les champs indispensables
 * ou quand une valeur sort de la plage physiologique. Un export contient beaucoup
 * de lignes de service vides ; les rejeter ici évite de polluer la base.
 */

private const val MILLIS_PER_MINUTE = 60_000f

private fun Long.millisToMinutes(): Int = (this / MILLIS_PER_MINUTE).roundToInt()

private fun Instant.minutesUntil(end: Instant): Int =
    Duration.between(this, end).toMinutes().toInt()

object SleepMapper {
    private const val PREFIX = "com.samsung.health.sleep."

    fun map(row: SamsungCsvRow): SleepNight? {
        val id = row.string("${PREFIX}datauuid") ?: return null
        val offsetColumn = "${PREFIX}time_offset"
        val bedTime = row.instant("${PREFIX}start_time", offsetColumn) ?: return null
        val wakeTime = row.instant("${PREFIX}end_time", offsetColumn) ?: return null
        // Sans `sleep_duration`, la ligne décrit un objectif de coucher, pas une nuit mesurée.
        val duration = row.int("sleep_duration")?.takeIf { it > 0 } ?: return null

        val offset = row.zoneOffset(offsetColumn) ?: ZoneOffset.UTC
        val localBed = bedTime.atOffset(offset)

        return SleepNight(
            id = id,
            // Une nuit appartient au jour du réveil : coucher à 23 h le 9 = nuit du 10.
            date = wakeTime.atOffset(offset).toLocalDate(),
            bedTime = bedTime,
            wakeTime = wakeTime,
            durationMinutes = duration,
            score = row.int("sleep_score"),
            efficiencyPercent = row.float("efficiency"),
            latencyMinutes = row.long("sleep_latency")?.millisToMinutes(),
            physicalRecovery = row.double("physical_recovery")?.roundToInt(),
            mentalRecovery = row.double("mental_recovery")?.roundToInt(),
            remMinutes = row.int("total_rem_duration"),
            lightMinutes = row.int("total_light_duration"),
            localBedTime = localBed.toLocalTime(),
        )
    }
}

object SleepStageMapper {
    private val CODES = mapOf(
        40001 to SleepStage.AWAKE,
        40002 to SleepStage.LIGHT,
        40003 to SleepStage.DEEP,
        40004 to SleepStage.REM,
    )

    fun map(row: SamsungCsvRow): SleepStageSegment? {
        val id = row.string("datauuid") ?: return null
        val sleepId = row.string("sleep_id") ?: return null
        val start = row.instant("start_time") ?: return null
        val end = row.instant("end_time") ?: return null
        val code = row.int("stage") ?: return null

        return SleepStageSegment(
            id = id,
            sleepId = sleepId,
            stage = CODES[code] ?: SleepStage.UNKNOWN,
            start = start,
            end = end,
            durationMinutes = start.minutesUntil(end),
        )
    }
}

object HeartRateMapper {
    private const val PREFIX = "com.samsung.health.heart_rate."
    private val PLAUSIBLE = 25..250

    fun map(row: SamsungCsvRow): HeartRateSample? {
        val id = row.string("${PREFIX}datauuid") ?: return null
        val time = row.instant("${PREFIX}start_time", "${PREFIX}time_offset") ?: return null
        val bpm = row.double("${PREFIX}heart_rate")?.roundToInt() ?: return null
        if (bpm !in PLAUSIBLE) return null

        return HeartRateSample(
            id = id,
            time = time,
            beatsPerMinute = bpm,
            min = row.double("${PREFIX}min")?.roundToInt(),
            max = row.double("${PREFIX}max")?.roundToInt(),
        )
    }
}

object StressMapper {
    fun map(row: SamsungCsvRow): StressSample? {
        val id = row.string("datauuid") ?: return null
        val start = row.instant("start_time") ?: return null
        val end = row.instant("end_time") ?: return null
        val score = row.double("score")?.roundToInt() ?: return null

        return StressSample(
            id = id,
            start = start,
            end = end,
            score = score,
            min = row.double("min")?.roundToInt(),
            max = row.double("max")?.roundToInt(),
        )
    }
}

object SpO2Mapper {
    private const val PREFIX = "com.samsung.health.oxygen_saturation."
    private val PLAUSIBLE = 50f..100f

    fun map(row: SamsungCsvRow): SpO2Sample? {
        val id = row.string("${PREFIX}datauuid") ?: return null
        val time = row.instant("${PREFIX}start_time", "${PREFIX}time_offset") ?: return null
        val percent = row.float("${PREFIX}spo2") ?: return null
        if (percent !in PLAUSIBLE) return null

        return SpO2Sample(
            id = id,
            time = time,
            percent = percent,
            min = row.float("${PREFIX}min"),
            max = row.float("${PREFIX}max"),
        )
    }
}

object BodyCompositionMapper {
    fun map(row: SamsungCsvRow): BodyComposition? {
        val id = row.string("datauuid") ?: return null
        val time = row.instant("start_time") ?: return null
        val weight = row.float("weight")?.takeIf { it > 0f } ?: return null
        val height = row.float("height")?.takeIf { it > 0f }

        return BodyComposition(
            id = id,
            time = time,
            weightKg = weight,
            heightCm = height,
            bodyMassIndex = height?.let { weight / ((it / 100f) * (it / 100f)) },
            bodyFatPercent = row.float("body_fat"),
            bodyFatMassKg = row.float("body_fat_mass"),
            skeletalMuscleMassKg = row.float("skeletal_muscle_mass"),
            fatFreeMassKg = row.float("fat_free_mass"),
            totalBodyWaterKg = row.float("total_body_water"),
            basalMetabolicRate = row.int("basal_metabolic_rate"),
        )
    }
}

object ExerciseMapper {
    private const val PREFIX = "com.samsung.health.exercise."

    /** Codes d'activité Samsung Health les plus courants. */
    private val KINDS = mapOf(
        1001 to ExerciseKind.WALKING,
        1002 to ExerciseKind.RUNNING,
        11007 to ExerciseKind.CYCLING,
        13001 to ExerciseKind.HIKING,
        14001 to ExerciseKind.SWIMMING,
        15003 to ExerciseKind.STRENGTH,
        15005 to ExerciseKind.STRENGTH,
        10004 to ExerciseKind.ELLIPTICAL,
        10005 to ExerciseKind.ROWING,
        9002 to ExerciseKind.YOGA,
    )

    fun map(row: SamsungCsvRow): ExerciseSession? {
        val id = row.string("${PREFIX}datauuid") ?: return null
        val offsetColumn = "${PREFIX}time_offset"
        val start = row.instant("${PREFIX}start_time", offsetColumn) ?: return null
        val end = row.instant("${PREFIX}end_time", offsetColumn) ?: return null
        val code = row.int("${PREFIX}exercise_type")
        val duration = row.long("${PREFIX}duration")?.millisToMinutes()
            ?: start.minutesUntil(end)
        if (duration <= 0) return null

        return ExerciseSession(
            id = id,
            kind = KINDS[code] ?: ExerciseKind.OTHER,
            samsungTypeCode = code,
            start = start,
            end = end,
            durationMinutes = duration,
            calories = row.float("${PREFIX}calorie"),
            distanceMeters = row.float("${PREFIX}distance"),
            meanHeartRate = row.double("${PREFIX}mean_heart_rate")?.roundToInt(),
            maxHeartRate = row.double("${PREFIX}max_heart_rate")?.roundToInt(),
        )
    }
}

object DailyStepsMapper {
    /**
     * Samsung écrit une ligne par source et par jour. Le code -2 porte l'agrégat
     * de toutes les sources. Garder les autres ferait compter les pas deux fois.
     */
    private const val AGGREGATED_SOURCE = -2

    fun map(row: SamsungCsvRow): DailySteps? {
        if (row.int("source_type") != AGGREGATED_SOURCE) return null
        val date = row.localDate("day_time") ?: return null
        val steps = row.int("count") ?: return null

        return DailySteps(
            date = date,
            steps = steps,
            distanceMeters = row.float("distance"),
            calories = row.float("calorie"),
        )
    }
}

object DailyActivityMapper {
    fun map(row: SamsungCsvRow): DailyActivity? {
        val date = row.localDate("day_time") ?: return null

        return DailyActivity(
            date = date,
            steps = row.int("step_count"),
            activeMinutes = row.long("active_time")?.millisToMinutes(),
            exerciseMinutes = row.long("exercise_time")?.millisToMinutes(),
            activeCalories = row.double("calorie")?.roundToInt(),
            distanceMeters = row.float("distance"),
            floors = row.int("floor_count"),
        )
    }
}

object EnergyScoreMapper {
    fun map(row: SamsungCsvRow): EnergyScore? {
        val date = row.localDate("day_time") ?: return null
        val total = row.double("total_score")?.roundToInt() ?: return null

        return EnergyScore(
            date = date,
            total = total,
            sleep = row.double("sleep_score")?.roundToInt(),
            activity = row.double("activity_score")?.roundToInt(),
            nightHeartRate = row.double("shr_value")?.roundToInt(),
            nightHeartRateVariability = row.double("shrv_value")?.roundToInt(),
            sleepDurationMinutes = row.long("sleep_duration")?.millisToMinutes(),
        )
    }
}

/** Rend la date locale d'un instant, dans le décalage porté par la ligne. */
internal fun Instant.toLocalDate(offset: ZoneOffset): LocalDate = atOffset(offset).toLocalDate()
