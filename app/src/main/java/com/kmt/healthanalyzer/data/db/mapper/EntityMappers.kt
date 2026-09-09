package com.kmt.healthanalyzer.data.db.mapper

import com.kmt.healthanalyzer.data.db.entity.BodyCompositionEntity
import com.kmt.healthanalyzer.data.db.entity.DailyActivityEntity
import com.kmt.healthanalyzer.data.db.entity.DailyStepsEntity
import com.kmt.healthanalyzer.data.db.entity.EnergyScoreEntity
import com.kmt.healthanalyzer.data.db.entity.ExerciseSessionEntity
import com.kmt.healthanalyzer.data.db.entity.HeartRateSampleEntity
import com.kmt.healthanalyzer.data.db.entity.HrvSampleEntity
import com.kmt.healthanalyzer.data.db.entity.SleepNightEntity
import com.kmt.healthanalyzer.data.db.entity.SleepStageSegmentEntity
import com.kmt.healthanalyzer.data.db.entity.SpO2SampleEntity
import com.kmt.healthanalyzer.data.db.entity.StressSampleEntity
import com.kmt.healthanalyzer.domain.model.BodyComposition
import com.kmt.healthanalyzer.domain.model.DailyActivity
import com.kmt.healthanalyzer.domain.model.DailySteps
import com.kmt.healthanalyzer.domain.model.DataOrigin
import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.ExerciseSession
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import com.kmt.healthanalyzer.domain.model.SleepNight
import com.kmt.healthanalyzer.domain.model.SleepStage
import com.kmt.healthanalyzer.domain.model.SleepStageSegment
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import com.kmt.healthanalyzer.domain.model.StressSample
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Mappeurs entre les modèles de domaine (`domain.model`) et les entités Room (`data.db.entity`).
 *
 * Chaque paire `toEntity()` / `toDomain()` est un aller-retour sans perte : les valeurs
 * `null` du domaine restent `null` en base, et inversement.
 */

// --- SleepNight ---

fun SleepNight.toEntity(): SleepNightEntity = SleepNightEntity(
    id = id,
    date = date.toString(),
    bedTimeEpochMillis = bedTime.toEpochMilli(),
    wakeTimeEpochMillis = wakeTime.toEpochMilli(),
    durationMinutes = durationMinutes,
    score = score,
    efficiencyPercent = efficiencyPercent,
    latencyMinutes = latencyMinutes,
    physicalRecovery = physicalRecovery,
    mentalRecovery = mentalRecovery,
    remMinutes = remMinutes,
    lightMinutes = lightMinutes,
    deepMinutes = deepMinutes,
    awakeMinutes = awakeMinutes,
    localBedTime = localBedTime?.toString(),
    origin = origin.name,
)

fun SleepNightEntity.toDomain(): SleepNight = SleepNight(
    id = id,
    date = LocalDate.parse(date),
    bedTime = Instant.ofEpochMilli(bedTimeEpochMillis),
    wakeTime = Instant.ofEpochMilli(wakeTimeEpochMillis),
    durationMinutes = durationMinutes,
    score = score,
    efficiencyPercent = efficiencyPercent,
    latencyMinutes = latencyMinutes,
    physicalRecovery = physicalRecovery,
    mentalRecovery = mentalRecovery,
    remMinutes = remMinutes,
    lightMinutes = lightMinutes,
    deepMinutes = deepMinutes,
    awakeMinutes = awakeMinutes,
    localBedTime = localBedTime?.let(LocalTime::parse),
    origin = DataOrigin.valueOf(origin),
)

// --- SleepStageSegment ---

fun SleepStageSegment.toEntity(): SleepStageSegmentEntity = SleepStageSegmentEntity(
    id = id,
    sleepId = sleepId,
    stage = stage.name,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
    durationMinutes = durationMinutes,
)

fun SleepStageSegmentEntity.toDomain(): SleepStageSegment = SleepStageSegment(
    id = id,
    sleepId = sleepId,
    stage = SleepStage.valueOf(stage),
    start = Instant.ofEpochMilli(startEpochMillis),
    end = Instant.ofEpochMilli(endEpochMillis),
    durationMinutes = durationMinutes,
)

// --- HeartRateSample ---

fun HeartRateSample.toEntity(): HeartRateSampleEntity = HeartRateSampleEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    beatsPerMinute = beatsPerMinute,
    min = min,
    max = max,
    origin = origin.name,
)

fun HeartRateSampleEntity.toDomain(): HeartRateSample = HeartRateSample(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    beatsPerMinute = beatsPerMinute,
    min = min,
    max = max,
    origin = DataOrigin.valueOf(origin),
)

// --- StressSample ---

fun StressSample.toEntity(): StressSampleEntity = StressSampleEntity(
    id = id,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
    score = score,
    min = min,
    max = max,
)

fun StressSampleEntity.toDomain(): StressSample = StressSample(
    id = id,
    start = Instant.ofEpochMilli(startEpochMillis),
    end = Instant.ofEpochMilli(endEpochMillis),
    score = score,
    min = min,
    max = max,
)

// --- HrvSample ---

fun HrvSample.toEntity(): HrvSampleEntity = HrvSampleEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    sdnnMillis = sdnnMillis,
    rmssdMillis = rmssdMillis,
)

fun HrvSampleEntity.toDomain(): HrvSample = HrvSample(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    sdnnMillis = sdnnMillis,
    rmssdMillis = rmssdMillis,
)

// --- SpO2Sample ---

fun SpO2Sample.toEntity(): SpO2SampleEntity = SpO2SampleEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    percent = percent,
    min = min,
    max = max,
)

fun SpO2SampleEntity.toDomain(): SpO2Sample = SpO2Sample(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    percent = percent,
    min = min,
    max = max,
)

// --- BodyComposition ---

fun BodyComposition.toEntity(): BodyCompositionEntity = BodyCompositionEntity(
    id = id,
    timeEpochMillis = time.toEpochMilli(),
    weightKg = weightKg,
    heightCm = heightCm,
    bodyMassIndex = bodyMassIndex,
    bodyFatPercent = bodyFatPercent,
    bodyFatMassKg = bodyFatMassKg,
    skeletalMuscleMassKg = skeletalMuscleMassKg,
    fatFreeMassKg = fatFreeMassKg,
    totalBodyWaterKg = totalBodyWaterKg,
    basalMetabolicRate = basalMetabolicRate,
    origin = origin.name,
)

fun BodyCompositionEntity.toDomain(): BodyComposition = BodyComposition(
    id = id,
    time = Instant.ofEpochMilli(timeEpochMillis),
    weightKg = weightKg,
    heightCm = heightCm,
    bodyMassIndex = bodyMassIndex,
    bodyFatPercent = bodyFatPercent,
    bodyFatMassKg = bodyFatMassKg,
    skeletalMuscleMassKg = skeletalMuscleMassKg,
    fatFreeMassKg = fatFreeMassKg,
    totalBodyWaterKg = totalBodyWaterKg,
    basalMetabolicRate = basalMetabolicRate,
    origin = DataOrigin.valueOf(origin),
)

// --- ExerciseSession ---

fun ExerciseSession.toEntity(): ExerciseSessionEntity = ExerciseSessionEntity(
    id = id,
    kind = kind.name,
    samsungTypeCode = samsungTypeCode,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = end.toEpochMilli(),
    durationMinutes = durationMinutes,
    calories = calories,
    distanceMeters = distanceMeters,
    meanHeartRate = meanHeartRate,
    maxHeartRate = maxHeartRate,
    origin = origin.name,
)

fun ExerciseSessionEntity.toDomain(): ExerciseSession = ExerciseSession(
    id = id,
    kind = ExerciseKind.valueOf(kind),
    samsungTypeCode = samsungTypeCode,
    start = Instant.ofEpochMilli(startEpochMillis),
    end = Instant.ofEpochMilli(endEpochMillis),
    durationMinutes = durationMinutes,
    calories = calories,
    distanceMeters = distanceMeters,
    meanHeartRate = meanHeartRate,
    maxHeartRate = maxHeartRate,
    origin = DataOrigin.valueOf(origin),
)

// --- DailySteps ---

fun DailySteps.toEntity(): DailyStepsEntity = DailyStepsEntity(
    date = date.toString(),
    steps = steps,
    distanceMeters = distanceMeters,
    calories = calories,
    origin = origin.name,
)

fun DailyStepsEntity.toDomain(): DailySteps = DailySteps(
    date = LocalDate.parse(date),
    steps = steps,
    distanceMeters = distanceMeters,
    calories = calories,
    origin = DataOrigin.valueOf(origin),
)

// --- DailyActivity ---

fun DailyActivity.toEntity(): DailyActivityEntity = DailyActivityEntity(
    date = date.toString(),
    steps = steps,
    activeMinutes = activeMinutes,
    exerciseMinutes = exerciseMinutes,
    activeCalories = activeCalories,
    distanceMeters = distanceMeters,
    floors = floors,
)

fun DailyActivityEntity.toDomain(): DailyActivity = DailyActivity(
    date = LocalDate.parse(date),
    steps = steps,
    activeMinutes = activeMinutes,
    exerciseMinutes = exerciseMinutes,
    activeCalories = activeCalories,
    distanceMeters = distanceMeters,
    floors = floors,
)

// --- EnergyScore ---

fun EnergyScore.toEntity(): EnergyScoreEntity = EnergyScoreEntity(
    date = date.toString(),
    total = total,
    sleep = sleep,
    activity = activity,
    nightHeartRate = nightHeartRate,
    nightHeartRateVariability = nightHeartRateVariability,
    sleepDurationMinutes = sleepDurationMinutes,
)

fun EnergyScoreEntity.toDomain(): EnergyScore = EnergyScore(
    date = LocalDate.parse(date),
    total = total,
    sleep = sleep,
    activity = activity,
    nightHeartRate = nightHeartRate,
    nightHeartRateVariability = nightHeartRateVariability,
    sleepDurationMinutes = sleepDurationMinutes,
)
