package com.kmt.healthanalyzer.data.db.mapper

import com.kmt.healthanalyzer.data.db.entity.BloodPressureEntity
import com.kmt.healthanalyzer.data.db.entity.DailyFloorsEntity
import com.kmt.healthanalyzer.data.db.entity.EcgRecordEntity
import com.kmt.healthanalyzer.data.db.entity.RespiratoryRateEntity
import com.kmt.healthanalyzer.data.db.entity.SkinTemperatureEntity
import com.kmt.healthanalyzer.data.db.entity.SleepApneaEntity
import com.kmt.healthanalyzer.data.db.entity.SnoringEpisodeEntity
import com.kmt.healthanalyzer.data.db.entity.StressAlertEntity
import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.DailyFloors
import com.kmt.healthanalyzer.domain.model.EcgRecord
import com.kmt.healthanalyzer.domain.model.RespiratoryRateSample
import com.kmt.healthanalyzer.domain.model.SkinTemperatureSample
import com.kmt.healthanalyzer.domain.model.SleepApneaResult
import com.kmt.healthanalyzer.domain.model.SnoringEpisode
import com.kmt.healthanalyzer.domain.model.StressAlert
import java.time.Instant
import java.time.LocalDate

/**
 * Mappeurs entre les modèles cliniques du domaine (`ClinicalModels.kt`) et leurs entités
 * Room. Séparés d'[EntityMappers] pour garder chaque fichier sous les 400 lignes.
 */

// --- BloodPressureReading ---

fun BloodPressureReading.toEntity(): BloodPressureEntity = BloodPressureEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    mean = mean,
)

fun BloodPressureEntity.toDomain(): BloodPressureReading = BloodPressureReading(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    mean = mean,
)

// --- EcgRecord ---

fun EcgRecord.toEntity(): EcgRecordEntity = EcgRecordEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    meanHeartRate = meanHeartRate,
    minHeartRate = minHeartRate,
    maxHeartRate = maxHeartRate,
    classification = classification,
    symptoms = symptoms,
)

fun EcgRecordEntity.toDomain(): EcgRecord = EcgRecord(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    meanHeartRate = meanHeartRate,
    minHeartRate = minHeartRate,
    maxHeartRate = maxHeartRate,
    classification = classification,
    symptoms = symptoms,
)

// --- SnoringEpisode ---

fun SnoringEpisode.toEntity(): SnoringEpisodeEntity = SnoringEpisodeEntity(
    id = id,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
    durationMinutes = durationMinutes,
)

fun SnoringEpisodeEntity.toDomain(): SnoringEpisode = SnoringEpisode(
    id = id,
    start = Instant.ofEpochMilli(startEpochMillis),
    end = Instant.ofEpochMilli(endEpochMillis),
    durationMinutes = durationMinutes,
)

// --- RespiratoryRateSample ---

fun RespiratoryRateSample.toEntity(): RespiratoryRateEntity = RespiratoryRateEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    breathsPerMinute = breathsPerMinute,
    min = min,
    max = max,
)

fun RespiratoryRateEntity.toDomain(): RespiratoryRateSample = RespiratoryRateSample(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    breathsPerMinute = breathsPerMinute,
    min = min,
    max = max,
)

// --- SkinTemperatureSample ---

fun SkinTemperatureSample.toEntity(): SkinTemperatureEntity = SkinTemperatureEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    celsius = celsius,
    baseline = baseline,
    min = min,
    max = max,
)

fun SkinTemperatureEntity.toDomain(): SkinTemperatureSample = SkinTemperatureSample(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    celsius = celsius,
    baseline = baseline,
    min = min,
    max = max,
)

// --- SleepApneaResult ---

fun SleepApneaResult.toEntity(): SleepApneaEntity = SleepApneaEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    result = result,
    averageBreathingDisturbance = averageBreathingDisturbance,
)

fun SleepApneaEntity.toDomain(): SleepApneaResult = SleepApneaResult(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    result = result,
    averageBreathingDisturbance = averageBreathingDisturbance,
)

// --- DailyFloors ---

fun DailyFloors.toEntity(): DailyFloorsEntity = DailyFloorsEntity(
    date = date.toString(),
    floors = floors,
)

fun DailyFloorsEntity.toDomain(): DailyFloors = DailyFloors(
    date = LocalDate.parse(date),
    floors = floors,
)

// --- StressAlert ---

fun StressAlert.toEntity(): StressAlertEntity = StressAlertEntity(
    id = id,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
)

fun StressAlertEntity.toDomain(): StressAlert = StressAlert(
    id = id,
    start = Instant.ofEpochMilli(startEpochMillis),
    end = Instant.ofEpochMilli(endEpochMillis),
)
