package com.kmt.healthanalyzer.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.SleepStage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fonctions PURES de conversion entre les types Health Connect et le modèle de domaine.
 *
 * Ce fichier ne dépend d'aucune instance de [androidx.health.connect.client.HealthConnectClient]
 * (donc d'aucun `Context` Android) : toutes les fonctions sont des mappings déterministes basés
 * sur des types simples (codes entiers, [Instant], [Float]), testables en JVM pur (JUnit4) sans
 * Robolectric ni instrumentation.
 */
object HealthConnectConverters {

    /** Convertit un `SleepSessionRecord.STAGE_TYPE_*` Health Connect vers l'enum de domaine [SleepStage]. */
    fun toSleepStage(stageType: Int): SleepStage = when (stageType) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        -> SleepStage.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        // STAGE_TYPE_SLEEPING (endormi, stade non précisé par la source) et
        // STAGE_TYPE_UNKNOWN tombent tous les deux dans UNKNOWN.
        else -> SleepStage.UNKNOWN
    }

    /** Convertit un `ExerciseSessionRecord.EXERCISE_TYPE_*` Health Connect vers l'enum de domaine [ExerciseKind]. */
    fun toExerciseKind(exerciseType: Int): ExerciseKind = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ExerciseKind.WALKING
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> ExerciseKind.RUNNING
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        -> ExerciseKind.CYCLING
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ExerciseKind.HIKING
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> ExerciseKind.SWIMMING
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> ExerciseKind.STRENGTH
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> ExerciseKind.ELLIPTICAL
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        -> ExerciseKind.ROWING
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> ExerciseKind.YOGA
        else -> ExerciseKind.OTHER
    }

    /**
     * Date d'une nuit de sommeil : le jour du RÉVEIL (`wakeTime`), dans le fuseau horaire local
     * de l'appareil. C'est la convention utilisée partout ailleurs dans l'app (export Samsung inclus).
     */
    fun nightDate(wakeTime: Instant, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        wakeTime.atZone(zoneId).toLocalDate()

    /** Durée en minutes entières entre deux instants (arrondi à la minute inférieure). */
    fun durationMinutes(start: Instant, end: Instant): Int =
        Duration.between(start, end).toMinutes().toInt()

    /** IMC = poids (kg) / taille (m)². Rend `null` si la taille est inconnue ou invalide (<= 0). */
    fun bodyMassIndex(weightKg: Float, heightCm: Float?): Float? {
        if (heightCm == null || heightCm <= 0f) return null
        val heightMeters = heightCm / 100f
        return weightKg / (heightMeters * heightMeters)
    }
}
