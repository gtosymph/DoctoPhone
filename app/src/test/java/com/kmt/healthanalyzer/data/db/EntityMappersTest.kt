package com.kmt.healthanalyzer.data.db

import com.kmt.healthanalyzer.data.db.mapper.toDomain
import com.kmt.healthanalyzer.data.db.mapper.toEntity
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
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests d'aller-retour domaine -> entité -> domaine pour chaque modèle du domaine.
 *
 * Chaque modèle avec des champs nullables est testé deux fois : une fois entièrement
 * rempli, une fois avec ses champs nullables à `null`, pour garantir qu'aucune
 * conversion Room ne perd d'information dans un sens ou dans l'autre.
 */
class EntityMappersTest {

    private val fixedInstant = Instant.ofEpochMilli(1_772_000_000_000)
    private val fixedInstant2 = Instant.ofEpochMilli(1_772_003_600_000)
    private val fixedDate: LocalDate = LocalDate.of(2026, 3, 1)

    // --- SleepNight ---

    @Test
    fun `SleepNight aller-retour avec tous les champs remplis`() {
        val domain = SleepNight(
            id = "sleep-1",
            date = fixedDate,
            bedTime = fixedInstant,
            wakeTime = fixedInstant2,
            durationMinutes = 420,
            score = 82,
            efficiencyPercent = 91.5f,
            latencyMinutes = 12,
            physicalRecovery = 70,
            mentalRecovery = 65,
            remMinutes = 90,
            lightMinutes = 200,
            deepMinutes = 100,
            awakeMinutes = 30,
            localBedTime = LocalTime.of(23, 15),
            origin = DataOrigin.SAMSUNG_EXPORT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SleepNight aller-retour avec champs nullables a null`() {
        val domain = SleepNight(
            id = "sleep-2",
            date = fixedDate,
            bedTime = fixedInstant,
            wakeTime = fixedInstant2,
            durationMinutes = 420,
            score = null,
            efficiencyPercent = null,
            latencyMinutes = null,
            physicalRecovery = null,
            mentalRecovery = null,
            remMinutes = null,
            lightMinutes = null,
            deepMinutes = null,
            awakeMinutes = null,
            localBedTime = null,
            origin = DataOrigin.HEALTH_CONNECT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- SleepStageSegment ---

    @Test
    fun `SleepStageSegment aller-retour`() {
        val domain = SleepStageSegment(
            id = "segment-1",
            sleepId = "sleep-1",
            stage = SleepStage.DEEP,
            start = fixedInstant,
            end = fixedInstant2,
            durationMinutes = 60,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- HeartRateSample ---

    @Test
    fun `HeartRateSample aller-retour avec tous les champs remplis`() {
        val domain = HeartRateSample(
            id = "hr-1",
            time = fixedInstant,
            beatsPerMinute = 72,
            min = 58,
            max = 140,
            origin = DataOrigin.SAMSUNG_EXPORT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `HeartRateSample aller-retour avec champs nullables a null`() {
        val domain = HeartRateSample(
            id = "hr-2",
            time = fixedInstant,
            beatsPerMinute = 72,
            min = null,
            max = null,
            origin = DataOrigin.HEALTH_CONNECT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- StressSample ---

    @Test
    fun `StressSample aller-retour avec tous les champs remplis`() {
        val domain = StressSample(
            id = "stress-1",
            start = fixedInstant,
            end = fixedInstant2,
            score = 45,
            min = 20,
            max = 80,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `StressSample aller-retour avec champs nullables a null`() {
        val domain = StressSample(
            id = "stress-2",
            start = fixedInstant,
            end = fixedInstant2,
            score = 45,
            min = null,
            max = null,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- HrvSample ---

    @Test
    fun `HrvSample aller-retour avec tous les champs remplis`() {
        val domain = HrvSample(
            id = "hrv-1",
            time = fixedInstant,
            sdnnMillis = 55.4f,
            rmssdMillis = 32.1f,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `HrvSample aller-retour avec champs nullables a null`() {
        val domain = HrvSample(
            id = "hrv-2",
            time = fixedInstant,
            sdnnMillis = null,
            rmssdMillis = null,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- SpO2Sample ---

    @Test
    fun `SpO2Sample aller-retour avec tous les champs remplis`() {
        val domain = SpO2Sample(
            id = "spo2-1",
            time = fixedInstant,
            percent = 97.5f,
            min = 94f,
            max = 99f,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SpO2Sample aller-retour avec champs nullables a null`() {
        val domain = SpO2Sample(
            id = "spo2-2",
            time = fixedInstant,
            percent = 97.5f,
            min = null,
            max = null,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- BodyComposition ---

    @Test
    fun `BodyComposition aller-retour avec tous les champs remplis`() {
        val domain = BodyComposition(
            id = "body-1",
            time = fixedInstant,
            weightKg = 78.4f,
            heightCm = 180f,
            bodyMassIndex = 24.2f,
            bodyFatPercent = 18.5f,
            bodyFatMassKg = 14.5f,
            skeletalMuscleMassKg = 35.2f,
            fatFreeMassKg = 63.9f,
            totalBodyWaterKg = 46.8f,
            basalMetabolicRate = 1750,
            origin = DataOrigin.SAMSUNG_EXPORT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `BodyComposition aller-retour avec champs nullables a null`() {
        val domain = BodyComposition(
            id = "body-2",
            time = fixedInstant,
            weightKg = 78.4f,
            heightCm = null,
            bodyMassIndex = null,
            bodyFatPercent = null,
            bodyFatMassKg = null,
            skeletalMuscleMassKg = null,
            fatFreeMassKg = null,
            totalBodyWaterKg = null,
            basalMetabolicRate = null,
            origin = DataOrigin.HEALTH_CONNECT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- ExerciseSession ---

    @Test
    fun `ExerciseSession aller-retour avec tous les champs remplis`() {
        val domain = ExerciseSession(
            id = "exercise-1",
            kind = ExerciseKind.RUNNING,
            samsungTypeCode = 1002,
            start = fixedInstant,
            end = fixedInstant2,
            durationMinutes = 45,
            calories = 320.5f,
            distanceMeters = 8000f,
            meanHeartRate = 140,
            maxHeartRate = 175,
            origin = DataOrigin.SAMSUNG_EXPORT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `ExerciseSession aller-retour avec champs nullables a null`() {
        val domain = ExerciseSession(
            id = "exercise-2",
            kind = ExerciseKind.OTHER,
            samsungTypeCode = null,
            start = fixedInstant,
            end = fixedInstant2,
            durationMinutes = 45,
            calories = null,
            distanceMeters = null,
            meanHeartRate = null,
            maxHeartRate = null,
            origin = DataOrigin.HEALTH_CONNECT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- DailySteps ---

    @Test
    fun `DailySteps aller-retour avec tous les champs remplis`() {
        val domain = DailySteps(
            date = fixedDate,
            steps = 8500,
            distanceMeters = 6200f,
            calories = 410f,
            origin = DataOrigin.SAMSUNG_EXPORT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `DailySteps aller-retour avec champs nullables a null`() {
        val domain = DailySteps(
            date = fixedDate,
            steps = 8500,
            distanceMeters = null,
            calories = null,
            origin = DataOrigin.HEALTH_CONNECT,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- DailyActivity ---

    @Test
    fun `DailyActivity aller-retour avec tous les champs remplis`() {
        val domain = DailyActivity(
            date = fixedDate,
            steps = 8500,
            activeMinutes = 60,
            exerciseMinutes = 30,
            activeCalories = 520,
            distanceMeters = 6200f,
            floors = 8,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `DailyActivity aller-retour avec champs nullables a null`() {
        val domain = DailyActivity(
            date = fixedDate,
            steps = null,
            activeMinutes = null,
            exerciseMinutes = null,
            activeCalories = null,
            distanceMeters = null,
            floors = null,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    // --- EnergyScore ---

    @Test
    fun `EnergyScore aller-retour avec tous les champs remplis`() {
        val domain = EnergyScore(
            date = fixedDate,
            total = 78,
            sleep = 82,
            activity = 65,
            nightHeartRate = 55,
            nightHeartRateVariability = 48,
            sleepDurationMinutes = 420,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `EnergyScore aller-retour avec champs nullables a null`() {
        val domain = EnergyScore(
            date = fixedDate,
            total = 78,
            sleep = null,
            activity = null,
            nightHeartRate = null,
            nightHeartRateVariability = null,
            sleepDurationMinutes = null,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }
}
