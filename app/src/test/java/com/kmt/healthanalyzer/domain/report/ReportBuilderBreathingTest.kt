package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.RespiratoryRateSample
import com.kmt.healthanalyzer.domain.model.SkinTemperatureSample
import com.kmt.healthanalyzer.domain.model.SpO2Sample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderBreathingTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int): Instant = day(n).atTime(hour, 0).toInstant(zone)

    @Test
    fun `averages spo2 per day and per month, and finds the monthly minimum`() {
        val spo2 = listOf(
            SpO2Sample("o-1", at(1, 2), percent = 96f),
            SpO2Sample("o-2", at(1, 3), percent = 92f),
            SpO2Sample("o-3", at(2, 2), percent = 98f),
        )

        val breathing = builder.build(ReportInput(range = day(1)..day(2), spO2 = spo2)).breathing

        assertEquals(94.0, breathing.spo2Daily.first { it.date == "2026-03-01" }.value!!, 0.01)
        val month = breathing.spo2Monthly.single()
        assertEquals(92.0, month.min, 0.01)
    }

    @Test
    fun `counts spo2 measurements under ninety percent`() {
        val spo2 = listOf(SpO2Sample("o-1", at(1, 2), percent = 88f), SpO2Sample("o-2", at(1, 3), percent = 95f))

        val kpi = builder.build(ReportInput(range = day(1)..day(1), spO2 = spo2)).breathing.kpi

        assertEquals(2, kpi.spo2Measures)
        assertEquals(1, kpi.spo2Under90)
    }

    @Test
    fun `averages the respiratory rate per day`() {
        val samples = listOf(
            RespiratoryRateSample("r-1", at(1, 2), breathsPerMinute = 14f),
            RespiratoryRateSample("r-2", at(1, 3), breathsPerMinute = 16f),
        )

        val breathing = builder.build(ReportInput(range = day(1)..day(1), respiratoryRates = samples)).breathing

        assertEquals(15.0, breathing.respiratoryDaily.single().value!!, 0.01)
        assertEquals(15.0, breathing.kpi.respiratoryMean!!, 0.01)
    }

    @Test
    fun `computes the mean and standard deviation of skin temperature`() {
        val samples = listOf(
            SkinTemperatureSample("t-1", at(1, 2), celsius = 33.0f),
            SkinTemperatureSample("t-2", at(2, 2), celsius = 33.4f),
            SkinTemperatureSample("t-3", at(3, 2), celsius = 32.8f),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(3), skinTemperatures = samples)).breathing.kpi

        assertEquals(33.067, kpi.skinTempMean!!, 0.01)
        // Écart-type de population (division par n) : 0.2494, pas 0.306 (n - 1).
        assertEquals(0.2494, kpi.skinTempStdDev!!, 0.001)
    }

    @Test
    fun `averages the daily means for respiratory rate and skin temperature, not the raw samples`() {
        val respiratory = listOf(
            // Jour 1 : deux échantillons, moyenne 20.
            RespiratoryRateSample("r-1", at(1, 2), breathsPerMinute = 10f),
            RespiratoryRateSample("r-2", at(1, 3), breathsPerMinute = 30f),
            // Jour 2 : un seul échantillon, 12.
            RespiratoryRateSample("r-3", at(2, 2), breathsPerMinute = 12f),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(2), respiratoryRates = respiratory)).breathing.kpi

        // Moyenne de [20, 12] = 16, pas la moyenne brute de [10, 30, 12] = 17.33.
        assertEquals(16.0, kpi.respiratoryMean!!, 0.01)
    }
}
