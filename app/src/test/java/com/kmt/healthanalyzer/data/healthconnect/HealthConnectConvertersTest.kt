package com.kmt.healthanalyzer.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.kmt.healthanalyzer.domain.model.ExerciseKind
import com.kmt.healthanalyzer.domain.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Teste les fonctions PURES de [HealthConnectConverters].
 *
 * Aucune dépendance Android ici : [SleepSessionRecord] et [ExerciseSessionRecord] ne sont
 * utilisés que pour leurs constantes entières (`STAGE_TYPE_*`, `EXERCISE_TYPE_*`), qui sont
 * de simples `const val` — leur lecture ne passe par aucun code Android.
 */
class HealthConnectConvertersTest {

    // --- toSleepStage ---

    @Test
    fun `mappe les stades de sommeil connus vers l'enum de domaine`() {
        assertEquals(SleepStage.AWAKE, HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_AWAKE))
        assertEquals(
            SleepStage.AWAKE,
            HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
        )
        assertEquals(
            SleepStage.AWAKE,
            HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
        )
        assertEquals(SleepStage.LIGHT, HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_LIGHT))
        assertEquals(SleepStage.DEEP, HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_DEEP))
        assertEquals(SleepStage.REM, HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_REM))
    }

    @Test
    fun `mappe un stade de sommeil generique ou inconnu vers UNKNOWN`() {
        assertEquals(
            SleepStage.UNKNOWN,
            HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_SLEEPING),
        )
        assertEquals(
            SleepStage.UNKNOWN,
            HealthConnectConverters.toSleepStage(SleepSessionRecord.STAGE_TYPE_UNKNOWN),
        )
        assertEquals(SleepStage.UNKNOWN, HealthConnectConverters.toSleepStage(-1))
    }

    // --- toExerciseKind ---

    @Test
    fun `mappe les types d'exercice connus vers l'enum de domaine`() {
        assertEquals(
            ExerciseKind.WALKING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
        )
        assertEquals(
            ExerciseKind.RUNNING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING),
        )
        assertEquals(
            ExerciseKind.RUNNING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL),
        )
        assertEquals(
            ExerciseKind.CYCLING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_BIKING),
        )
        assertEquals(
            ExerciseKind.CYCLING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY),
        )
        assertEquals(
            ExerciseKind.HIKING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_HIKING),
        )
        assertEquals(
            ExerciseKind.SWIMMING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL),
        )
        assertEquals(
            ExerciseKind.SWIMMING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER),
        )
        assertEquals(
            ExerciseKind.STRENGTH,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING),
        )
        assertEquals(
            ExerciseKind.ELLIPTICAL,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL),
        )
        assertEquals(
            ExerciseKind.ROWING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_ROWING),
        )
        assertEquals(
            ExerciseKind.ROWING,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE),
        )
        assertEquals(
            ExerciseKind.YOGA,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_YOGA),
        )
    }

    @Test
    fun `mappe un type d'exercice non reconnu vers OTHER`() {
        assertEquals(
            ExerciseKind.OTHER,
            HealthConnectConverters.toExerciseKind(ExerciseSessionRecord.EXERCISE_TYPE_GOLF),
        )
        assertEquals(ExerciseKind.OTHER, HealthConnectConverters.toExerciseKind(-1))
    }

    // --- nightDate ---

    @Test
    fun `la date de la nuit est le jour du reveil, pas celui du coucher`() {
        // Coucher la veille à 23h locale, réveil le lendemain à 7h locale (Europe-Paris).
        val zone = ZoneId.of("Europe/Paris")
        val wakeTime = Instant.parse("2026-03-02T06:00:00Z") // 07:00 heure de Paris (hiver, UTC+1)

        val date = HealthConnectConverters.nightDate(wakeTime, zone)

        assertEquals(LocalDate.of(2026, 3, 2), date)
    }

    @Test
    fun `la date de la nuit reste celle du jour du reveil meme juste apres minuit local`() {
        val zone = ZoneId.of("Europe/Paris")
        // 00:30 heure de Paris le 2026-03-03 (hiver, UTC+1) -> 2026-03-02T23:30:00Z.
        val wakeTime = Instant.parse("2026-03-02T23:30:00Z")

        val date = HealthConnectConverters.nightDate(wakeTime, zone)

        assertEquals(LocalDate.of(2026, 3, 3), date)
    }

    // --- durationMinutes ---

    @Test
    fun `calcule la duree en minutes entre deux instants`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-01-01T01:30:00Z")

        assertEquals(90, HealthConnectConverters.durationMinutes(start, end))
    }

    @Test
    fun `arrondit la duree a la minute inferieure`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = start.plusSeconds(89) // moins d'une minute complète en plus

        assertEquals(1, HealthConnectConverters.durationMinutes(start, end))
    }

    // --- bodyMassIndex ---

    @Test
    fun `calcule l'IMC quand la taille est connue`() {
        val bmi = HealthConnectConverters.bodyMassIndex(weightKg = 70f, heightCm = 175f)

        assertEquals(22.857143f, bmi!!, 0.001f)
    }

    @Test
    fun `rend null quand la taille est inconnue`() {
        assertNull(HealthConnectConverters.bodyMassIndex(weightKg = 70f, heightCm = null))
    }

    @Test
    fun `rend null quand la taille est invalide`() {
        assertNull(HealthConnectConverters.bodyMassIndex(weightKg = 70f, heightCm = 0f))
        assertNull(HealthConnectConverters.bodyMassIndex(weightKg = 70f, heightCm = -10f))
    }
}
