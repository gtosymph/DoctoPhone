package com.kmt.healthanalyzer.data.db

import com.kmt.healthanalyzer.data.db.mapper.toDomain
import com.kmt.healthanalyzer.data.db.mapper.toEntity
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
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests d'aller-retour domaine -> entité -> domaine pour les 8 modèles cliniques. */
class ClinicalEntityMappersTest {

    private val fixedInstant = Instant.ofEpochMilli(1_772_000_000_000)
    private val fixedInstant2 = Instant.ofEpochMilli(1_772_003_600_000)
    private val fixedDate: LocalDate = LocalDate.of(2026, 3, 1)

    @Test
    fun `BloodPressureReading aller-retour avec tous les champs remplis`() {
        val domain = BloodPressureReading(
            id = "bp-1", time = fixedInstant, systolic = 128, diastolic = 82, pulse = 70, mean = 97,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `BloodPressureReading aller-retour avec les champs optionnels absents`() {
        val domain = BloodPressureReading(id = "bp-2", time = fixedInstant, systolic = 118, diastolic = 76)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `EcgRecord aller-retour avec tous les champs remplis`() {
        val domain = EcgRecord(
            id = "ecg-1", time = fixedInstant, meanHeartRate = 82, minHeartRate = 78,
            maxHeartRate = 88, classification = 1, symptoms = "0",
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `EcgRecord aller-retour avec les champs optionnels absents`() {
        val domain = EcgRecord(id = "ecg-2", time = fixedInstant)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SnoringEpisode aller-retour`() {
        val domain = SnoringEpisode(id = "sn-1", start = fixedInstant, end = fixedInstant2, durationMinutes = 60)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `RespiratoryRateSample aller-retour avec tous les champs remplis`() {
        val domain = RespiratoryRateSample(id = "rr-1", time = fixedInstant, breathsPerMinute = 14.5f, min = 11f, max = 18f)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `RespiratoryRateSample aller-retour avec les champs optionnels absents`() {
        val domain = RespiratoryRateSample(id = "rr-2", time = fixedInstant, breathsPerMinute = 14.5f)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SkinTemperatureSample aller-retour avec tous les champs remplis`() {
        val domain = SkinTemperatureSample(
            id = "st-1", time = fixedInstant, celsius = 33.2f, baseline = 33.0f, min = 32.5f, max = 33.8f,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SkinTemperatureSample aller-retour avec les champs optionnels absents`() {
        val domain = SkinTemperatureSample(id = "st-2", time = fixedInstant, celsius = 33.2f)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SleepApneaResult aller-retour avec tous les champs remplis`() {
        val domain = SleepApneaResult(id = "ap-1", time = fixedInstant, result = 1, averageBreathingDisturbance = 12.5f)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `SleepApneaResult aller-retour avec les champs optionnels absents`() {
        val domain = SleepApneaResult(id = "ap-2", time = fixedInstant, result = 0)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `DailyFloors aller-retour`() {
        val domain = DailyFloors(date = fixedDate, floors = 9)

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `StressAlert aller-retour`() {
        val domain = StressAlert(id = "al-1", start = fixedInstant, end = fixedInstant2)

        assertEquals(domain, domain.toEntity().toDomain())
    }
}
