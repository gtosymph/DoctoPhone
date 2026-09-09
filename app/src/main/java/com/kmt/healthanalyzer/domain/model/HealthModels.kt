package com.kmt.healthanalyzer.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Origine d'une donnée de santé, pour tracer ce que l'app a importé. */
enum class DataOrigin { SAMSUNG_EXPORT, HEALTH_CONNECT }

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

enum class ExerciseKind {
    WALKING, RUNNING, CYCLING, HIKING, SWIMMING, STRENGTH, ELLIPTICAL, ROWING, YOGA, OTHER
}

/** Une nuit de sommeil, rattachée au jour du réveil. */
data class SleepNight(
    val id: String,
    val date: LocalDate,
    val bedTime: Instant,
    val wakeTime: Instant,
    val durationMinutes: Int,
    val score: Int? = null,
    val efficiencyPercent: Float? = null,
    val latencyMinutes: Int? = null,
    val physicalRecovery: Int? = null,
    val mentalRecovery: Int? = null,
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val localBedTime: LocalTime? = null,
    val origin: DataOrigin = DataOrigin.SAMSUNG_EXPORT,
)

/** Un segment de stade de sommeil à l'intérieur d'une nuit. */
data class SleepStageSegment(
    val id: String,
    val sleepId: String,
    val stage: SleepStage,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Int,
)

data class HeartRateSample(
    val id: String,
    val time: Instant,
    val beatsPerMinute: Int,
    val min: Int? = null,
    val max: Int? = null,
    val origin: DataOrigin = DataOrigin.SAMSUNG_EXPORT,
)

data class StressSample(
    val id: String,
    val start: Instant,
    val end: Instant,
    val score: Int,
    val min: Int? = null,
    val max: Int? = null,
)

data class HrvSample(
    val id: String,
    val time: Instant,
    val sdnnMillis: Float?,
    val rmssdMillis: Float?,
)

data class SpO2Sample(
    val id: String,
    val time: Instant,
    val percent: Float,
    val min: Float? = null,
    val max: Float? = null,
)

data class BodyComposition(
    val id: String,
    val time: Instant,
    val weightKg: Float,
    val heightCm: Float? = null,
    val bodyMassIndex: Float? = null,
    val bodyFatPercent: Float? = null,
    val bodyFatMassKg: Float? = null,
    val skeletalMuscleMassKg: Float? = null,
    val fatFreeMassKg: Float? = null,
    val totalBodyWaterKg: Float? = null,
    val basalMetabolicRate: Int? = null,
    val origin: DataOrigin = DataOrigin.SAMSUNG_EXPORT,
)

data class ExerciseSession(
    val id: String,
    val kind: ExerciseKind,
    val samsungTypeCode: Int?,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Int,
    val calories: Float? = null,
    val distanceMeters: Float? = null,
    val meanHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val origin: DataOrigin = DataOrigin.SAMSUNG_EXPORT,
)

/** Total de pas d'une journée, toutes sources confondues. */
data class DailySteps(
    val date: LocalDate,
    val steps: Int,
    val distanceMeters: Float? = null,
    val calories: Float? = null,
    val origin: DataOrigin = DataOrigin.SAMSUNG_EXPORT,
)

/** Résumé d'activité d'une journée, tel que Samsung Health le calcule. */
data class DailyActivity(
    val date: LocalDate,
    val steps: Int? = null,
    val activeMinutes: Int? = null,
    val exerciseMinutes: Int? = null,
    val activeCalories: Int? = null,
    val distanceMeters: Float? = null,
    val floors: Int? = null,
)

/** Score d'énergie quotidien Samsung (Energy Score) et ses composantes. */
data class EnergyScore(
    val date: LocalDate,
    val total: Int,
    val sleep: Int? = null,
    val activity: Int? = null,
    val nightHeartRate: Int? = null,
    val nightHeartRateVariability: Int? = null,
    val sleepDurationMinutes: Int? = null,
)
