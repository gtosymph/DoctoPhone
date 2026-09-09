package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.EnergyScore
import com.kmt.healthanalyzer.domain.model.StressAlert
import com.kmt.healthanalyzer.domain.model.StressSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderStressTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int, minute: Int = 0): Instant = day(n).atTime(hour, minute).toInstant(zone)

    @Test
    fun `averages the daily means, not the raw samples, so a heavily sampled day does not dominate`() {
        val stress = listOf(
            // Jour 1 : deux échantillons, moyenne 50.
            StressSample("s-1", at(1, 8), at(1, 9), score = 20),
            StressSample("s-2", at(1, 9), at(1, 10), score = 80),
            // Jour 2 : un seul échantillon, 10.
            StressSample("s-3", at(2, 8), at(2, 9), score = 10),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(2), stress = stress)).stress.kpi

        // Moyenne de [50, 10] = 30, pas la moyenne brute de [20, 80, 10] = 36.67.
        assertEquals(30.0, kpi.mean!!, 0.01)
    }

    @Test
    fun `flags the share of samples above the sixty threshold`() {
        val stress = listOf(
            StressSample("s-1", at(1, 8), at(1, 9), score = 70),
            StressSample("s-2", at(1, 9), at(1, 10), score = 30),
            StressSample("s-3", at(1, 10), at(1, 11), score = 65),
            StressSample("s-4", at(1, 11), at(1, 12), score = 10),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(1), stress = stress)).stress.kpi

        assertEquals(50.0, kpi.percentAbove60!!, 0.01)
    }

    @Test
    fun `counts stress alerts and finds the peak hour`() {
        val stress = listOf(
            StressSample("s-1", at(1, 8), at(1, 9), score = 20),
            StressSample("s-2", at(1, 22), at(1, 23), score = 90),
        )
        val alerts = listOf(StressAlert("a-1", at(1, 22), at(1, 22, 30)))

        val kpi = builder.build(ReportInput(range = day(1)..day(1), stress = stress, stressAlerts = alerts)).stress.kpi

        assertEquals(1, kpi.alerts)
        assertEquals(22, kpi.peakHour)
    }

    @Test
    fun `averages the daily energy score as vitality`() {
        val energy = listOf(EnergyScore(date = day(1), total = 60), EnergyScore(date = day(2), total = 80))

        val model = builder.build(ReportInput(range = day(1)..day(2), energyScores = energy))

        assertEquals(70.0, model.stress.kpi.vitalityMean!!, 0.01)
        assertEquals(listOf(60.0, 80.0), model.stress.vitalityDaily.map { it.value })
    }

    @Test
    fun `leaves the stress kpi empty without any sample`() {
        val kpi = builder.build(ReportInput(range = day(1)..day(1))).stress.kpi

        assertNull(kpi.mean)
        assertNull(kpi.peakHour)
        assertEquals(0, kpi.measuredDays)
    }
}
