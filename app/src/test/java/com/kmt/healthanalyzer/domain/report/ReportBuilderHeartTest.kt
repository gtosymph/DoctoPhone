package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.BloodPressureReading
import com.kmt.healthanalyzer.domain.model.EcgRecord
import com.kmt.healthanalyzer.domain.model.HeartRateSample
import com.kmt.healthanalyzer.domain.model.HrvSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderHeartTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int, minute: Int = 0): Instant = day(n).atTime(hour, minute).toInstant(zone)

    @Test
    fun `estimates the daily resting heart rate as the fifth percentile, like the home screen`() {
        val samples = (1..100).map { HeartRateSample(id = "hr-$it", time = at(1, 8, it % 60), beatsPerMinute = it) }

        val kpi = builder.build(ReportInput(range = day(1)..day(1), heartRates = samples)).heart.kpi

        assertEquals(5.0, kpi.restingMean!!, 0.01)
        assertEquals(1, kpi.measuredDays)
    }

    @Test
    fun `ignores a day with too few samples for a resting estimate`() {
        val samples = listOf(HeartRateSample("hr-1", at(1, 8), 60), HeartRateSample("hr-2", at(1, 9), 62))

        val kpi = builder.build(ReportInput(range = day(1)..day(1), heartRates = samples)).heart.kpi

        assertNull(kpi.restingMean)
        assertEquals(0, kpi.measuredDays)
    }

    @Test
    fun `reports the highest single beat rate observed, across every sample`() {
        val samples = listOf(
            HeartRateSample("hr-1", at(1, 8), 60),
            HeartRateSample("hr-2", at(1, 12), 145),
            HeartRateSample("hr-3", at(1, 20), 70),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(1), heartRates = samples)).heart.kpi

        assertEquals(145, kpi.maxObserved)
    }

    @Test
    fun `averages the heart rate by local hour of day`() {
        val samples = listOf(
            HeartRateSample("hr-1", at(1, 8), 60),
            HeartRateSample("hr-2", at(1, 8, 30), 80),
            HeartRateSample("hr-3", at(1, 20), 70),
        )

        val hourly = builder.build(ReportInput(range = day(1)..day(1), heartRates = samples)).heart.hourly

        assertEquals(24, hourly.size)
        assertEquals(70.0, hourly[8].value!!, 0.01)
        assertEquals(70.0, hourly[20].value!!, 0.01)
        assertNull(hourly[0].value)
    }

    @Test
    fun `computes the median hrv across the daily means, one sample per day here`() {
        val hrv = listOf(
            HrvSample("h-1", at(1, 2), sdnnMillis = null, rmssdMillis = 30f),
            HrvSample("h-2", at(2, 2), sdnnMillis = null, rmssdMillis = 50f),
            HrvSample("h-3", at(3, 2), sdnnMillis = null, rmssdMillis = 70f),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(3), hrv = hrv)).heart.kpi

        assertEquals(50.0, kpi.hrvMedian!!, 0.01)
    }

    @Test
    fun `averages a day's hrv samples before taking the median, so a heavily sampled day does not dominate`() {
        val hrv = listOf(
            // Jour 1 : deux échantillons, moyenne 40.
            HrvSample("h-1", at(1, 2), sdnnMillis = null, rmssdMillis = 20f),
            HrvSample("h-2", at(1, 3), sdnnMillis = null, rmssdMillis = 60f),
            // Jour 2 : un seul échantillon, 90.
            HrvSample("h-3", at(2, 2), sdnnMillis = null, rmssdMillis = 90f),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(2), hrv = hrv)).heart.kpi

        // Médiane de [40, 90] = 65, pas la médiane brute de [20, 60, 90] = 60.
        assertEquals(65.0, kpi.hrvMedian!!, 0.01)
    }

    @Test
    fun `carries every blood pressure reading as a point`() {
        val readings = listOf(BloodPressureReading(id = "bp-1", time = at(1, 8), systolic = 120, diastolic = 80, pulse = 65))

        val points = builder.build(ReportInput(range = day(1)..day(1), bloodPressure = readings)).heart.bloodPressure

        val point = points.single()
        assertEquals("2026-03-01", point.date)
        assertEquals(120, point.systolic)
        assertEquals(80, point.diastolic)
        assertEquals(65, point.pulse)
    }

    @Test
    fun `translates every ecg record into a labeled point without guessing a clinical meaning`() {
        val record = EcgRecord(id = "e-1", time = at(1, 8), meanHeartRate = 72, classification = 1)

        val point = builder.build(ReportInput(range = day(1)..day(1), ecgRecords = listOf(record))).heart.ecg.single()

        assertEquals(72, point.meanHeartRate)
        assertEquals(1, point.classification)
        assertEquals("Résultat 1 — à lire dans Samsung Health Monitor", point.classificationLabel)
    }

    @Test
    fun `labels an ecg record without a classification code as unrecorded`() {
        val record = EcgRecord(id = "e-1", time = at(1, 8), classification = null)

        val point = builder.build(ReportInput(range = day(1)..day(1), ecgRecords = listOf(record))).heart.ecg.single()

        assertEquals("Résultat non enregistré", point.classificationLabel)
    }
}
