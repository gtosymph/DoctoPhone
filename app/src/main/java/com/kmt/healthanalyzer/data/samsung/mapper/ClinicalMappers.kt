package com.kmt.healthanalyzer.data.samsung.mapper

import com.kmt.healthanalyzer.data.samsung.csv.SamsungCsvRow
import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.DailyFloors
import com.kmt.healthanalyzer.domain.model.EcgRecord
import com.kmt.healthanalyzer.domain.model.RespiratoryRateSample
import com.kmt.healthanalyzer.domain.model.SkinTemperatureSample
import com.kmt.healthanalyzer.domain.model.SleepApneaResult
import com.kmt.healthanalyzer.domain.model.SnoringEpisode
import com.kmt.healthanalyzer.domain.model.StressAlert
import java.time.Duration
import kotlin.math.roundToInt

/**
 * Convertit les lignes brutes des 8 types cliniques Samsung en modèles du domaine.
 *
 * Séparés de [SamsungMappers] pour garder chaque fichier sous les 400 lignes, comme
 * [com.kmt.healthanalyzer.domain.model.ClinicalModels] est séparé de `HealthModels`.
 */

private const val MILLIS_PER_MINUTE = 60_000f

private fun Long.millisToMinutes(): Int = (this / MILLIS_PER_MINUTE).roundToInt()

object BloodPressureMapper {
    // Les colonnes de ce type portent le préfixe du type Samsung d'origine, contrairement
    // à la plupart des autres types `com.samsung.shealth.*`.
    private const val PREFIX = "com.samsung.health.blood_pressure."
    private val SYSTOLIC_RANGE = 60..250
    private val DIASTOLIC_RANGE = 30..150

    fun map(row: SamsungCsvRow): BloodPressureReading? {
        val id = row.string("${PREFIX}datauuid") ?: return null
        val time = row.instant("${PREFIX}start_time", "${PREFIX}time_offset") ?: return null
        val systolic = row.int("${PREFIX}systolic")?.takeIf { it in SYSTOLIC_RANGE } ?: return null
        val diastolic = row.int("${PREFIX}diastolic")?.takeIf { it in DIASTOLIC_RANGE } ?: return null

        return BloodPressureReading(
            id = id,
            time = time,
            systolic = systolic,
            diastolic = diastolic,
            pulse = row.int("${PREFIX}pulse"),
            mean = row.int("${PREFIX}mean"),
        )
    }
}

object EcgMapper {
    fun map(row: SamsungCsvRow): EcgRecord? {
        val id = row.string("datauuid") ?: return null
        val time = row.instant("start_time", "time_offset") ?: return null

        return EcgRecord(
            id = id,
            time = time,
            meanHeartRate = row.double("mean_heart_rate")?.roundToInt(),
            minHeartRate = row.double("min_heart_rate")?.roundToInt(),
            maxHeartRate = row.double("max_heart_rate")?.roundToInt(),
            classification = row.int("classification"),
            symptoms = row.string("symptoms"),
        )
    }
}

object SnoringMapper {
    fun map(row: SamsungCsvRow): SnoringEpisode? {
        val id = row.string("datauuid") ?: return null
        val start = row.instant("start_time", "time_offset") ?: return null
        val end = row.instant("end_time", "time_offset") ?: return null
        val duration = row.long("duration")?.millisToMinutes()
            ?: Duration.between(start, end).toMinutes().toInt()

        return SnoringEpisode(id = id, start = start, end = end, durationMinutes = duration)
    }
}

object RespiratoryRateMapper {
    private val PLAUSIBLE = 4f..40f

    fun map(row: SamsungCsvRow): RespiratoryRateSample? {
        val id = row.string("datauuid") ?: return null
        val time = row.instant("start_time", "time_offset") ?: return null
        val average = row.float("average")?.takeIf { it in PLAUSIBLE } ?: return null

        return RespiratoryRateSample(
            id = id,
            time = time,
            breathsPerMinute = average,
            min = row.float("lower_limit"),
            max = row.float("upper_limit"),
        )
    }
}

object SkinTemperatureMapper {
    private val PLAUSIBLE = 25f..45f

    fun map(row: SamsungCsvRow): SkinTemperatureSample? {
        val id = row.string("datauuid") ?: return null
        val time = row.instant("start_time", "time_offset") ?: return null
        val celsius = row.float("temperature")?.takeIf { it in PLAUSIBLE } ?: return null

        return SkinTemperatureSample(
            id = id,
            time = time,
            celsius = celsius,
            baseline = row.float("baseline"),
            min = row.float("min"),
            max = row.float("max"),
        )
    }
}

object SleepApneaMapper {
    fun map(row: SamsungCsvRow): SleepApneaResult? {
        val id = row.string("datauuid") ?: return null
        val time = row.instant("start_time", "time_offset") ?: return null
        val result = row.int("result") ?: return null

        return SleepApneaResult(
            id = id,
            time = time,
            result = result,
            averageBreathingDisturbance = row.float("average_bd"),
        )
    }
}

object DailyFloorsMapper {
    fun map(row: SamsungCsvRow): DailyFloors? {
        val date = row.localDate("day_time") ?: return null
        val floors = row.int("floor_count") ?: return null

        return DailyFloors(date = date, floors = floors)
    }
}

object StressAlertMapper {
    fun map(row: SamsungCsvRow): StressAlert? {
        val id = row.string("datauuid") ?: return null
        val start = row.instant("start_time", "time_offset") ?: return null
        val end = row.instant("end_time", "time_offset") ?: return null

        return StressAlert(id = id, start = start, end = end)
    }
}
