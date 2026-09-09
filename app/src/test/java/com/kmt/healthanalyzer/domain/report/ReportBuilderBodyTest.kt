package com.kmt.healthanalyzer.domain.report

import com.kmt.healthanalyzer.domain.model.BodyComposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderBodyTest {

    private val zone = ZoneOffset.UTC
    private val builder = ReportBuilder(zone)

    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun at(n: Int, hour: Int): Instant = day(n).atTime(hour, 0).toInstant(zone)

    @Test
    fun `carries every weight measurement as a point`() {
        val body = listOf(BodyComposition(id = "w-1", time = at(1, 8), weightKg = 80f, bodyMassIndex = 24.5f))

        val point = builder.build(ReportInput(range = day(1)..day(1), bodyCompositions = body)).body.daily.single()

        assertEquals(80.0, point.weightKg, 0.01)
        assertEquals(24.5, point.bodyMassIndex!!, 0.01)
    }

    @Test
    fun `computes the weight delta between the first and the last measurement`() {
        val body = listOf(
            BodyComposition(id = "w-1", time = at(1, 8), weightKg = 82f),
            BodyComposition(id = "w-2", time = at(10, 8), weightKg = 79.5f),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(10), bodyCompositions = body)).body.kpi

        assertEquals(82.0, kpi.firstWeightKg!!, 0.01)
        assertEquals(79.5, kpi.lastWeightKg!!, 0.01)
        assertEquals(-2.5, kpi.deltaKg!!, 0.01)
    }

    @Test
    fun `reports the most recent body mass index and days since the last measurement`() {
        val body = listOf(BodyComposition(id = "w-1", time = at(1, 8), weightKg = 80f, bodyMassIndex = 24.5f))

        val kpi = builder.build(ReportInput(range = day(1)..day(5), bodyCompositions = body)).body.kpi

        assertEquals(24.5, kpi.bodyMassIndex!!, 0.01)
        assertEquals("2026-03-01", kpi.lastMeasuredOn)
        assertEquals(4, kpi.daysSinceLastMeasure) // du 1er au 5, 5 jours de rapport
    }

    @Test
    fun `computes the fat mass delta from the raw fat mass field measured by the scale`() {
        val body = listOf(
            BodyComposition(id = "w-1", time = at(1, 8), weightKg = 80f, bodyFatPercent = 30f, bodyFatMassKg = 24f),
            BodyComposition(id = "w-2", time = at(10, 8), weightKg = 78f, bodyFatPercent = 25f, bodyFatMassKg = 19.5f),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(10), bodyCompositions = body)).body.kpi

        assertEquals(-4.5, kpi.deltaFatKg!!, 0.01) // 19.5 - 24
    }

    @Test
    fun `falls back to percent times weight when the scale did not give a fat mass`() {
        val body = listOf(
            BodyComposition(id = "w-1", time = at(1, 8), weightKg = 80f, bodyFatPercent = 30f, bodyFatMassKg = null),
            BodyComposition(id = "w-2", time = at(10, 8), weightKg = 78f, bodyFatPercent = 25f, bodyFatMassKg = null),
        )

        val kpi = builder.build(ReportInput(range = day(1)..day(10), bodyCompositions = body)).body.kpi

        // 25 % de 78 = 19,5 ; 30 % de 80 = 24 ; delta = -4,5.
        assertEquals(-4.5, kpi.deltaFatKg!!, 0.01)
    }

    @Test
    fun `leaves the body kpi empty without any measurement`() {
        val kpi = builder.build(ReportInput(range = day(1)..day(5))).body.kpi

        assertNull(kpi.bodyMassIndex)
        assertNull(kpi.lastMeasuredOn)
        assertEquals(0, kpi.measures)
    }
}
